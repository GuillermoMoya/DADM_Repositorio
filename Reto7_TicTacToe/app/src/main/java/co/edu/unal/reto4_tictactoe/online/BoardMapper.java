package co.edu.unal.reto4_tictactoe.online;

import co.edu.unal.reto4_tictactoe.TicTacToeGame;

/**
 * Pure, stateless helpers that map between the authoritative {@code board} field of a
 * {@link MatchState} (a 9-character {@link String}) and the {@code char[9]} board representation of
 * {@link TicTacToeGame} (design §Mapeo {@code board} ↔ {@code TicTacToeGame char[9]}).
 *
 * <p>The cloud stores the board as a string of exactly {@value TicTacToeGame#BOARD_SIZE}
 * characters in index order {@code 0..8}, where each character is one of:
 *
 * <ul>
 *   <li>{@code ' '} — empty cell ({@link TicTacToeGame#OPEN_SPOT}).</li>
 *   <li>{@code 'X'} — host mark ({@link TicTacToeGame#HUMAN_PLAYER}); the Anfitrión.</li>
 *   <li>{@code 'O'} — guest mark ({@link TicTacToeGame#COMPUTER_PLAYER}); the Invitado.</li>
 * </ul>
 *
 * <p>These methods are the single conversion seam between the cloud string form and the
 * {@code TicTacToeGame} char-array form. The conversion is <b>lossless</b> for any valid 9-char
 * board: {@code toBoardString(toCharArray(s)).equals(s)} and
 * {@code Arrays.equals(toCharArray(toBoardString(c)), c)} both hold (verified as Property 6 in
 * task 2.3). Req 9.2, 13.3, 20.1.
 *
 * <p>This class only touches the board representation; it never validates turn legality, winner,
 * or lifecycle. It is a utility class and must not be instantiated.
 */
public final class BoardMapper {

    private BoardMapper() {
        // Utility class: no instances.
    }

    /**
     * Serializes a {@code char[9]} board to its 9-character cloud string form, cell by cell in
     * index order {@code 0..8}.
     *
     * <p>No per-cell normalization is applied: each character is copied verbatim, so a lossless
     * round-trip is preserved for any input (including unexpected characters). Callers that need a
     * valid board should pass only {@code ' '}/{@code 'X'}/{@code 'O'} cells.
     *
     * @param board the board cells; must be non-{@code null} and exactly
     *     {@link TicTacToeGame#BOARD_SIZE} long.
     * @return a {@link String} of exactly {@value TicTacToeGame#BOARD_SIZE} characters.
     * @throws IllegalArgumentException if {@code board} is {@code null} or not of length
     *     {@link TicTacToeGame#BOARD_SIZE}.
     */
    public static String toBoardString(char[] board) {
        if (board == null || board.length != TicTacToeGame.BOARD_SIZE) {
            throw new IllegalArgumentException(
                    "board must be a non-null char[" + TicTacToeGame.BOARD_SIZE + "]");
        }
        return new String(board);
    }

    /**
     * Deserializes a 9-character cloud string into a fresh {@code char[9]} board, in index order
     * {@code 0..8}. The returned array is a new array owned by the caller.
     *
     * @param board the 9-character cloud string; must be non-{@code null} and exactly
     *     {@link TicTacToeGame#BOARD_SIZE} long.
     * @return a new {@code char[}{@value TicTacToeGame#BOARD_SIZE}{@code ]} with the board cells.
     * @throws IllegalArgumentException if {@code board} is {@code null} or its length is not
     *     {@link TicTacToeGame#BOARD_SIZE}.
     */
    public static char[] toCharArray(String board) {
        if (board == null || board.length() != TicTacToeGame.BOARD_SIZE) {
            throw new IllegalArgumentException(
                    "board must be a non-null 9-char string");
        }
        return board.toCharArray();
    }

    /**
     * Loads a 9-character cloud {@code board} string into the given {@link TicTacToeGame} using its
     * {@link TicTacToeGame#setBoardState(char[])} seam (cloud → client read path).
     *
     * <p>Mirrors the design's read path {@code game.setBoardState(board.toCharArray())}. The
     * {@code setBoardState} guard already ignores arrays whose length is not
     * {@link TicTacToeGame#BOARD_SIZE}, so a malformed cloud value leaves the game unchanged
     * (robustness against corrupt data); a {@code null} board is likewise ignored.
     *
     * @param game the render-only game to update; ignored if {@code null}.
     * @param board the cloud board string; a {@code null} or non-9-char value leaves {@code game}
     *     unchanged.
     */
    public static void applyToGame(TicTacToeGame game, String board) {
        if (game == null || board == null) {
            return;
        }
        // setBoardState guards against length != BOARD_SIZE, so a corrupt string is a no-op.
        game.setBoardState(board.toCharArray());
    }

    /**
     * Reads the current board of a {@link TicTacToeGame} as a 9-character cloud {@code board}
     * string (client → cloud write path), mirroring {@code new String(game.getBoardState())}.
     *
     * @param game the game whose board to serialize; must be non-{@code null}.
     * @return the 9-character board string.
     * @throws IllegalArgumentException if {@code game} is {@code null}.
     */
    public static String fromGame(TicTacToeGame game) {
        if (game == null) {
            throw new IllegalArgumentException("game must be non-null");
        }
        return new String(game.getBoardState());
    }
}
