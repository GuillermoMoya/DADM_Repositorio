package co.edu.unal.reto4_tictactoe.online;

/**
 * Player identity abstraction for the Modo_Online (online multiplayer) feature.
 *
 * <p>Encapsulates obtaining a stable, identified player so that the rest of the online layer
 * never imports authentication SDK types directly (design "Components and Interfaces" §1).
 * The concrete {@link FirebaseAuthProvider} backs this with Firebase anonymous sign-in.
 *
 * <p>Maps requirements: 2.1 (an identified Player) and 22.2 (reads/writes only for identified
 * users).
 */
public interface AuthProvider {

    /**
     * Ensures an identified Player exists, delivering the stable {@code uid} when ready.
     *
     * <p>If a session already exists the implementation may invoke {@code onReady} immediately
     * with the current {@code uid}; otherwise it performs sign-in and reports the result
     * asynchronously. Exactly one of the callbacks is invoked per call.
     *
     * @param onReady invoked with the stable {@code uid} once the Player is identified
     * @param onError invoked with the failure cause if sign-in cannot be completed
     */
    void ensureSignedIn(Callback<String> onReady, Callback<Throwable> onError);

    /**
     * Returns the current stable Player {@code uid}.
     *
     * @return the {@code uid} of the signed-in Player, or {@code null} if not yet authenticated
     */
    String currentUid();
}
