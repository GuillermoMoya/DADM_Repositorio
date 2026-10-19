package co.edu.unal.reto4_tictactoe.online;

/**
 * Real-time observer for a value that the cloud pushes whenever it changes.
 *
 * <p>Used by the observation methods of {@link MatchRepository}: {@code observeOpenMatches}
 * delivers the current {@code List<MatchSummary>} of WAITING matches, and {@code observeMatch}
 * delivers the authoritative {@link MatchState} of a single match. The callback fires on the
 * initial snapshot and again on every subsequent change, until its {@link ListenerHandle} is
 * detached (design "Components and Interfaces" §2, Req 6.3, 8.2, 8.3, 17.1).
 *
 * <p>Kept free of any Firebase (or Android) types so the cloud layer stays swappable for tests.
 *
 * @param <T> the type of value observed (e.g. {@code List<MatchSummary>} or {@link MatchState})
 */
@FunctionalInterface
public interface Listener<T> {

    /**
     * Invoked with the latest value each time the observed data changes.
     *
     * @param value the current value from the cloud; never {@code null} for a present node
     *              (observers of an absent node receive the implementation's empty/absent
     *              representation, e.g. an empty list)
     */
    void onChanged(T value);
}
