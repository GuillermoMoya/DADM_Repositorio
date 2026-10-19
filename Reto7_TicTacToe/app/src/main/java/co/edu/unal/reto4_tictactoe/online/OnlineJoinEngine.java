package co.edu.unal.reto4_tictactoe.online;

/**
 * Pure, Android-free, Firebase-free join-claim decision shared by the whole Modo_Online.
 *
 * <p>This is the single source of truth for "can this requester still claim the Invitado slot of
 * this match, and if so what is the resulting authoritative state?" Both
 * {@code FakeMatchRepository} (tests) and {@code FirebaseMatchRepository} (task 8.2, inside its
 * RTDB {@code runTransaction}) call this helper so the exact same claim rule runs everywhere
 * (design §Concurrency and Integrity → "Unión (claim del slot de Invitado)"). It is the pure
 * counterpart of {@link OnlineMoveEngine} for the move guard, and it is the decision that
 * Property 2 ("exactamente un Invitado reclama una Partida WAITING") and Property 3 ("X siempre
 * inicia") exercise.
 *
 * <h2>Availability (Property 2)</h2>
 * Given the current {@link MatchState} and the requester's {@code uid}, the join may proceed
 * <b>iff</b> all of the following hold (Req 1.1, 1.2, 10.1, 10.2, 10.3, 15.3, 23.3):
 * <ul>
 *   <li>the match exists and the requester's uid is non-{@code null};</li>
 *   <li>{@code status == WAITING} — a match already {@code PLAYING}/{@code FINISHED}/
 *       {@code ABANDONED} is no longer joinable;</li>
 *   <li>{@code guestUid == null} — the slot is still free, so a <b>third</b> player can never claim
 *       an already-complete match (Req 1.2, 15.3).</li>
 * </ul>
 * Because {@code guestUid} flips from {@code null} to the winner inside one atomic read-decide-write
 * (the repository holds its monitor; RTDB retries the transaction), two concurrent requests see
 * exactly one success and the loser evaluates against the now-occupied slot and is rejected
 * (Req 10.2, 23.3).
 *
 * <h2>Outcome</h2>
 * When available, a brand-new {@link MatchState} is returned with the slot claimed: {@code guestUid}
 * set to the requester, {@code status = PLAYING}, and {@code turn = "X"} so the Anfitrión (mark
 * {@code 'X'}) always moves first (Req 11.3, Property 3). All other fields (board, host, winner,
 * timestamps, presence) are carried over unchanged; the repository stamps {@code updatedAt},
 * records presence and removes the {@code /lobby/<id>} entry as its own side effects (design steps
 * 2–3), none of which belong in this pure decision.
 *
 * <h2>Purity</h2>
 * {@link #evaluate(MatchState, String)} never mutates its {@code input}; it returns a fresh
 * {@link Result}. On rejection the result exposes no new state and the caller's input is untouched.
 * The {@code updatedAt} timestamp is intentionally <b>not</b> set here (clock injection is a
 * repository concern); callers stamp it after claiming.
 *
 * <p>Utility class: not instantiable.
 */
public final class OnlineJoinEngine {

    private OnlineJoinEngine() {
        // Utility class: no instances.
    }

    /**
     * Pure outcome of evaluating a candidate join against a match.
     *
     * <p>Immutable value object. When {@link #isJoined()} is {@code false}, the match is no longer
     * available and {@link #getNewState()} is {@code null} (the caller's state must stay unchanged
     * and {@code JoinCallback.onUnavailable()} is invoked). When {@code true},
     * {@link #getNewState()} is a brand-new {@link MatchState} with the slot claimed
     * ({@code guestUid}, {@code status = PLAYING}, {@code turn = "X"}).
     */
    public static final class Result {

        private final boolean joined;
        private final MatchState newState;

        private Result(boolean joined, MatchState newState) {
            this.joined = joined;
            this.newState = newState;
        }

        /** The singleton rejected result (match not available; no state change). */
        private static final Result UNAVAILABLE = new Result(false, null);

        static Result unavailable() {
            return UNAVAILABLE;
        }

        static Result joined(MatchState newState) {
            return new Result(true, newState);
        }

        /** @return {@code true} iff the slot was free and the requester claimed it. */
        public boolean isJoined() {
            return joined;
        }

        /**
         * @return a fresh {@link MatchState} with the slot claimed when {@link #isJoined()} is
         *     {@code true}; {@code null} when the match was unavailable.
         */
        public MatchState getNewState() {
            return newState;
        }
    }

    /**
     * Evaluates whether {@code requester} may claim the Invitado slot of {@code input}, and if so
     * computes the resulting authoritative state — without mutating {@code input}.
     *
     * @param input     the current authoritative {@link MatchState}; not mutated. May be
     *                  {@code null} (an unknown match is never joinable).
     * @param requester the uid attempting to join. May be {@code null} (never joinable).
     * @return a joined {@link Result} carrying a fresh claimed state when the slot is free, or
     *     {@link Result#unavailable()} (no state change) otherwise.
     */
    public static Result evaluate(MatchState input, String requester) {
        if (input == null || requester == null) {
            return Result.unavailable();
        }
        // Claim only if the match is still WAITING with a free guest slot (design step 1).
        boolean available = input.lifecycle() == GameLifecycle.WAITING
                && input.getGuestUid() == null;
        if (!available) {
            return Result.unavailable();
        }

        // --- Available: build a fresh claimed state (input stays untouched). ---
        MatchState next = copyOf(input);
        next.setGuestUid(requester);
        next.setLifecycle(GameLifecycle.PLAYING);
        next.setTurn(MatchState.MARK_X); // Host = X moves first (Req 11.3, Property 3).
        return Result.joined(next);
    }

    /** Deep-ish copy of a {@link MatchState} so {@link #evaluate} never mutates its input. */
    private static MatchState copyOf(MatchState s) {
        MatchState c = new MatchState();
        c.setMatchId(s.getMatchId());
        c.setHostUid(s.getHostUid());
        c.setGuestUid(s.getGuestUid());
        c.setBoard(s.getBoard());
        c.setTurn(s.getTurn());
        c.setStatus(s.getStatus());
        c.setWinner(s.getWinner());
        c.setCreatedAt(s.getCreatedAt());
        c.setUpdatedAt(s.getUpdatedAt());
        c.setPresence(s.getPresence() == null
                ? new java.util.HashMap<>()
                : new java.util.HashMap<>(s.getPresence()));
        return c;
    }
}
