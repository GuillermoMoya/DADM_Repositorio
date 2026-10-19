package co.edu.unal.reto4_tictactoe.online;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.GenerationMode;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Property-based test for the atomic claim of the Invitado slot.
 *
 * <p>// Feature: online-multiplayer, Property 2: exactamente un invitado reclama la partida
 *
 * <p><b>Validates: Requirements 1.1, 1.2, 10.1, 10.2, 10.3, 15.3, 23.3</b>
 *
 * <p>Property 2 (design §Correctness Properties, "Exactamente un Invitado reclama una Partida
 * WAITING"): for <i>every</i> {@code WAITING} match with a free guest slot and <i>every</i> list of
 * concurrent requesters, exactly one requester ends up registered as {@code guestUid} and the match
 * moves to {@code PLAYING}; every other request is rejected ({@code onUnavailable}) and does not
 * change {@code guestUid}. Consequently a third player can never join an already-complete match.
 *
 * <p>Rather than modelling the race deterministically, this test genuinely exercises the atomic
 * read-decide-write by spawning {@code N} threads that all call
 * {@link FakeMatchRepository#joinMatch} on the <b>same</b> {@code WAITING} match at the same time
 * (released together by a {@link CountDownLatch}). {@link FakeMatchRepository} serializes those
 * calls through its monitor exactly as an RTDB transaction would, so the race must resolve to a
 * single winner. The join claim itself is delegated by the repository to the shared pure
 * {@link OnlineJoinEngine#evaluate(MatchState, String)}, the single source of truth under test.
 *
 * <p>The generator spans 2..8 distinct concurrent requesters (never the host) so every run has at
 * least one loser, plus a seed that shuffles the launch order so different interleavings are
 * explored across the 100 randomized iterations; the host uid is always excluded from the
 * requester set so the only legal claim is a genuine Invitado. Minimum 100 iterations.
 */
class OnlineJoinEngineSingleGuestPropertyTest {

    // Feature: online-multiplayer, Property 2: exactamente un invitado reclama la partida
    @Property(tries = 100, generation = GenerationMode.RANDOMIZED)
    void exactlyOneGuestClaimsAWaitingMatch(@ForAll("scenarios") Scenario scenario)
            throws InterruptedException {

        final int requesterCount = scenario.requesterCount;
        final String hostUid = "HOST";

        // Distinct Invitado candidates, none equal to the host, so every success is a real guest.
        // The order is shuffled by the scenario seed to vary the race interleaving per iteration.
        List<String> requesters = new ArrayList<>(requesterCount);
        for (int i = 0; i < requesterCount; i++) {
            requesters.add("G" + i);
        }
        Collections.shuffle(requesters, new Random(scenario.seed));

        // A single WAITING match created by the host (guest slot free).
        FakeMatchRepository repo = new FakeMatchRepository();
        repo.setCurrentUid(hostUid);
        AtomicReference<String> matchIdRef = new AtomicReference<>();
        repo.createMatch(new ResultCallback<String>() {
            @Override
            public void onSuccess(String matchId) {
                matchIdRef.set(matchId);
            }

            @Override
            public void onError(Throwable error) {
                throw new AssertionError("createMatch must succeed", error);
            }
        });
        final String matchId = matchIdRef.get();
        assertNotNull(matchId, "createMatch must yield a match id");
        assertEquals(GameLifecycle.WAITING, repo.snapshot(matchId).lifecycle(),
                "the match starts WAITING");

        // Launch all requesters concurrently and release them at once to force the race.
        AtomicInteger joinedCount = new AtomicInteger(0);
        AtomicInteger unavailableCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        List<String> winners = java.util.Collections.synchronizedList(new ArrayList<>());

        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(requesterCount);
        List<Thread> threads = new ArrayList<>(requesterCount);

        for (final String requester : requesters) {
            Thread t = new Thread(() -> {
                try {
                    start.await();
                    // Each requester acts as itself for the duration of this atomic claim. The
                    // repository serializes joinMatch through its monitor, so even though every
                    // thread sets its uid and calls join, the read-decide-write stays atomic.
                    synchronized (repo) {
                        repo.setCurrentUid(requester);
                        repo.joinMatch(matchId, new JoinCallback() {
                            @Override
                            public void onJoined() {
                                joinedCount.incrementAndGet();
                                winners.add(requester);
                            }

                            @Override
                            public void onUnavailable() {
                                unavailableCount.incrementAndGet();
                            }

                            @Override
                            public void onError(Throwable error) {
                                errorCount.incrementAndGet();
                            }
                        });
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }, "join-" + requester);
            threads.add(t);
            t.start();
        }

        start.countDown(); // Release every requester simultaneously.
        assertTrue(done.await(10, TimeUnit.SECONDS), "all join attempts must finish");
        for (Thread t : threads) {
            t.join(TimeUnit.SECONDS.toMillis(5));
        }

        // --- Exactly one winner, everyone else rejected, no errors. ---
        assertEquals(0, errorCount.get(), "no join should error on an in-memory WAITING match");
        assertEquals(1, joinedCount.get(),
                "exactly one requester may claim the guest slot (count=" + requesterCount + ")");
        assertEquals(requesterCount - 1, unavailableCount.get(),
                "every other requester is rejected as unavailable");

        // --- The authoritative state reflects that single winner. ---
        MatchState after = repo.snapshot(matchId);
        assertNotNull(after, "the match still exists after the race");
        assertEquals(GameLifecycle.PLAYING, after.lifecycle(),
                "the claimed match moved to PLAYING");
        assertEquals(1, winners.size(), "a single winner was recorded");
        String winner = winners.get(0);
        assertEquals(winner, after.getGuestUid(),
                "the stored guestUid is exactly the single winning requester");
        assertEquals(hostUid, after.getHostUid(), "the host is unchanged");
        assertEquals(MatchState.MARK_X, after.getTurn(), "a joined match starts on X (host first)");
        assertTrue(requesters.contains(after.getGuestUid()),
                "the guest is one of the concurrent requesters, never the host");

        // --- A third player can never join the now-full match (Req 1.2, 15.3). ---
        repo.setCurrentUid("LATECOMER");
        AtomicInteger lateJoined = new AtomicInteger(0);
        AtomicInteger lateUnavailable = new AtomicInteger(0);
        repo.joinMatch(matchId, new JoinCallback() {
            @Override
            public void onJoined() {
                lateJoined.incrementAndGet();
            }

            @Override
            public void onUnavailable() {
                lateUnavailable.incrementAndGet();
            }

            @Override
            public void onError(Throwable error) {
                throw new AssertionError("late join must not error", error);
            }
        });
        assertEquals(0, lateJoined.get(), "a full match cannot be joined by a third player");
        assertEquals(1, lateUnavailable.get(), "the late join is reported unavailable");
        assertEquals(winner, repo.snapshot(matchId).getGuestUid(),
                "the guest slot is unchanged by the rejected late join");
    }

    /**
     * Generates a race scenario: 2..8 concurrent requesters (so every run has at least one loser)
     * paired with a random seed that shuffles their launch order. Pairing the small count domain
     * with a full-range seed keeps the input space large enough that jqwik runs all 100 randomized
     * iterations with varied interleavings, rather than exhausting a tiny domain early.
     */
    @Provide
    Arbitrary<Scenario> scenarios() {
        Arbitrary<Integer> counts = Arbitraries.integers().between(2, 8);
        Arbitrary<Long> seeds = Arbitraries.longs();
        return Combinators.combine(counts, seeds).as(Scenario::new);
    }

    /** Immutable carrier for a generated race scenario (requester count + shuffle seed). */
    static final class Scenario {
        final int requesterCount;
        final long seed;

        Scenario(int requesterCount, long seed) {
            this.requesterCount = requesterCount;
            this.seed = seed;
        }

        @Override
        public String toString() {
            return "Scenario{requesterCount=" + requesterCount + ", seed=" + seed + "}";
        }
    }
}
