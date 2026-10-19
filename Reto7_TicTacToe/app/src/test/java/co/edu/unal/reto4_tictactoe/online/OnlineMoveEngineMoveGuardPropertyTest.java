package co.edu.unal.reto4_tictactoe.online;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import co.edu.unal.reto4_tictactoe.TicTacToeGame;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Tuple;

import java.util.Random;

/**
 * Property-based test for the online move guard.
 *
 * <p>// Feature: online-multiplayer, Property 1: guardián de movimiento
 *
 * <p><b>Validates: Requirements 5.1, 5.2, 12.3, 14.1, 14.2, 14.4, 23.1, 23.6</b>
 *
 * <p>Property 1 (design §Correctness Properties, "Guardián de movimiento"): for <i>every</i>
 * {@link MatchState}, requester uid and target cell, {@link OnlineMoveEngine#evaluate} applies the
 * move <b>if and only if</b> all of the following hold:
 *
 * <ul>
 *   <li>{@code status == PLAYING} (Req 5.2, 12.3, 14.4);</li>
 *   <li>the requester is a participant — the {@code hostUid} (mark {@code 'X'}) or the
 *       {@code guestUid} (mark {@code 'O'}) (Req 5.1);</li>
 *   <li>the requester's mark equals {@code turn} — it is their turn (Req 14.1, 23.1);</li>
 *   <li>the target cell is in range and free (Req 14.2, 23.6).</li>
 * </ul>
 *
 * <p>In any other case the move is <b>rejected</b>: {@link OnlineMoveEngine.Result#isApplied()} is
 * {@code false}, {@link OnlineMoveEngine.Result#getNewState()} is {@code null}, and the caller's
 * input {@link MatchState} (board, turn, status, winner) is left <b>unchanged</b>.
 *
 * <p>The generator deliberately spans the whole input space — every lifecycle
 * ({@code WAITING/PLAYING/FINISHED/ABANDONED}), participant and non-participant requesters, turn
 * {@code X}/{@code O}, boards with occupied and free cells, and cells both in and out of range —
 * so a single run exercises both the "applied" and the "rejected" branches. The expected verdict
 * is recomputed here independently of the engine and compared against it. Minimum 100 iterations.
 */
class OnlineMoveEngineMoveGuardPropertyTest {

    // Feature: online-multiplayer, Property 1: guardián de movimiento
    @Property(tries = 100)
    void moveAppliedIffValidAndStateUnchangedWhenRejected(@ForAll("candidateMoves") Candidate c) {
        MatchState before = c.state;
        String requester = c.requester;
        int cell = c.cell;

        // Snapshot the input to detect any mutation (engine purity, state-unchanged-on-reject).
        String boardBefore = before.getBoard();
        String turnBefore = before.getTurn();
        String statusBefore = before.getStatus();
        int winnerBefore = before.getWinner();
        String hostBefore = before.getHostUid();
        String guestBefore = before.getGuestUid();

        // Expected verdict, recomputed independently of the engine.
        boolean expectedApplied = isLegal(before, requester, cell);

        OnlineMoveEngine.Result result = OnlineMoveEngine.evaluate(before, requester, cell);

        String context = String.format(
                "status=%s, host=%s, guest=%s, turn=%s, board='%s', requester=%s, cell=%d",
                statusBefore, hostBefore, guestBefore, turnBefore, boardBefore, requester, cell);

        assertEquals(expectedApplied, result.isApplied(), () -> "applied iff valid (" + context + ")");

        if (expectedApplied) {
            // A legal move must expose a fresh resulting state.
            assertNotNull(result.getNewState(), () -> "applied move must expose a new state (" + context + ")");
        } else {
            // Rejected: no new state is exposed and the input must be untouched.
            assertNull(result.getNewState(),
                    () -> "rejected move must expose no new state, getNewState() == null (" + context + ")");
            assertEquals(MatchState.WINNER_NONE, result.getWinner(),
                    () -> "rejected move reports winner 0 (" + context + ")");
            assertNull(result.getStatus(), () -> "rejected move exposes no status (" + context + ")");
        }

        // Purity / state-unchanged: the caller's input must never be mutated by evaluate().
        assertEquals(boardBefore, before.getBoard(), "input board unchanged");
        assertEquals(turnBefore, before.getTurn(), "input turn unchanged");
        assertEquals(statusBefore, before.getStatus(), "input status unchanged");
        assertEquals(winnerBefore, before.getWinner(), "input winner unchanged");
        assertEquals(hostBefore, before.getHostUid(), "input hostUid unchanged");
        assertEquals(guestBefore, before.getGuestUid(), "input guestUid unchanged");
    }

