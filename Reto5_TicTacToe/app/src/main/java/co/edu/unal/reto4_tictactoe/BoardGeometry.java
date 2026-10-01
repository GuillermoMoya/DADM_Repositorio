package co.edu.unal.reto4_tictactoe;

/**
 * Pure, Android-free geometry helpers for the custom board view.
 *
 * <p>This class deliberately has NO Android imports so that all of its logic can
 * be exercised by plain JVM unit / property tests without an emulator. It hosts
 * the core arithmetic that {@code BoardView} delegates to: touch-to-position
 * mapping, the per-cell "what to draw" decision, and mark scaling.
 */
public final class BoardGeometry {

    /** Row-major position returned when a touch maps to no cell. */
    public static final int NONE = -1;

    /** Board is 3x3. */
    private static final int GRID = 3;

    // Player state characters, mirrored from TicTacToeGame to avoid an Android
    // dependency chain in tests. These MUST match TicTacToeGame's constants.
    private static final char HUMAN_PLAYER = 'X';
    private static final char COMPUTER_PLAYER = 'O';
    private static final char OPEN_SPOT = ' ';

    private BoardGeometry() {
        // Utility class; not instantiable.
    }

    /** What to draw in a cell. */
    public enum Mark {
        HUMAN, COMPUTER, NONE
    }

    /**
     * Maps a touch at {@code (x, y)} to a row-major board position {@code 0..8},
     * or {@link #NONE} ({@code -1}) when the touch does not resolve to a cell.
     *
     * <p>The board occupies {@code [0, 3*cellW) x [0, 3*cellH)}. The function is
     * total: every input yields either a valid {@code 0..8} position or
     * {@code NONE}.
     *
     * <ul>
     *   <li>Returns {@code NONE} when not yet laid out ({@code cellW <= 0} or
     *       {@code cellH <= 0}).</li>
     *   <li>Returns {@code NONE} for out-of-bounds coordinates.</li>
     *   <li>Returns {@code NONE} for touches within {@code gridStroke/2} of an
     *       interior grid boundary (grid-line band).</li>
     *   <li>Otherwise returns {@code row * 3 + col}, with {@code col}/{@code row}
     *       clamped to {@code 0..2}.</li>
     * </ul>
     *
     * @param x          touch x in pixels
     * @param y          touch y in pixels
     * @param cellW      cell width in pixels ({@code viewWidth / 3})
     * @param cellH      cell height in pixels ({@code viewHeight / 3})
     * @param gridStroke grid line thickness in pixels
     * @return a position in {@code 0..8}, or {@link #NONE}
     */
    public static int mapTouch(int x, int y, int cellW, int cellH, int gridStroke) {
        // Not laid out yet.
        if (cellW <= 0 || cellH <= 0) {
            return NONE;
        }

        int boardW = GRID * cellW;
        int boardH = GRID * cellH;

        // Out of bounds.
        if (x < 0 || y < 0 || x >= boardW || y >= boardH) {
            return NONE;
        }

        int col = x / cellW;
        int row = y / cellH;

        // Clamp against floating/rounding at the far edge.
        if (col > GRID - 1) {
            col = GRID - 1;
        }
        if (row > GRID - 1) {
            row = GRID - 1;
        }

        // Grid-line band handling: reject touches within half the grid stroke of
        // an interior boundary so line taps select no cell.
        if (isOnGridLine(x, cellW, boardW, gridStroke)
                || isOnGridLine(y, cellH, boardH, gridStroke)) {
            return NONE;
        }

        return row * GRID + col;
    }

    /**
     * Whether {@code coord} lies within {@code gridStroke/2} of one of the two
     * interior boundaries at {@code cellSize} and {@code 2*cellSize} along an
     * axis whose total length is {@code boardSize}.
     */
    private static boolean isOnGridLine(int coord, int cellSize, int boardSize, int gridStroke) {
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

    /**
     * Maps a board-cell state character to the mark that should be drawn there.
     *
     * @param state one of {@code 'X'} (human), {@code 'O'} (computer), or
     *              {@code ' '} (open)
     * @return {@link Mark#HUMAN}, {@link Mark#COMPUTER}, or {@link Mark#NONE}
     */
    public static Mark markFor(char state) {
        if (state == HUMAN_PLAYER) {
            return Mark.HUMAN;
        }
        if (state == COMPUTER_PLAYER) {
            return Mark.COMPUTER;
        }
        return Mark.NONE;
    }

    /**
     * Computes the size a mark bitmap should be scaled to so it fits within a
     * cell (minus {@code inset} on each side) while preserving aspect ratio.
     *
     * @param bitmapW natural bitmap width
     * @param bitmapH natural bitmap height
     * @param cellW   cell width in pixels
     * @param cellH   cell height in pixels
     * @param inset   padding applied inside the cell on each side
     * @return a two-element array {@code {width, height}}; {@code {0, 0}} when
     *         inputs are non-positive or the available area is empty
     */
    public static int[] scaledSize(int bitmapW, int bitmapH, int cellW, int cellH, int inset) {
        if (bitmapW <= 0 || bitmapH <= 0 || cellW <= 0 || cellH <= 0) {
            return new int[]{0, 0};
        }

        int availW = cellW - 2 * inset;
        int availH = cellH - 2 * inset;
        if (availW <= 0 || availH <= 0) {
            return new int[]{0, 0};
        }

        // Scale factor that fits the bitmap inside the available area preserving
        // aspect ratio (fit-inside / "contain").
        double scale = Math.min((double) availW / bitmapW, (double) availH / bitmapH);

        int width = (int) Math.floor(bitmapW * scale);
        int height = (int) Math.floor(bitmapH * scale);

        // Guarantee at least 1px and never exceed the available area.
        width = clampToRange(width, 1, availW);
        height = clampToRange(height, 1, availH);

        return new int[]{width, height};
    }

    private static int clampToRange(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }
}
