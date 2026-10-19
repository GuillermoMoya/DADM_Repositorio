// Feature: custom-boardview-graphics-sound, Property 4: Board rendering reflects getBoardState exactly
package co.edu.unal.reto4_tictactoe;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Arbitraries;

/**
 * Property 4: Board rendering reflects getBoardState exactly.
 *
 * <p>{@code BoardView.onDraw} decides what to draw in each cell solely from the
 * game's board state via {@link BoardGeometry#markFor(char)}. This test verifies
 * that pure decision:
 * <ul>
 *   <li>Every {@code HUMAN_PLAYER} ('X') state maps to {@link BoardGeometry.Mark#HUMAN}.</li>
 *   <li>Every {@code COMPUTER_PLAYER} ('O') state maps to {@link BoardGeometry.Mark#COMPUTER}.</li>
 *   <li>Every {@code OPEN_SPOT} (' ') state maps to {@link BoardGeometry.Mark#NONE}.</li>
 *   <li>An all-open board yields zero drawn marks.</li>
 * </ul>
 *
 * <p>Validates: Requirements 4.2, 4.3, 4.4, 5.1, 7.1, 7.2, 7.3, 11.1
 */
class BoardGeometryMarkForPropertyTest {

    private static final char HUMAN_PLAYER = 'X';
    private static final char COMPUTER_PLAYER = 'O';
    private static final char OPEN_SPOT = ' ';
    private static final int BOARD_SIZE = 9;

    /**
     * For any board of X/O/' ' states, the per-cell draw decision from
     * {@link BoardGeometry#markFor} matches the underlying cell state exactly.
     */
    @Property(tries = 100)
    boolean markForMatchesEachCellState(@ForAll("boards") char[] board) {
        if (board.length != BOARD_SIZE) {
            return false;
        }

        for (char state : board) {
            BoardGeometry.Mark expected = expectedMarkFor(state);
            if (BoardGeometry.markFor(state) != expected) {
                return false;
            }
        }
        return true;
    }

    /**
     * An all-open board produces no marks to draw: every cell resolves to NONE.
     */
    @Property(tries = 100)
    boolean allOpenBoardYieldsZeroMarks(@ForAll("allOpenBoards") char[] board) {
        int marks = 0;
        for (char state : board) {
            if (BoardGeometry.markFor(state) != BoardGeometry.Mark.NONE) {
                marks++;
            }
        }
        return marks == 0;
    }

    private BoardGeometry.Mark expectedMarkFor(char state) {
        if (state == HUMAN_PLAYER) {
            return BoardGeometry.Mark.HUMAN;
        }
        if (state == COMPUTER_PLAYER) {
            return BoardGeometry.Mark.COMPUTER;
        }
        return BoardGeometry.Mark.NONE;
    }

    @Provide
    Arbitrary<char[]> boards() {
        Arbitrary<Character> cell =
                Arbitraries.of(HUMAN_PLAYER, COMPUTER_PLAYER, OPEN_SPOT);
        return cell.array(Character[].class).ofSize(BOARD_SIZE).map(this::toCharArray);
    }

    @Provide
    Arbitrary<char[]> allOpenBoards() {
        char[] board = new char[BOARD_SIZE];
        for (int i = 0; i < BOARD_SIZE; i++) {
            board[i] = OPEN_SPOT;
        }
        return Arbitraries.just(board);
    }

    private char[] toCharArray(Character[] boxed) {
        char[] result = new char[boxed.length];
        for (int i = 0; i < boxed.length; i++) {
            result[i] = boxed[i];
        }
        return result;
    }
}
