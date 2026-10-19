package co.edu.unal.reto4_tictactoe;

/**
 * Pure, Android-free harness that mirrors the MediaPlayer lifecycle handled by
 * {@code MainActivity.onResume}/{@code onPause}.
 *
 * <p>It holds two nullable player references (human and computer move sounds).
 * {@link #resume(Factory)} creates both players; {@link #pause()} releases each
 * non-null player and then nulls the field &mdash; exactly mirroring the field
 * handling in {@code MainActivity}:
 *
 * <pre>
 *   onResume(): mHumanMoveSound = MediaPlayer.create(...);
 *               mComputerMoveSound = MediaPlayer.create(...);
 *   onPause():  if (mHumanMoveSound != null)    { mHumanMoveSound.release();    mHumanMoveSound = null; }
 *               if (mComputerMoveSound != null) { mComputerMoveSound.release(); mComputerMoveSound = null; }
 * </pre>
 *
 * <p>By construction, no released player is ever retained in a field after a
 * {@link #pause()} (Requirement 10.4).
 */
public class SoundLifecycle {

    /** Factory that produces fresh {@link SoundPlayer} instances on resume. */
    public interface Factory {
        SoundPlayer create();
    }

    private SoundPlayer mHumanMoveSound;
    private SoundPlayer mComputerMoveSound;

    /**
     * Mirror of {@code onResume}: (re)create both move-sound players.
     *
     * @param factory produces the fresh player instances
     */
    public void resume(Factory factory) {
        mHumanMoveSound = factory.create();
        mComputerMoveSound = factory.create();
    }

    /**
     * Mirror of {@code onPause}: release each non-null player and clear its field so
     * no released instance is retained.
     */
    public void pause() {
        if (mHumanMoveSound != null) {
            mHumanMoveSound.release();
            mHumanMoveSound = null;
        }
        if (mComputerMoveSound != null) {
            mComputerMoveSound.release();
            mComputerMoveSound = null;
        }
    }

    /** @return the current human move-sound player, or {@code null} when paused/never resumed. */
    public SoundPlayer getHumanMoveSound() {
        return mHumanMoveSound;
    }

    /** @return the current computer move-sound player, or {@code null} when paused/never resumed. */
    public SoundPlayer getComputerMoveSound() {
        return mComputerMoveSound;
    }
}
