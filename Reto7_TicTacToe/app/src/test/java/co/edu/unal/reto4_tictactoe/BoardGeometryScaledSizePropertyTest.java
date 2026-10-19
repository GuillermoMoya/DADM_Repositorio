// Feature: custom-boardview-graphics-sound, Property 6: Marks are scaled to fit within their cell
package co.edu.unal.reto4_tictactoe;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Property test for {@link BoardGeometry#scaledSize(int, int, int, int, int)}.
 *
 * <p><b>Property 6: Marks are scaled to fit within their cell.</b> For any positive
 * bitmap and cell dimensions with a non-negative inset, the returned scaled size must
 * never exceed the cell bounds: {@code width <= cellW} and {@code height <= cellH}.
 *
 * <b>Validates: Requirements 4.5</b>
 */
class BoardGeometryScaledSizePropertyTest {

    @Property(tries = 100)
    void scaledMarkFitsWithinCell(
            @ForAll @IntRange(min = 1, max = 4000) int bitmapW,
            @ForAll @IntRange(min = 1, max = 4000) int bitmapH,
            @ForAll @IntRange(min = 1, max = 4000) int cellW,
            @ForAll @IntRange(min = 1, max = 4000) int cellH,
            @ForAll @IntRange(min = 0, max = 200) int inset) {

        int[] size = BoardGeometry.scaledSize(bitmapW, bitmapH, cellW, cellH, inset);

        int width = size[0];
        int height = size[1];

        // The scaled mark must fit within the cell it is drawn into.
        assertTrue(width <= cellW,
                "scaled width " + width + " must be <= cell width " + cellW);
        assertTrue(height <= cellH,
                "scaled height " + height + " must be <= cell height " + cellH);
    }
}
