package co.edu.unal.reto4_tictactoe.online;

/**
 * Handle returned by the real-time observation methods of {@link MatchRepository}
 * ({@code observeOpenMatches}, {@code observeMatch}).
 *
 * <p>It exists so callers (Activities) can release the underlying cloud listener when their
 * Android lifecycle requires it ({@code onPause}/{@code onStop}/{@code onDestroy}), avoiding
 * memory leaks and writes/reads after a view is gone (design "Components and Interfaces" §2,
 * Req 17.1, 17.2). The handle intentionally exposes no cloud types, keeping the abstraction
 * free of Firebase so the backing implementation stays swappable for tests.
 */
public interface ListenerHandle {

    /**
     * Detaches the associated real-time listener so no further callbacks are delivered.
     *
     * <p>Implementations must be idempotent: calling {@code detach()} more than once (or after
     * the listener has already been removed) is a no-op and must not throw.
     */
    void detach();
}
