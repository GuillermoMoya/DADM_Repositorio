package co.edu.unal.reto4_tictactoe.online;

/**
 * Minimal single-argument functional callback used across the Modo_Online layer.
 *
 * <p>Keeps the {@link AuthProvider} and {@code MatchRepository} abstractions free of any
 * Firebase (or Android) types so the cloud layer stays swappable for tests (see design
 * "Components and Interfaces"). It mirrors a {@code Consumer<T>} but is defined here to avoid
 * a hard dependency on a minimum API level that ships {@code java.util.function}.
 *
 * @param <T> the type of value delivered to the callback
 */
@FunctionalInterface
public interface Callback<T> {

    /**
     * Invoked with the result of an asynchronous operation.
     *
     * @param value the delivered value (may be {@code null} depending on the operation)
     */
    void onResult(T value);
}
