// Feature: orientation-and-state-persistence, Task 1.1: full-board serialization API on TicTacToeGame
package co.edu.unal.reto4_tictactoe;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Focused JVM unit tests for the full-board serialization API added to
 * {@link TicTacToeGame} ({@code getBoardState():char[]} and {@code setBoardState(char[])}).
 *
 * <p>These pure methods are JVM-testable without an emulator because they operate only on
 * the model's {@code char[]} board.
 *
 * <p>Validates: Requirements 4.1, 4.2, 4.3, 4.4
 */
class BoardStateSerializationTest {

    private static char[] sampleBoard() {
        return new char[] {
                TicTacToeGame.HUMAN_PLAYER, TicTacToeGame.OPEN_SPOT, TicTacToeGame.COMPUTER_PLAYER,
                TicTacToeGame.OPEN_SPOT, TicTacToeGame.HUMAN_PLAYER, TicTacToeGame.OPEN_SPOT,
                TicTacToeGame.COMPUTER_PLAYER, TicTacToeGame.OPEN_SPOT, TicTacToeGame.HUMAN_PLAYER
        };
    }

    @Test
    @DisplayName("getBoardState() returns all 9 cells in index order")
    void getBoardStateReturnsNineCells() {
        TicTacToeGame game = new TicTacToeGame();
        char[] state = game.getBoardState();

        assertEquals(TicTacToeGame.BOARD_SIZE, state.length, "must return exactly 9 cells");
        for (int i = 0; i < TicTacToeGame.BOARD_SIZE; i++) {
            assertEquals(game.getBoardState(i), state[i], "cell " + i + " must match single-cell getter");
        }
    }

    @Test
    @DisplayName("round-trip: setBoardState then getBoardState yields equal cells")
    void roundTripPreservesCells() {
        TicTacToeGame game = new TicTacToeGame();
        char[] expected = sampleBoard();

        game.setBoardState(expected);

        assertArrayEquals(expected, game.getBoardState(), "round-tripped board must equal the input");
    }

    @Test
    @DisplayName("mutating the returned array does not change the model")
    void returnedArrayIsDefensiveCopy() {
        TicTacToeGame game = new TicTacToeGame();
        game.setBoardState(sampleBoard());
        char[] snapshot = game.getBoardState();

        char[] returned = game.getBoardState();
        Arrays.fill(returned, TicTacToeGame.COMPUTER_PLAYER);

        assertArrayEquals(snapshot, game.getBoardState(),
                "mutating the array returned by getBoardState() must not affect the model");
    }

    @Test
    @DisplayName("mutating the caller's array after setBoardState does not change the model")
    void callerArrayIsCopiedDefensively() {
        TicTacToeGame game = new TicTacToeGame();
        char[] input = sampleBoard();
        char[] expected = input.clone();

        game.setBoardState(input);
        Arrays.fill(input, TicTacToeGame.HUMAN_PLAYER);

        assertArrayEquals(expected, game.getBoardState(),
                "mutating the caller's array after setBoardState() must not affect the model");
    }

    @Test
    @DisplayName("setBoardState(null) leaves the board unchanged")
    void nullInputLeavesBoardUnchanged() {
        TicTacToeGame game = new TicTacToeGame();
        char[] expected = sampleBoard();
        game.setBoardState(expected);

        game.setBoardState(null);

        assertArrayEquals(expected, game.getBoardState(), "null input must be ignored (no change)");
    }

    @Test
    @DisplayName("setBoardState with wrong-length array leaves the board unchanged")
    void wrongLengthInputLeavesBoardUnchanged() {
        TicTacToeGame game = new TicTacToeGame();
        char[] expected = sampleBoard();
        game.setBoardState(expected);

        game.setBoardState(new char[] { 'X', 'O', 'X' });          // too short
        assertArrayEquals(expected, game.getBoardState(), "3-length input must be ignored");

        game.setBoardState(new char[TicTacToeGame.BOARD_SIZE + 1]); // too long
        assertArrayEquals(expected, game.getBoardState(), "10-length input must be ignored");

        game.setBoardState(new char[0]);                            // empty
        assertArrayEquals(expected, game.getBoardState(), "empty input must be ignored");
    }
}
