package co.edu.unal.reto4_tictactoe.online;

import java.util.List;

/**
 * Central cloud abstraction for the Modo_Online (online multiplayer) feature.
 *
 * <p>This is the seam that <strong>decouples the UI and the reused pure logic</strong>
 * ({@code TicTacToeGame}, {@code MoveGate}, {@code GameStatus}, {@code BoardView}) from Firebase.
 * Every match operation flows through here, and it is the swap point for tests: an in-memory
 * {@code FakeMatchRepository} models the same atomic behavior without a network, while the real
 * {@code FirebaseMatchRepository} backs it with RTDB (design "Components and Interfaces" §2).
 *
 * <p><strong>No Firebase (or Android) types appear in this interface or its support types.</strong>
 * That constraint is what keeps the cloud provider swappable; only the concrete
 * {@code FirebaseMatchRepository} imports Firebase packages (Req 6.1, 20.4).
 *
 * <p>Support types: {@link MatchSummary} (lightweight Lobby view), {@link MatchState}
 * (authoritative full state), {@link ListenerHandle} (lifecycle control for real-time
 * observation), {@link Listener} (real-time value observer), and the typed success/failure
 * callbacks {@link ResultCallback}, {@link JoinCallback} and {@link MoveCallback}.
 *
 * <p>Maps requirements: 6 (the cloud mediates and is authoritative), 9, 10, 13, 14, 15, 16, 17,
 * 20.4.
 */
public interface MatchRepository {

    // --- Lobby (Req 3, 7, 8) ---

    /**
     * Observes, in real time, the list of matches currently in the {@code WAITING} state.
     *
     * <p>The listener fires with the initial list and again on every change (a match appearing
     * or disappearing from the Lobby) until the returned handle is detached (Req 8.2, 8.3,
     * 17.2).
     *
     * @param listener receives the current list of joinable {@link MatchSummary} entries
     * @return a {@link ListenerHandle} the caller detaches when leaving the active lifecycle
     *         state ({@code onPause}/{@code onStop}/{@code onDestroy}) to avoid leaks (Req 17.2)
     */
    ListenerHandle observeOpenMatches(Listener<List<MatchSummary>> listener);

    // --- Creation / join (Req 2, 4, 9, 10) ---

    /**
     * Creates a {@code WAITING} match with an empty board; the requester becomes the Anfitrión
     * (mark {@code 'X'}, moves first).
     *
     * @param onMatchId receives the new match's id on success, or the failure cause on error
     *                  (Req 16.1)
     */
    void createMatch(ResultCallback<String> onMatchId);

    /**
     * Atomically claims the Invitado slot of a {@code WAITING} match and moves it to
     * {@code PLAYING}, or fails if the match is no longer available.
     *
     * @param matchId  the id of the match to join
     * @param callback receives the outcome: joined, unavailable, or error
     *                 (Req 1.2, 10.1-10.3, 16.2)
     */
    void joinMatch(String matchId, JoinCallback callback);

    // --- Match in progress (Req 13, 14) ---

    /**
     * Observes the authoritative {@link MatchState} of a single match in real time.
     *
     * <p>The listener fires with the current state and again on every authoritative change,
     * which is how both devices stay in sync, until the handle is detached (Req 13.1, 17.2).
     *
     * @param matchId  the id of the match to observe
     * @param listener receives the current {@link MatchState} on every change
     * @return a {@link ListenerHandle} the caller detaches on the Android lifecycle (Req 17.2)
     */
    ListenerHandle observeMatch(String matchId, Listener<MatchState> listener);

    /**
     * Atomically validates and applies a move: requires {@code status == PLAYING}, the requester
     * to be the participant whose mark matches {@code turn}, and the target cell to be free; then
     * writes the board, recomputes the winner and advances {@code status}/{@code turn}.
     *
     * @param matchId  the id of the match to move in
     * @param cell     the target cell index (0-8)
     * @param callback receives the outcome: applied, rejected, or error
     *                 (Req 14.1-14.4, 15, 23.1, 23.6)
     */
    void submitMove(String matchId, int cell, MoveCallback callback);

    // --- Leaving / lifecycle (Req 18, 19) ---

    /**
     * Explicit, voluntary exit from a match: marks it {@code ABANDONED} if it is {@code PLAYING},
     * or removes it (and its Lobby entry) if it is a {@code WAITING} match owned by the Anfitrión
     * (Req 18.1, 18.3).
     *
     * @param matchId the id of the match to leave
     */
    void leaveMatch(String matchId);

    /**
     * Registers presence for the current Player and arranges {@code onDisconnect} handling so a
     * transient disconnect is reflected without destroying an active {@code PLAYING} match, while
     * an orphaned {@code WAITING} match can be cleaned up (Req 18.2, 18.3, 23.4).
     *
     * @param matchId the id of the match to register presence for
     */
    void registerPresence(String matchId);
}
