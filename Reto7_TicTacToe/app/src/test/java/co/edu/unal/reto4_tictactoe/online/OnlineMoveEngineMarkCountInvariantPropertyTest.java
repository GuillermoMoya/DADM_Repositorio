// Feature: online-multiplayer, Property 5: invariante countX - countO ∈ {0,1}
package co.edu.unal.reto4_tictactoe.online;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.edu.unal.reto4_tictactoe.TicTacToeGame;
import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Property-based test for the mark-count invariant under strict turn alternation (design
 * §Correctness Properties → Property 5).
 *
 * <p><b>Property 5: Invariante de conteo de marcas por turnos alternados.</b> For every sequence of
 * valid moves applied — here, through the shared pure engine {@link OnlineMoveEngine#evaluate} that
 * {@code submitMove} delegates to — over a match started with {@code turn == "X"}, every reachable
 * state satisfies {@code countX - countO ∈ {0, 1}}: X is never behind O and never ahead by more
 * than one. This reflects the strict X↔O alternation the engine enforces (host plays {@code 'X'}
 * and moves first; the turn flips on every applied move; Req 11.3, 14.1, 15.2).
 *
 * <p>The engine is the single source of truth both {@code FakeMatchRepository} and
 * {@code FirebaseMatchRepository} reuse, so exercising it directly validates the invariant for all
 * {@code submitMove} paths without touching Firebase (design §Testing Strategy). Minimum 100
 * iterations.
 *
 * <p>Validates: Requirements 11.3, 14.1, 15.2.
 */
class OnlineMoveEngineMarkCountInvariantPropertyTest {

    private static final String HOST_UID = "host-uid";
    private static final String GUEST_UID = "guest-uid";

    /**
     * Builds the authoritative starting state of a freshly joined match: {@code PLAYING}, empty
     * board, {@code turn == "X"}, with both participants present (design §Data Models; Req 11.3).
     */
    private static MatchState freshPlayingMatch() {
        MatchState state = new MatchState();
        state.setMatchId("match-1");
        state.setHostUid(HOST_UID);
        state.setGuestUid(GUEST_UID);
        state.setBoard(MatchState.EMPTY_BOARD);
        state.setTurn(MatchState.MARK_X);
        state.setLifecycle(GameLifecycle.PLAYING);
        state.setWinner(MatchState.WINNER_NONE);
        return state;
    }

    /** Counts the occurrences of {@code mark} across the 9-char board string. */
    private static int count(String board, char mark) {
        int n = 0;
        for (int i = 0; i < board.length(); i++) {
            if (board.charAt(i) == mark) {
                n++;
            }
        }
        return n;
    }

    /** Asserts the mark-count invariant on an arbitrary reachable board. */
    private static void assertMarkCountInvariant(String board, String context) {
        int countX = count(board, 'X');
        int countO = count(board, 'O');
        int diff = countX - countO;
        assertTrue(
                diff == 0 || diff == 1,
                () -> context + " expected countX - countO ∈ {0,1} but got "
                        + diff + " (X=" + countX + ", O=" + countO + ", board='" + board + "')");
    }

    /**
     * Generates sequences of candidate cell indices in the full range {@code [0, 8]}. Indices are
     * applied greedily against the current turn's player; the engine rejects illegal ones (occupied
     * cell, out of turn), which the harness skips. Lengths run past a full board so sequences can
     * drive matches all the way to a win or a tie.
     */
    @Provide
    Arbitrary<List<Integer>> moveSequences() {
        return Arbitraries.integers()
                .between(0, TicTacToeGame.BOARD_SIZE - 1)
                .list()
                .ofMinSize(0)
                .ofMaxSize(30);
    }

    /**
     * Property 5: applying any sequence of valid moves (through {@link OnlineMoveEngine#evaluate},
     * the engine {@code submitMove} reuses) over a match started with {@code turn == "X"} keeps
     * {@code countX - countO ∈ {0, 1}} at every reachable state.
     *
     * <p>Validates: Requirements 11.3, 14.1, 15.2.
     */
    @Property(tries = 100)
    void markCountInvariantHoldsAtEveryReachableState(
            @ForAll("moveSequences") List<Integer> candidateCells) {
        MatchState state = freshPlayingMatch();
        // The initial empty board trivially satisfies the invariant (0 - 0 = 0).
        assertMarkCountInvariant(state.getBoard(), "initial state");

        int applied = 0;
        for (int cell : candidateCells) {
            // Pick the player whose turn it is; only that player can legally move.
            String requester =
                    MatchState.MARK_X.equals(state.getTurn()) ? HOST_UID : GUEST_UID;

            OnlineMoveEngine.Result result = OnlineMoveEngine.evaluate(state, requester, cell);
            if (!result.isApplied()) {
                // Illegal move (occupied cell, or match already FINISHED): skip, state unchanged.
                continue;
            }

            // Advance to the authoritative resulting state and re-check the invariant.
            state = result.getNewState();
            applied++;
            assertMarkCountInvariant(
                    state.getBoard(), "after applied move #" + applied + " at cell " + cell);

            // Once the match finishes, no further moves are legal; stop driving this sequence.
            if (state.lifecycle() == GameLifecycle.FINISHED) {
                break;
            }
        }
    }

    /**
     * A deterministic full alternating game: play cells 0..8 in order with the correct player each
     * turn. Every intermediate board must satisfy the invariant, X leads at odd move counts, and
     * counts are equal at even move counts — a concrete witness of strict alternation.
     *
     * <p>Validates: Requirements 11.3, 14.1, 15.2.
     */
    @Property(tries = 100)
    void strictAlternationKeepsXAtMostOneAhead(@ForAll("moveSequences") List<Integer> ignored) {
        MatchState state = freshPlayingMatch();
        int applied = 0;
        for (int cell = 0; cell < TicTacToeGame.BOARD_SIZE; cell++) {
            String requester =
                    MatchState.MARK_X.equals(state.getTurn()) ? HOST_UID : GUEST_UID;
            OnlineMoveEngine.Result result = OnlineMoveEngine.evaluate(state, requester, cell);
            if (!result.isApplied()) {
                continue;
            }
            state = result.getNewState();
            applied++;

            int countX = count(state.getBoard(), 'X');
            int countO = count(state.getBoard(), 'O');
            assertMarkCountInvariant(state.getBoard(), "sequential move #" + applied);
            // After an odd number of applied moves X is one ahead; after an even number, tied.
            int expectedDiff = (applied % 2 == 1) ? 1 : 0;
            assertEquals(
                    expectedDiff,
                    countX - countO,
                    "countX - countO after " + applied + " alternating moves");

            if (state.lifecycle() == GameLifecycle.FINISHED) {
                break;
            }
        }
    }
}
