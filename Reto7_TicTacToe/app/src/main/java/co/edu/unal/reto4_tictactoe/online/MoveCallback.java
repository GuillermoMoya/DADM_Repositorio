package co.edu.unal.reto4_tictactoe.online;

/**
 * Outcome callback for {@link MatchRepository#submitMove(String, int, MoveCallback)}.
 *
 * <p>Submitting a move runs an atomic, cloud-authoritative transaction that re-validates the
 * move (status is PLAYING, requester is a participant whose mark matches {@code turn}, and the
 * target cell is free) before applying it. It has three mutually exclusive outcomes, and exactly
 * one of the methods below is invoked per call:
 *
 * <ul>
 *   <li>{@link #onApplied()} — the move was legal and committed to the authoritative
 *       {@code board} (Req 14.1, 14.2, 23.1).</li>
 *   <li>{@link #onRejected()} — the move was not legal against the authoritative state (not the
 *       requester's turn, cell taken, game not PLAYING, or requester not a participant); the
 *       authoritative state is left unchanged (Req 14.4, 23.6).</li>
 *   <li>{@link #onError(Throwable)} — a connection or write error prevented the transaction from
 *       completing; the app stays in a consistent state (Req 16.1).</li>
 * </ul>
 *
 * <p>{@code onRejected} is distinct from {@code onError}: a rejected move is a normal, expected
 * outcome of an illegal attempt, not a failure. Local UX gating (via {@code MoveGate}) usually
 * prevents illegal attempts, but the cloud remains the authority and may still reject.
 *
 * <p>Kept free of any Firebase (or Android) types so the cloud layer stays swappable for tests.
 */
public interface MoveCallback {

    /** Invoked when the move was legal and applied to the authoritative board. */
    void onApplied();

    /**
     * Invoked when the move was illegal against the authoritative state and therefore not
     * applied; the authoritative state is unchanged (Req 14.4, 23.6).
     */
    void onRejected();

    /**
     * Invoked when a connection or write error prevented the move from completing.
     *
     * @param error the failure cause
     */
    void onError(Throwable error);
}
