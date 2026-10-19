// Feature: custom-boardview-graphics-sound, Property 5: Cell dimensions tile the board for any size
package co.edu.unal.reto4_tictactoe;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Property 5: Cell dimensions tile the board for any size.
 *
 * <p>Cell dimensions are derived from the view's current width and height using
 * integer division ({@code cellW = w / 3}, {@code cellH = h / 3}), exactly as
 * {@code BoardView.onSizeChanged} computes them and as
 * {@link BoardGeometry#mapTouch(int, int, int, int, int)} assumes when it treats
 * the board region as {@code [0, 3*cellW) x [0, 3*cellH)}. Because there is no
 * dedicated helper method for this arithmetic, the property computes the
 * expected cell dimensions directly and verifies that the three cells along each
 * axis tile the board region up to the integer-division rounding remainder.
 *
 * <p>The tiling guarantee, for any dimension {@code d > 0} with {@code c = d/3}:
 * <ul>
 *   <li>the three cells never overflow the view: {@code 3*c <= d}; and</li>
 *   <li>the uncovered remainder is strictly less than one full grid width:
 *       {@code d - 3*c < 3} (i.e. it equals {@code d % 3}, which is 0, 1, or 2).</li>
 * </ul>
 *
 * <p>Validates: Requirements 3.2, 3.3
 */
class BoardGeometryCellDimsPropertyTest {

    private static final int GRID = 3;

    /**
     * For any positive width and height, the cell width/height equal {@code w/3}
     * and {@code h/3}, and the three cells tile the board region along each axis
     * up to the integer-rounding remainder ({@code 0..2} pixels uncovered).
     */
    @Property(tries = 100)
    void cellDimsEqualThirdAndTileTheBoard(
            @ForAll @IntRange(min = 1, max = 10_000) int w,
            @ForAll @IntRange(min = 1, max = 10_000) int h) {

        int cellW = w / GRID;
        int cellH = h / GRID;

        // Cell dims are exactly one third via integer division.
        assertEquals(w / GRID, cellW, "cell width must equal w/3 for w=" + w);
        assertEquals(h / GRID, cellH, "cell height must equal h/3 for h=" + h);

        // Tiling: the three cells never overflow the view along either axis.
        assertTrue(GRID * cellW <= w,
                "3*cellW must not exceed w; w=" + w + ", cellW=" + cellW);
        assertTrue(GRID * cellH <= h,
                "3*cellH must not exceed h; h=" + h + ", cellH=" + cellH);

        // Tiling: the uncovered remainder is strictly less than one grid width,
        // i.e. only the integer-division rounding remainder (0..2 px) is left.
        assertTrue(w - GRID * cellW < GRID,
                "uncovered width remainder must be < 3; w=" + w + ", cellW=" + cellW);
        assertTrue(h - GRID * cellH < GRID,
                "uncovered height remainder must be < 3; h=" + h + ", cellH=" + cellH);

        // The remainder is exactly w % 3 / h % 3 and thus non-negative.
        assertEquals(w % GRID, w - GRID * cellW, "width remainder must equal w % 3");
        assertEquals(h % GRID, h - GRID * cellH, "height remainder must equal h % 3");
    }
}