    /**
     * Oracle: recomputes Property 1 legality independently of {@link OnlineMoveEngine}. A move is
     * legal iff the match is {@code PLAYING}, the requester is a participant whose mark equals the
     * current {@code turn}, the board is a well-formed 9-char string, the cell is in range, and the
     * cell is free.
     */
    private static boolean isLegal(MatchState s, String requester, int cell) {
        if (s == null || requester == null) {
            return false;
        }
        if (s.lifecycle() != GameLifecycle.PLAYING) {
            return false;
        }
        if (cell < 0 || cell >= TicTacToeGame.BOARD_SIZE) {
            return false;
        }
        String mark;
        if (requester.equals(s.getHostUid())) {
            mark = MatchState.MARK_X;
        } else if (requester.equals(s.getGuestUid())) {
            mark = MatchState.MARK_O;
        } else {
            return false;
        }
        if (!mark.equals(s.getTurn())) {
            return false;
        }
        String board = s.getBoard();
        if (board == null || board.length() != TicTacToeGame.BOARD_SIZE) {
            return false;
        }
        return board.charAt(cell) == MatchState.EMPTY_CELL;
    }

    /**
     * Generates random candidate moves spanning the full input space: every lifecycle, boards with
     * a random mix of {@code ' '}/{@code 'X'}/{@code 'O'} cells, turn {@code X}/{@code O}, a
     * requester that may be the host, the guest, or an unrelated uid, and a cell index that may be
     * in or out of range. The construction mixes "applied" and "rejected" cases without biasing
     * toward either.
     */
    @Provide
    Arbitrary<Candidate> candidateMoves() {
        Arbitrary<String> hostUids = Arbitraries.of("H1", "H2", "H3");
        Arbitrary<String> guestUids = Arbitraries.of("G1", "G2", "G3");
        // Lifecycle by name, plus a couple of malformed/unset values to exercise lifecycle()==null.
        Arbitrary<String> statuses = Arbitraries.of(
                GameLifecycle.WAITING.name(),
                GameLifecycle.PLAYING.name(),
                GameLifecycle.FINISHED.name(),
                GameLifecycle.ABANDONED.name(),
                "", "???");
        Arbitrary<String> turns = Arbitraries.of(MatchState.MARK_X, MatchState.MARK_O);
        // Board: 9 cells, each ' ' / 'X' / 'O', built from a seed so occupied and free cells mix.
        Arbitrary<Long> boardSeeds = Arbitraries.longs();
        // Requester selector: 0=host, 1=guest, 2=stranger.
        Arbitrary<Integer> requesterKind = Arbitraries.integers().between(0, 2);
        // Cell: include out-of-range values (-2..10) so range checks are exercised.
        Arbitrary<Integer> cells = Arbitraries.integers().between(-2, 10);

        return Combinators.combine(hostUids, guestUids, statuses, turns, boardSeeds,
                        requesterKind, cells)
                .as((host, guest, status, turn, seed, kind, cell) -> {
                    String g = guest.equals(host) ? guest + "x" : guest;

                    MatchState state = new MatchState();
                    state.setMatchId("m-" + Math.abs(seed));
                    state.setHostUid(host);
                    state.setGuestUid(g);
                    state.setBoard(randomBoard(seed));
                    state.setTurn(turn);
                    state.setStatus(status);
                    state.setWinner(MatchState.WINNER_NONE);

                    String requester;
                    if (kind == 0) {
                        requester = host;
                    } else if (kind == 1) {
                        requester = g;
                    } else {
                        requester = "STRANGER";
                    }

                    return new Candidate(state, requester, cell);
                });
    }

    /** Builds a 9-char board with a random mix of empty/X/O cells (never a validated position). */
    private static String randomBoard(long seed) {
        Random rnd = new Random(seed);
        char[] cells = {MatchState.EMPTY_CELL, 'X', 'O'};
        char[] board = new char[TicTacToeGame.BOARD_SIZE];
        for (int i = 0; i < board.length; i++) {
            board[i] = cells[rnd.nextInt(cells.length)];
        }
        return new String(board);
    }

    /** Immutable carrier for a generated candidate move (valid or invalid). */
    static final class Candidate {
        final MatchState state;
        final String requester;
        final int cell;

        Candidate(MatchState state, String requester, int cell) {
            this.state = state;
            this.requester = requester;
            this.cell = cell;
        }

        @Override
        public String toString() {
            return "Candidate{status=" + state.getStatus() + ", host=" + state.getHostUid()
                    + ", guest=" + state.getGuestUid() + ", turn=" + state.getTurn()
                    + ", board='" + state.getBoard() + "', requester=" + requester
                    + ", cell=" + cell + "}";
        }
    }
}
