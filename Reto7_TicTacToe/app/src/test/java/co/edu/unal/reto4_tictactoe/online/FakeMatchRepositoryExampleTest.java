package co.edu.unal.reto4_tictactoe.online;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Example-based smoke tests for {@link FakeMatchRepository}, the in-memory {@link MatchRepository}
 * test double that mirrors the atomic behavior of {@code FirebaseMatchRepository} through the same
 * shared engines ({@link OnlineJoinEngine}, {@link OnlineMoveEngine}).
 *
 * <p>Unlike the property tests (Properties 1–5), these are concrete, hand-picked scenarios
 * (1–3 cases) that exercise the repository end-to-end through its public contract: create → join →
 * alternating moves → terminal result, plus the two "expected but not successful" outcomes
 * ({@code onRejected}, {@code onUnavailable}).
 *
 * <p><b>Validates: Requirements 10.3, 12.3, 14.4, 16.2</b>
 */
class FakeMatchRepositoryExampleTest {

    private static final String HOST = "host-uid";
    private static final String GUEST = "guest-uid";
    private static final String INTRUDER = "intruder-uid";

    /** Builds a repository with a deterministic monotonic clock and sequential match ids. */
    private static FakeMatchRepository newRepo() {
        AtomicLong clock = new AtomicLong(1_000L);
        return new FakeMatchRepository(() -> null, clock::getAndIncrement, null);
    }

    /** Creates a match as HOST and returns the generated match id. */
    private static String hostCreatesMatch(FakeMatchRepository repo) {
        repo.setCurrentUid(HOST);
        AtomicReference<String> matchId = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        repo.createMatch(new ResultCallback<String>() {
            @Override
            public void onSuccess(String result) {
                matchId.set(result);
            }

            @Override
            public void onError(Throwable e) {
                error.set(e);
            }
        });
        assertNull(error.get(), "createMatch should not error");
        assertNotNull(matchId.get(), "createMatch should yield a matchId");
        return matchId.get();
    }

    /** Joins the given match as GUEST and returns the observed outcome ("joined"/"unavailable"). */
    private static String join(FakeMatchRepository repo, String matchId, String uid) {
        repo.setCurrentUid(uid);
        AtomicReference<String> outcome = new AtomicReference<>();
        repo.joinMatch(matchId, new JoinCallback() {
            @Override
            public void onJoined() {
                outcome.set("joined");
            }

            @Override
            public void onUnavailable() {
                outcome.set("unavailable");
            }

            @Override
            public void onError(Throwable error) {
                outcome.set("error:" + error);
            }
        });
        return outcome.get();
    }

    /** Submits a move as {@code uid} and returns the observed outcome ("applied"/"rejected"). */
    private static String submitMove(FakeMatchRepository repo, String matchId, int cell, String uid) {
        repo.setCurrentUid(uid);
        AtomicReference<String> outcome = new AtomicReference<>();
        repo.submitMove(matchId, cell, new MoveCallback() {
            @Override
            public void onApplied() {
                outcome.set("applied");
            }

            @Override
            public void onRejected() {
                outcome.set("rejected");
            }

            @Override
            public void onError(Throwable error) {
                outcome.set("error:" + error);
            }
        });
        return outcome.get();
    }

    /**
     * Case 1 — Happy path: host creates, guest joins, then a sequence of alternating legal moves
     * drives the match to a known terminal result (X wins the top row). Asserts the authoritative
     * final snapshot (lifecycle FINISHED, winner X, final board) and that the match left the Lobby
     * once joined (Req 14.4).
     */
    @Test
    void happyPath_createJoinPlayToXWin_producesConsistentFinishedState() {
        FakeMatchRepository repo = newRepo();

        String matchId = hostCreatesMatch(repo);
        // While WAITING, the match is advertised in the Lobby.
        assertEquals(1, repo.lobbySize(), "a WAITING match must be listed in the Lobby");

        assertEquals("joined", join(repo, matchId, GUEST), "guest should claim the open slot");
        // Once PLAYING, the match is no longer joinable and must be removed from the Lobby.
        assertEquals(0, repo.lobbySize(), "a PLAYING match must leave the Lobby");

        MatchState afterJoin = repo.snapshot(matchId);
        assertEquals(GameLifecycle.PLAYING, afterJoin.lifecycle());
        assertEquals(MatchState.MARK_X, afterJoin.getTurn(), "X (host) moves first");
        assertEquals(GUEST, afterJoin.getGuestUid());

        // Alternating legal moves. Top row for X wins: X@0, O@3, X@1, O@4, X@2.
        assertEquals("applied", submitMove(repo, matchId, 0, HOST));
        assertEquals("applied", submitMove(repo, matchId, 3, GUEST));
        assertEquals("applied", submitMove(repo, matchId, 1, HOST));
        assertEquals("applied", submitMove(repo, matchId, 4, GUEST));
        assertEquals("applied", submitMove(repo, matchId, 2, HOST)); // X completes the top row.

        MatchState end = repo.snapshot(matchId);
        assertEquals(GameLifecycle.FINISHED, end.lifecycle(), "a winning move finishes the match");
        assertEquals(MatchState.WINNER_X, end.getWinner(), "X (host) wins");
        assertEquals("XXXOO    ", end.getBoard(), "board reflects the exact move sequence");
        assertEquals(0, repo.lobbySize(), "a FINISHED match is not in the Lobby");
    }

    /**
     * Case 2 — Rejection: in a PLAYING match it is X's turn; the guest (O) attempts to move out of
     * turn. The move is rejected and the authoritative board/turn is left unchanged (Req 12.3,
     * 14.4).
     */
    @Test
    void outOfTurnMove_isRejected_andLeavesStateUnchanged() {
        FakeMatchRepository repo = newRepo();

        String matchId = hostCreatesMatch(repo);
        assertEquals("joined", join(repo, matchId, GUEST));

        MatchState before = repo.snapshot(matchId);
        assertEquals(MatchState.MARK_X, before.getTurn(), "it is X's turn at the start");

        // Guest (O) tries to move while it is X's turn → out of turn.
        assertEquals("rejected", submitMove(repo, matchId, 0, GUEST),
                "an out-of-turn move must be rejected");

        MatchState after = repo.snapshot(matchId);
        assertEquals(before.getBoard(), after.getBoard(), "board must be unchanged after rejection");
        assertEquals(before.getTurn(), after.getTurn(), "turn must be unchanged after rejection");
        assertEquals(GameLifecycle.PLAYING, after.lifecycle(), "match stays PLAYING");
        assertEquals(MatchState.WINNER_NONE, after.getWinner());
    }

    /**
     * Case 3 — Join unavailability: once a match is PLAYING (host created, guest joined), a third
     * uid attempting to join receives {@code onUnavailable}; the existing guest is unchanged
     * (Req 10.3, 16.2).
     */
    @Test
    void joinAlreadyPlayingMatch_isUnavailable_andGuestUnchanged() {
        FakeMatchRepository repo = newRepo();

        String matchId = hostCreatesMatch(repo);
        assertEquals("joined", join(repo, matchId, GUEST), "first join claims the slot");

        String guestBefore = repo.snapshot(matchId).getGuestUid();
        assertEquals(GUEST, guestBefore);

        // A third player tries to join a match that is already PLAYING.
        assertEquals("unavailable", join(repo, matchId, INTRUDER),
                "joining a PLAYING match must report unavailable");

        MatchState after = repo.snapshot(matchId);
        assertEquals(GUEST, after.getGuestUid(), "the claimed guest slot must not change");
        assertEquals(GameLifecycle.PLAYING, after.lifecycle());
    }
}
