package co.edu.unal.reto4_tictactoe;

/**
 * Pure, Android-free seam over an audio player used for move sounds.
 *
 * <p>{@code android.media.MediaPlayer} cannot be instantiated on the JVM, so the
 * lifecycle logic that {@code MainActivity.onResume}/{@code onPause} performs is
 * expressed against this interface instead. A real implementation wraps a
 * {@code MediaPlayer}; a fake implementation (used in tests) merely flips a flag on
 * {@link #release()} so the harness can be verified without Android.
 */
public interface SoundPlayer {

    /** Play the sound from the start (seeking back if it is already playing). */
    void play();

    /** Release the underlying resources. After this, {@link #isReleased()} is {@code true}. */
    void release();

    /** @return {@code true} once {@link #release()} has been invoked on this player. */
    boolean isReleased();
}
