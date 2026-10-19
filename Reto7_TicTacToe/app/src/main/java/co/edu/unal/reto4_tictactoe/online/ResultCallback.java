package co.edu.unal.reto4_tictactoe.online;

/**
 * Success/failure callback for a one-shot cloud operation that yields a result value.
 *
 * <p>Used by {@link MatchRepository#createMatch(ResultCallback)}, where the result is the newly
 * created {@code matchId}. Exactly one of {@link #onSuccess(Object)} or
 * {@link #onError(Throwable)} is invoked per operation, so the UI can react to a connection or
 * write error without being left in an inconsistent state (design "Components and Interfaces"
 * §2, Req 16.1).
 *
 * <p>Kept free of any Firebase (or Android) types so the cloud layer stays swappable for tests.
 *
 * @param <T> the type of the successful result (e.g. {@code String} matchId)
 */
public interface ResultCallback<T> {

    /**
     * Invoked when the operation completes successfully.
     *
     * @param result the operation's result value
     */
    void onSuccess(T result);

    /**
     * Invoked when the operation fails (e.g. a connection or write error).
     *
     * @param error the failure cause
     */
    void onError(Throwable error);
}
