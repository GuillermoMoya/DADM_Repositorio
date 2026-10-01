package co.edu.unal.reto4_tictactoe;

import co.edu.unal.reto4_tictactoe.TicTacToeGame.DifficultyLevel;

/**
 * Pure, Android-free mapping between the difficulty dialog's selected index and the
 * {@link DifficultyLevel} enum value.
 *
 * <p>The difficulty dialog in {@code MainActivity} maps the single-choice index
 * ({@code 0} Easy, {@code 1} Harder, {@code 2} Expert) to a {@link DifficultyLevel}. That mapping
 * is extracted here so it is unit-testable without a dialog, and {@code MainActivity} delegates to
 * it (see design "Unit / example tests (JVM)" and Requirement 8.6).
 */
public final class Difficulty {

    private Difficulty() {
        // Utility class; not instantiable.
    }

    /**
     * Maps a difficulty-dialog selection index to its {@link DifficultyLevel}.
     *
     * @param index the selected item index: {@code 0} Easy, {@code 1} Harder, {@code 2} Expert
     * @return the matching {@link DifficultyLevel}
     * @throws IllegalArgumentException if {@code index} is outside {@code 0..2}
     */
    public static DifficultyLevel difficultyFor(int index) {
        switch (index) {
            case 0:
                return DifficultyLevel.Easy;
            case 1:
                return DifficultyLevel.Harder;
            case 2:
                return DifficultyLevel.Expert;
            default:
                throw new IllegalArgumentException("Unknown difficulty index: " + index);
        }
    }

    /**
     * Inverse of {@link #difficultyFor(int)}: maps a {@link DifficultyLevel} back to the index the
     * difficulty dialog should pre-select.
     *
     * @param level the current difficulty level
     * @return the dialog index: {@code 0} Easy, {@code 1} Harder, {@code 2} Expert
     */
    public static int indexFor(DifficultyLevel level) {
        switch (level) {
            case Easy:
                return 0;
            case Harder:
                return 1;
            case Expert:
                return 2;
            default:
                throw new IllegalArgumentException("Unknown difficulty level: " + level);
        }
    }
}
