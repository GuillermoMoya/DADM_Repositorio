// Feature: custom-boardview-graphics-sound, Task 9.3: startNewGame clears state and sets human-first status
package co.edu.unal.reto4_tictactoe;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Example (non-property) unit tests for the "new game" behavior.
 *
 * <p>{@code MainActivity.startNewGame()} clears the board via {@link TicTacToeGame#clearBoard()}
 * and sets the human-first status via {@link GameStatus#newGameStatus()}. Because the board
 * clearing lives in the JVM-testable {@link TicTacToeGame} and the status decision lives in the
 * pure {@link GameStatus} helper, both halves of the behavior can be verified without an emulator.
 *
 * <p>Validates: Requirements 8.1
 */
class StartNewGameTest {

    @Test
    @DisplayName("a new game reports the human-first status")
    void newGameStatusIsHumanFirst() {
        assertEquals(GameStatus.NEW_GAME_HUMAN_FIRST, GameStatus.newGameStatus());
    }

    @Test
    @DisplayName("clearing the board opens every one of the nine cells")
    void clearBoardOpensEveryCell() {
        TicTacToeGame game = new TicTacToeGame();

        // Dirty several cells with both players before starting over.
        game.setMove(TicTacToeGame.HUMAN_PLAYER, 0);
        game.setMove(TicTacToeGame.COMPUTER_PLAYER, 4);
        game.setMove(TicTacToeGame.HUMAN_PLAYER, 8);

        game.clearBoard();

        for (int i = 0; i < TicTacToeGame.BOARD_SIZE; i++) {
            assertEquals(TicTacToeGame.OPEN_SPOT, game.getBoardState(i),
                    "cell " + i + " must be open after clearBoard()");
        }
    }

    @Test
    @DisplayName("after a new game the board is a clean, continuing game (no winner)")
    void newGameHasNoWinner() {
        TicTacToeGame game = new TicTacToeGame();
        game.setMove(TicTacToeGame.HUMAN_PLAYER, 1);
        game.setMove(TicTacToeGame.COMPUTER_PLAYER, 2);

        game.clearBoard();

        // A freshly cleared board continues (code 0) and its status is the human's turn.
        assertEquals(0, game.checkForWinner());
        assertEquals(GameStatus.HUMAN_TURN, GameStatus.statusFor(game.checkForWinner()));
    }
}
