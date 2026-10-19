package co.edu.unal.reto4_tictactoe.online;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.util.Random;

/**
 * Property-based test for the "X always starts" invariant of the online join claim.
 *
 * <p>// Feature: online-multiplayer, Property 3: X siempre inicia
 *
 * <p><b>Validates: Requirements 11.3</b>
 *
 * <p>Property 3 (design §Correctness Properties, "X siempre inicia"): for <i>every</i> match that
 * transitions to {@code PLAYING}, the initial value of {@code turn} is {@code "X"} (the
 * Anfitrión's mark), so the player holding mark {@code X} makes the first move. This holds
 * <b>regardless</b> of whatever {@code turn} the {@code WAITING} match carried before the join —
 * the claim always resets {@code turn} to {@code "X"}.
 *
 * <p>The property drives {@link OnlineJoinEngine#evaluate(MatchState, String)} directly over the
 * pure claim decision, the single source of truth that both {@link FakeMatchRepository} and the
 * real {@code FirebaseMatchRepository} reuse to perform the transition WAITING → PLAYING. The
 * generator produces random {@code WAITING} matches with a free guest slot — varying
 * {@code hostUid}, a board with a random mix of empty/X/O cells, and {@code turn} set to either
 * {@code "X"} or {@code "O"} <i>before</i> the join — plus random requester uids. For every
 * evaluation that reports {@code isJoined()}, we assert the resulting state is {@code PLAYING} with
 * {@code turn == MatchState.MARK_X}.
 *
 * <p>Because {@link OnlineJoinEngine} only claims a free {@code WAITING} slot, every joined result
 * in this generator is a genuine WAITING → PLAYING transition, so each successful iteration is a
 * real witness of the property. Minimum 100 iterations.
 */
class OnlineJoinEngineXStartsPropertyTest {

    // Feature: online-multiplayer, Property 3: X siempre inicia
    @Property(tries = 100)
    void everyMatchThatBecomesPlayingStartsWithTurnX(@ForAll("waitingClaims") Claim claim) {
        MatchState before = claim.state;
        String requester = claim.requester;

        // Preserve the pre-join turn to prove the claim resets it to "X" regardless of its value.
        String turnBefore = before.getTurn();

        OnlineJoinEngine.Result result = OnlineJoinEngine.evaluate(before, requester);

        String context = String.format(
                "status=%s, host=%s, guest=%s, turnBefore=%s, board='%s', requester=%s",
                before.getStatus(), before.getHostUid(), before.getGuestUid(),
                turnBefore, before.getBoard(), requester);

        // Every generated candidate is a free WAITING slot with a non-null requester, so the claim
        // must succeed and perform the WAITING -> PLAYING transition this property is about.
        assertEquals(true, result.isJoined(), () -> "WAITING slot with a requester must be claimed (" + context + ")");

        MatchState after = result.getNewState();
        assertNotNull(after, () -> "a joined result must expose the new PLAYING state (" + context + ")");

        // Property 3 core: any match transitioning to PLAYING starts with turn == "X".
        assertEquals(GameLifecycle.PLAYING, after.lifecycle(),
                () -> "join transitions the match to PLAYING (" + context + ")");
        assertEquals(MatchState.MARK_X, after.getTurn(),
                () -> "X always starts: turn must be \"X\" once PLAYING, regardless of turnBefore (" + context + ")");

        // The pure engine must not mutate its input while deciding.
        assertEquals(turnBefore, before.getTurn(), "input turn unchanged by evaluate()");
    }

    /**
     * Generates random, genuinely joinable candidates: a {@code WAITING} match with
     * {@code guestUid == null}, a varied {@code hostUid}, a board with a random mix of
     * {@code ' '}/{@code 'X'}/{@code 'O'} cells, and {@code turn} deliberately set to {@code "X"}
     * <i>or</i> {@code "O"} before the join (so the test proves the claim always resets it to
     * {@code "X"}), paired with a non-null requester uid (host or an unrelated player).
     */
    @Provide
    Arbitrary<Claim> waitingClaims() {
        Arbitrary<String> hostUids = Arbitraries.of("H1", "H2", "H3", "host-A", "host-B");
        // Pre-join turn is intentionally X or O to exercise the "regardless of previous turn" clause.
        Arbitrary<String> turnsBefore = Arbitraries.of(MatchState.MARK_X, MatchState.MARK_O);
        Arbitrary<Long> boardSeeds = Arbitraries.longs();
        // Requester selector: 0 = host itself joining, 1 = an unrelated player.
        Arbitrary<Integer> requesterKind = Arbitraries.integers().between(0, 1);
        Arbitrary<String> strangers = Arbitraries.of("G1", "G2", "G3", "guest-A", "guest-B");

        return Combinators.combine(hostUids, turnsBefore, boardSeeds, requesterKind, strangers)
                .as((host, turnBefore, seed, kind, stranger) -> {
                    MatchState state = new MatchState();
                    state.setMatchId("m-" + Math.abs(seed));
                    state.setHostUid(host);
                    state.setGuestUid(null); // Free slot: genuinely joinable.
                    state.setBoard(randomBoard(seed));
                    state.setTurn(turnBefore); // May be "O" before the join.
                    state.setLifecycle(GameLifecycle.WAITING);
                    state.setWinner(MatchState.WINNER_NONE);

                    String requester = (kind == 0) ? host : (stranger.equals(host) ? stranger + "x" : stranger);
                    return new Claim(state, requester);
                });
    }

    /** Builds a 9-char board with a random mix of empty/X/O cells. */
    private static String randomBoard(long seed) {
        Random rnd = new Random(seed);
        char[] cells = {MatchState.EMPTY_CELL, 'X', 'O'};
        char[] board = new char[9];
        for (int i = 0; i < board.length; i++) {
            board[i] = cells[rnd.nextInt(cells.length)];
        }
        return new String(board);
    }

    /** Immutable carrier for a generated joinable candidate. */
    static final class Claim {
        final MatchState state;
        final String requester;

        Claim(MatchState state, String requester) {
            this.state = state;
            this.requester = requester;
        }

        @Override
        public String toString() {
            return "Claim{status=" + state.getStatus() + ", host=" + state.getHostUid()
                    + ", guest=" + state.getGuestUid() + ", turnBefore=" + state.getTurn()
                    + ", board='" + state.getBoard() + "', requester=" + requester + "}";
        }
    }
}
