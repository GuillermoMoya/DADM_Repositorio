package co.edu.unal.reto4_tictactoe.online;

/**
 * Outcome callback for {@link MatchRepository#joinMatch(String, JoinCallback)}.
 *
 * <p>Joining a match is an atomic claim of the Invitado slot. It has three mutually exclusive
 * outcomes, and exactly one of the methods below is invoked per call:
 *
 * <ul>
 *   <li>{@link #onJoined()} — the slot was claimed; the match moved to {@code PLAYING} and the
 *       requester is now the guest (Req 1.1, 10.1, 10.2).</li>
 *   <li>{@link #onUnavailable()} — the match can no longer be joined (it was not WAITING, or the
 *       guest slot was already taken by someone else); the UI should inform "partida no
 *       disponible" and stay/return to the Lobby (design "Error Handling", Req 16.2, 10.3,
 *       23.3).</li>
 *   <li>{@link #onError(Throwable)} — a connection or write error prevented the operation from
 *       completing; the app stays in a consistent state (Req 16.1).</li>
 * </ul>
 *
 * <p>{@code onUnavailable} is distinct from {@code onError}: an unavailable match is a normal,
 * expected outcome of a competing join, not a failure.
 *
 * <p>Kept free of any Firebase (or Android) types so the cloud layer stays swappable for tests.
 */
public interface JoinCallback {

    /** Invoked when the Invitado slot was successfully claimed and the match is now PLAYING. */
    void onJoined();

    /**
     * Invoked when the match is no longer joinable (not WAITING, or guest slot already taken).
     * This is an expected outcome of a competing join, not an error (Req 16.2, 10.3, 23.3).
     */
    void onUnavailable();

    /**
     * Invoked when a connection or write error prevented the join from completing.
     *
     * @param error the failure cause
     */
    void onError(Throwable error);
}
