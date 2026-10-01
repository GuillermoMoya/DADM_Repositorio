// Feature: custom-boardview-graphics-sound, Task 9.3: difficulty selection sets the correct DifficultyLevel
package co.edu.unal.reto4_tictactoe;

import co.edu.unal.reto4_tictactoe.TicTacToeGame.DifficultyLevel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Example (non-property) unit tests for difficulty selection.
 *
 * <p>The difficulty dialog in {@code MainActivity} maps the chosen index (0 Easy, 1 Harder,
 * 2 Expert) to a {@link DifficultyLevel} through the pure {@link Difficulty} helper, then applies
 * it with {@link TicTacToeGame#setDifficultyLevel(DifficultyLevel)}. These tests verify both the
 * index &rarr; level mapping and that applying it to {@link TicTacToeGame} round-trips through
 * {@link TicTacToeGame#getDifficultyLevel()}.
 *
 * <p>Validates: Requirements 8.6
 */
class DifficultySelectionTest {

    @Test
    @DisplayName("index 0 selects Easy and applies it to the game")
    void indexZeroSelectsEasy() {
        assertEquals(DifficultyLevel.Easy, Difficulty.difficultyFor(0));

        TicTacToeGame game = new TicTacToeGame();
        game.setDifficultyLevel(Difficulty.difficultyFor(0));
        assertEquals(DifficultyLevel.Easy, game.getDifficultyLevel());
    }

    @Test
    @DisplayName("index 1 selects Harder and applies it to the game")
    void indexOneSelectsHarder() {
        assertEquals(DifficultyLevel.Harder, Difficulty.difficultyFor(1));

        TicTacToeGame game = new TicTacToeGame();
        game.setDifficultyLevel(Difficulty.difficultyFor(1));
        assertEquals(DifficultyLevel.Harder, game.getDifficultyLevel());
    }

    @Test
    @DisplayName("index 2 selects Expert and applies it to the game")
    void indexTwoSelectsExpert() {
        assertEquals(DifficultyLevel.Expert, Difficulty.difficultyFor(2));

        TicTacToeGame game = new TicTacToeGame();
        game.setDifficultyLevel(Difficulty.difficultyFor(2));
        assertEquals(DifficultyLevel.Expert, game.getDifficultyLevel());
    }

    @Test
    @DisplayName("index <-> level mapping is a consistent round-trip for every level")
    void indexAndLevelRoundTrip() {
        for (DifficultyLevel level : DifficultyLevel.values()) {
            assertEquals(level, Difficulty.difficultyFor(Difficulty.indexFor(level)),
                    "round-trip must preserve " + level);
        }
    }

    @Test
    @DisplayName("an out-of-range selection index is rejected")
    void unknownIndexIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> Difficulty.difficultyFor(3));
        assertThrows(IllegalArgumentException.class, () -> Difficulty.difficultyFor(-1));
    }
}
