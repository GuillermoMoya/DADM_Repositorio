// Feature: custom-boardview-graphics-sound, Task 9.3: winner-code -> status-string mapping
package co.edu.unal.reto4_tictactoe;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Example (non-property) unit tests for the winner-code &rarr; status mapping.
 *
 * <p>{@code MainActivity} maps each {@link TicTacToeGame#checkForWinner()} code to a status text.
 * That decision is extracted into the pure {@link GameStatus#statusFor(int)} helper, which
 * {@code MainActivity} delegates to via its {@code statusTextResId(GameStatus)} translator (0 &rarr;
 * {@code turn_human}, 1 &rarr; {@code result_tie}, 2 &rarr; {@code result_human_wins}, 3 &rarr;
 * {@code result_computer_wins}). These tests pin every code to the expected status on the JVM.
 *
 * <p>Validates: Requirements 8.3
 */
class GameStatusMappingTest {

    @Test
    @DisplayName("winner code 0 (continue) maps to the human's turn")
    void codeZeroMapsToHumanTurn() {
        assertEquals(GameStatus.HUMAN_TURN, GameStatus.statusFor(0));
    }

    @Test
    @DisplayName("winner code 1 maps to a tie")
    void codeOneMapsToTie() {
        assertEquals(GameStatus.TIE, GameStatus.statusFor(1));
    }

    @Test
    @DisplayName("winner code 2 maps to a human win")
    void codeTwoMapsToHumanWins() {
        assertEquals(GameStatus.HUMAN_WINS, GameStatus.statusFor(2));
    }

    @Test
    @DisplayName("winner code 3 maps to a computer win")
    void codeThreeMapsToComputerWins() {
        assertEquals(GameStatus.COMPUTER_WINS, GameStatus.statusFor(3));
    }

    @Test
    @DisplayName("only a zero winner code means the game continues; 1/2/3 are game over")
    void gameOverIffWinnerNonZero() {
        assertEquals(false, GameStatus.isGameOver(0));
        assertEquals(true, GameStatus.isGameOver(1));
        assertEquals(true, GameStatus.isGameOver(2));
        assertEquals(true, GameStatus.isGameOver(3));
    }

    @Test
    @DisplayName("an unknown winner code is rejected rather than silently mapped")
    void unknownCodeIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> GameStatus.statusFor(4));
        assertThrows(IllegalArgumentException.class, () -> GameStatus.statusFor(-1));
    }
}
