package co.edu.unal.reto4_tictactoe.online;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.edu.unal.reto4_tictactoe.TicTacToeGame;
import java.util.Arrays;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Property-based test for the board round trip between the cloud {@code board} string form and the
 * {@link TicTacToeGame} {@code char[9]} form.
 *
 * <p>Covers design §Correctness Properties → Property 6 and §Testing Strategy: a single correctness
 * property (verified from two angles), minimum 100 iterations, over valid {@code char[9]} boards
 * (cells in {@code {'X','O',' '}}).
 */
// Feature: online-multiplayer, Property 6: round trip del tablero
class BoardMapperRoundTripPropertyTest {

    /**
     * Generates valid 9-cell boards: each of the {@link TicTacToeGame#BOARD_SIZE} cells is one of
     * {@code ' '} (empty), {@code 'X'} (host) or {@code 'O'} (guest), matching the mark alphabet
     * the cloud stores (design §Mapeo {@code board} ↔ {@code char[9]}).
     */
    @Provide
    Arbitrary<char[]> validBoards() {
        Arbitrary<Character> cell =
                Arbitraries.of(
                        TicTacToeGame.OPEN_SPOT,
                        TicTacToeGame.HUMAN_PLAYER,
                        TicTacToeGame.COMPUTER_PLAYER);
        return cell.list()
                .ofSize(TicTacToeGame.BOARD_SIZE)
                .map(
                        list -> {
                            char[] board = new char[TicTacToeGame.BOARD_SIZE];
                            for (int i = 0; i < TicTacToeGame.BOARD_SIZE; i++) {
                                board[i] = list.get(i);
                            }
                            return board;
                        });
    }

    /**
     * Property 6: round-tripping a valid {@code char[9]} through the cloud string form and back —
     * via {@link BoardMapper#toBoardString(char[])} then {@link TicTacToeGame#setBoardState(char[])}
     * — reproduces an identical board. The cloud↔client conversion is lossless.
     *
     * <p>Validates: Requirements 9.2, 13.3.
     */
    @Property(tries = 100)
    void roundTripThroughSetBoardStateIsLossless(@ForAll("validBoards") char[] original) {
        // char[9] -> cloud 9-char string.
        String serialized = BoardMapper.toBoardString(original);
        assertEquals(TicTacToeGame.BOARD_SIZE, serialized.length(), "serialized board length");

        // cloud string -> TicTacToeGame via setBoardState (the authoritative read path).
        TicTacToeGame game = new TicTacToeGame();
        game.setBoardState(BoardMapper.toCharArray(serialized));

        // The reloaded board must equal the original cell for cell.
        assertArrayEquals(original, game.getBoardState(), "reloaded board via setBoardState");

        // The applyToGame seam must produce the same result as the explicit path above.
        TicTacToeGame viaApply = new TicTacToeGame();
        BoardMapper.applyToGame(viaApply, serialized);
        assertArrayEquals(original, viaApply.getBoardState(), "reloaded board via applyToGame");

        // Serializing the reloaded game back must reproduce the exact same string (string identity).
        assertEquals(serialized, BoardMapper.fromGame(game), "re-serialized board string");
    }

    /**
     * Property 6 (pure mapping identity): {@code toCharArray(toBoardString(c))} equals {@code c},
     * {@code toBoardString(toCharArray(s)).equals(s)}, and the decoded array is a fresh copy —
     * independent of {@link TicTacToeGame}.
     *
     * <p>Validates: Requirements 9.2, 13.3.
     */
    @Property(tries = 100)
    void pureMapperRoundTripIsLossless(@ForAll("validBoards") char[] original) {
        String serialized = BoardMapper.toBoardString(original);

        // string -> char[] -> string reproduces the string, cell for cell.
        char[] decoded = BoardMapper.toCharArray(serialized);
        assertArrayEquals(original, decoded, "decoded char[] equals original");
        assertEquals(serialized, BoardMapper.toBoardString(decoded), "re-serialized string");

        // The decoded array is a fresh copy, not the same reference as the input.
        assertNotSame(original, decoded, "decoded array must be a fresh copy");
        assertTrue(Arrays.equals(decoded, original), "decoded array content matches original");
    }
}
