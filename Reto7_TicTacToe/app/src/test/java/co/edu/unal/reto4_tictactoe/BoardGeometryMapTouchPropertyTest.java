// Feature: custom-boardview-graphics-sound, Property 1: Touch mapping is a total function to a valid cell or none
package co.edu.unal.reto4_tictactoe;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Property 1: Touch mapping is a total function to a valid cell or none.
 *
 * <p>{@link BoardGeometry#mapTouch(int, int, int, int, int)} must, for every
 * possible input, return either a valid row-major position {@code 0..8} or
 * {@link BoardGeometry#NONE} ({@code -1}). This test generates {@code (x, y)}
 * coordinates that fall across the whole relevant range — well before the board
 * origin, across cell interiors, near/on the interior grid-line bands, on the
 * far edges, and beyond the board — always with positive cell dimensions, and
 * asserts the returned value matches an independent oracle of the documented
 * semantics.
 */
class BoardGeometryMapTouchPropertyTest {

    private static final int GRID = 3;

    /**
     * The result is always total: it is either NONE (-1) or a valid cell 0..8,
     * and it exactly matches the independent oracle, for coordinates ranging
     * from below the origin to beyond the board with positive cell dims.
     */
    @Property(tries = 100)
    void mapTouchIsTotalAndMatchesOracle(
            @ForAll @IntRange(min = 1, max = 200) int cellW,
            @ForAll @IntRange(min = 1, max = 200) int cellH,
            @ForAll @IntRange(min = 0, max = 20) int gridStroke,
            @ForAll @IntRange(min = -50, max = 650) int x,
            @ForAll @IntRange(min = -50, max = 650) int y) {

        int actual = BoardGeometry.mapTouch(x, y, cellW, cellH, gridStroke);

        // Totality: the result is always NONE or a valid cell index.
        assertTrue(actual == BoardGeometry.NONE || (actual >= 0 && actual <= 8),
                "mapTouch must return -1 or 0..8, got " + actual);

        int expected = oracle(x, y, cellW, cellH, gridStroke);
        assertEquals(expected, actual,
                "mapTouch mismatch for x=" + x + ", y=" + y
                        + ", cellW=" + cellW + ", cellH=" + cellH
                        + ", gridStroke=" + gridStroke);
    }

    /**
     * Interior touches (comfortably away from any grid-line band and inside the
     * board) always map to the expected row-major cell. Generated col/row select
     * the target cell; the offset is kept clear of the grid bands.
     */
    @Property(tries = 100)
    void interiorTouchesMapToTheirCell(
            @ForAll @IntRange(min = 1, max = 200) int cellW,
            @ForAll @IntRange(min = 1, max = 200) int cellH,
            @ForAll @IntRange(min = 0, max = 20) int gridStroke,
            @ForAll @IntRange(min = 0, max = 2) int col,
            @ForAll @IntRange(min = 0, max = 2) int row) {

        int half = gridStroke / 2;
        // Only exercise cells large enough to contain a point clear of both the
        // cell edges and the interior grid-line band.
        if (cellW <= 2 * half + 1 || cellH <= 2 * half + 1) {
            return;
        }

        int x = col * cellW + (half + 1);
        int y = row * cellH + (half + 1);

        // Guard: keep strictly inside the board (matters for the far column/row
        // when the +offset could otherwise land on the outer edge boundary).
        assertTrue(x < GRID * cellW && y < GRID * cellH);

        int actual = BoardGeometry.mapTouch(x, y, cellW, cellH, gridStroke);
        assertEquals(row * GRID + col, actual,
                "interior touch should map to its cell; x=" + x + ", y=" + y);
    }

    /**
     * Out-of-bounds coordinates (and the not-laid-out case) always return NONE.
     */
    @Property(tries = 100)
    void outOfBoundsReturnsNone(
            @ForAll @IntRange(min = 1, max = 200) int cellW,
            @ForAll @IntRange(min = 1, max = 200) int cellH,
            @ForAll @IntRange(min = 0, max = 20) int gridStroke,
            @ForAll @IntRange(min = -300, max = 900) int x,
            @ForAll @IntRange(min = -300, max = 900) int y) {

        int boardW = GRID * cellW;
        int boardH = GRID * cellH;

        // Only assert when the point is genuinely outside the board.
        if (x < 0 || y < 0 || x >= boardW || y >= boardH) {
            assertEquals(BoardGeometry.NONE,
                    BoardGeometry.mapTouch(x, y, cellW, cellH, gridStroke),
                    "out-of-bounds must map to NONE; x=" + x + ", y=" + y);
        }
    }

    /**
     * Independent reimplementation of the documented mapTouch semantics, used as
     * the property oracle.
     */
    private static int oracle(int x, int y, int cellW, int cellH, int gridStroke) {
        if (cellW <= 0 || cellH <= 0) {
            return BoardGeometry.NONE;
        }
        int boardW = GRID * cellW;
        int boardH = GRID * cellH;
        if (x < 0 || y < 0 || x >= boardW || y >= boardH) {
            return BoardGeometry.NONE;
        }
        int col = Math.min(x / cellW, GRID - 1);
        int row = Math.min(y / cellH, GRID - 1);
        if (onGridLine(x, cellW, gridStroke) || onGridLine(y, cellH, gridStroke)) {
            return BoardGeometry.NONE;
        }
        return row * GRID + col;
    }

    private static boolean onGridLine(int coord, int cellSize, int gridStroke) {
        if (gridStroke <= 0) {
            return false;
        }
        int half = gridStroke / 2;
        for (int line = 1; line < GRID; line++) {
            int boundary = line * cellSize;
            if (Math.abs(coord - boundary) <= half) {
                return true;
            }
        }
        return false;
    }
}
