package co.edu.unal.reto4_tictactoe.online;

/**
 * Ciclo_Vida_Partida — the lifecycle a match goes through in the Modo_Online.
 *
 * <p>This enum models the authoritative {@code status} field of a {@code MatchState} stored in the
 * cloud (Firebase RTDB). The valid transitions are:
 *
 * <ul>
 *   <li>{@link #WAITING} → {@link #PLAYING}: the Invitado claims the open guest slot via an atomic
 *       transaction (Req 10.1).</li>
 *   <li>{@link #PLAYING} → {@link #FINISHED}: a move produces {@code checkForWinner() != 0}
 *       (Req 14.4).</li>
 *   <li>{@code *} → {@link #ABANDONED}: a player leaves explicitly or the host leaves a
 *       {@code WAITING} match (Req 18).</li>
 * </ul>
 *
 * <p>Persisted to RTDB by name (e.g. {@code "WAITING"}); keep the names stable so cloud snapshots
 * deserialize correctly (see design §Data Models, Req 1, 5, 10, 12, 18, 19).
 */
public enum GameLifecycle {

    /** A match was created and is waiting for an Invitado to join; no moves are allowed yet. */
    WAITING,

    /** Both players are present and the game is in progress. */
    PLAYING,

    /** The game ended with a tie or a win ({@code winner != 0}). */
    FINISHED,

    /** A player left the match (explicit leave or host leaving a WAITING match). */
    ABANDONED
}
