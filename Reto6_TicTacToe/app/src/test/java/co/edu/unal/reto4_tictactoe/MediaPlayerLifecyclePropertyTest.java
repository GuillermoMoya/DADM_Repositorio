// Feature: custom-boardview-graphics-sound, Property 7: No released MediaPlayer is ever retained
package co.edu.unal.reto4_tictactoe;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Property 7: No released MediaPlayer is ever retained.
 *
 * <p>{@code android.media.MediaPlayer} cannot be instantiated on the JVM, so the lifecycle logic
 * of {@code MainActivity.onResume}/{@code onPause} is exercised through a pure seam:
 * {@link SoundLifecycle} holds two nullable {@link SoundPlayer} references, {@code resume(factory)}
 * creates two players, and {@code pause()} releases each non-null player then nulls the field &mdash;
 * mirroring {@code MainActivity} exactly. The test drives random {@code resume}/{@code pause}
 * sequences against the harness with a fake player and asserts that after each {@code pause} no
 * field references a released player, and a released fake is never retained in the fields.
 *
 * <p>Validates: Requirements 10.4
 */
class MediaPlayerLifecyclePropertyTest {

    /** Lifecycle operation applied to the harness during a random sequence. */
    private enum Op { RESUME, PAUSE }

    /**
     * Fake {@link SoundPlayer} standing in for a {@code MediaPlayer}. It flips an
     * {@code isReleased} flag on {@link #release()} so the harness can be inspected on the JVM.
     * Every fake ever produced by the factory is recorded so the test can prove no released
     * instance is retained anywhere in the harness fields.
     */
    private static final class FakeSoundPlayer implements SoundPlayer {
        boolean released;

        @Override
        public void play() {
            // no-op for lifecycle testing
        }

        @Override
        public void release() {
            released = true;
        }

        @Override
        public boolean isReleased() {
            return released;
        }
    }

    /** Generates random sequences of resume/pause operations. */
    @Provide
    Arbitrary<List<Op>> opSequences() {
        return Arbitraries.of(Op.RESUME, Op.PAUSE).list().ofMinSize(0).ofMaxSize(40);
    }

    /**
     * For any random sequence of resume/pause calls, after each pause neither field references a
     * released player (both are null), and no released fake is ever retained in the harness.
     */
    @Property(tries = 200)
    void noReleasedPlayerIsEverRetained(@ForAll("opSequences") List<Op> ops) {
        SoundLifecycle lifecycle = new SoundLifecycle();
        List<FakeSoundPlayer> created = new ArrayList<>();

        SoundLifecycle.Factory factory = () -> {
            FakeSoundPlayer p = new FakeSoundPlayer();
            created.add(p);
            return p;
        };

        for (Op op : ops) {
            if (op == Op.RESUME) {
                lifecycle.resume(factory);
            } else {
                lifecycle.pause();

                // After a pause, both fields must be cleared (mirrors onPause nulling).
                assertNull(lifecycle.getHumanMoveSound(),
                        "human field must be null after pause");
                assertNull(lifecycle.getComputerMoveSound(),
                        "computer field must be null after pause");
            }

            // Invariant after every op: neither field holds a released player.
            SoundPlayer human = lifecycle.getHumanMoveSound();
            SoundPlayer computer = lifecycle.getComputerMoveSound();
            if (human != null) {
                assertFalse(human.isReleased(),
                        "a retained human player must never be released");
            }
            if (computer != null) {
                assertFalse(computer.isReleased(),
                        "a retained computer player must never be released");
            }

            // Stronger check: no released fake is retained in either field.
            for (FakeSoundPlayer fake : created) {
                if (fake.isReleased()) {
                    assertTrue(fake != human && fake != computer,
                            "a released fake must never be retained in a harness field");
                }
            }
        }
    }

    /**
     * A resume immediately followed by a pause releases exactly the two players created by that
     * resume and retains neither.
     */
    @Property(tries = 200)
    void resumeThenPauseReleasesBothAndRetainsNone(@ForAll boolean extraPause) {
        SoundLifecycle lifecycle = new SoundLifecycle();
        List<FakeSoundPlayer> created = new ArrayList<>();
        SoundLifecycle.Factory factory = () -> {
            FakeSoundPlayer p = new FakeSoundPlayer();
            created.add(p);
            return p;
        };

        lifecycle.resume(factory);
        lifecycle.pause();
        if (extraPause) {
            lifecycle.pause(); // idempotent: pausing again must stay safe
        }

        assertNull(lifecycle.getHumanMoveSound());
        assertNull(lifecycle.getComputerMoveSound());
        for (FakeSoundPlayer fake : created) {
            assertTrue(fake.isReleased(), "every created fake must have been released");
        }
    }
}
