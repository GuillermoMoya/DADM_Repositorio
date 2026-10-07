package co.edu.unal.reto4_tictactoe;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * Custom view that draws the Tic-Tac-Toe board (grid + player marks) directly on
 * a {@link android.graphics.Canvas} and maps touches to a board position.
 *
 * <p>{@code BoardView} deliberately holds <b>no authoritative game state</b>. It
 * renders from {@link TicTacToeGame#getBoardState(int)} and reports candidate
 * cell selections upward through {@link OnMoveListener}. {@code MainActivity}
 * remains the single owner of turn order, game-over gating, winner evaluation,
 * status text, and sound.
 *
 * <p>Touch handling ({@code onTouchEvent}) is implemented in a later task (4.4).
 */
public class BoardView extends View {

    /** Grid line thickness in pixels. */
    private static final int GRID_STROKE = 6;

    /**
     * Fraction of the smaller cell dimension reserved as padding on each side
     * when scaling a mark to fit inside its cell.
     */
    private static final double INSET_FRACTION = 0.1;

    /** Paint used to draw the grid lines. */
    private Paint mGridPaint;

    /**
     * Human mark ('X') image at its natural decoded size. Retained so the
     * drawing bitmap can be re-scaled from the source on every size change.
     */
    private Bitmap mHumanNaturalBitmap;

    /**
     * Computer mark ('O') image at its natural decoded size. Retained so the
     * drawing bitmap can be re-scaled from the source on every size change.
     */
    private Bitmap mComputerNaturalBitmap;

    /** Human mark ('X') image scaled to the current cell size; drawn in onDraw. */
    private Bitmap mHumanBitmap;

    /** Computer mark ('O') image scaled to the current cell size; drawn in onDraw. */
    private Bitmap mComputerBitmap;

    /** Cell width in pixels (view width / 3); computed in onSizeChanged. */
    private int mCellWidth;

    /** Cell height in pixels (view height / 3); computed in onSizeChanged. */
    private int mCellHeight;

    /** Non-owning reference to the game engine that holds authoritative state. */
    private TicTacToeGame mGame;

    /** Callback used to report a candidate cell selection to MainActivity. */
    private OnMoveListener mListener;

    /**
     * Programmatic constructor.
     *
     * @param context the hosting context
     */
    public BoardView(Context context) {
        super(context);
        initialize(context);
    }

    /**
     * XML-inflation constructor.
     *
     * @param context the hosting context
     * @param attrs   the attributes from the XML layout
     */
    public BoardView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialize(context);
    }

    /**
     * Shared initialization for both constructors: builds the grid paint and
     * decodes the mark bitmaps at natural size. The natural-size bitmaps are
     * kept as the scaling source and re-scaled into the drawing bitmaps in
     * {@code onSizeChanged}.
     *
     * @param context the hosting context
     */
    private void initialize(Context context) {
        mGridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mGridPaint.setColor(Color.LTGRAY);
        mGridPaint.setStrokeWidth(GRID_STROKE);

        mHumanNaturalBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.human_mark);
        mComputerNaturalBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.computer_mark);
    }

    /**
     * Recomputes cell dimensions and re-scales the mark bitmaps whenever the
     * view size changes (e.g. layout, rotation, recreation), keeping touch
     * mapping and drawing consistent with the current size.
     *
     * <p>Cell dimensions are {@code w / 3} and {@code h / 3}. The drawing
     * bitmaps are always scaled from the retained natural-size sources — never
     * from an already-scaled bitmap — so repeated size changes do not compound
     * scaling loss. Zero/negative sizes and null source bitmaps are guarded.
     *
     * @param w    the new view width in pixels
     * @param h    the new view height in pixels
     * @param oldw the previous view width in pixels
     * @param oldh the previous view height in pixels
     */
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        if (w <= 0 || h <= 0) {
            return;
        }

        mCellWidth = w / 3;
        mCellHeight = h / 3;

        int inset = (int) (Math.min(mCellWidth, mCellHeight) * INSET_FRACTION);

        mHumanBitmap = scaleMark(mHumanNaturalBitmap, inset);
        mComputerBitmap = scaleMark(mComputerNaturalBitmap, inset);
    }

    /**
     * Produces a copy of {@code natural} scaled to fit inside the current cell
     * (minus {@code inset} on each side), preserving aspect ratio. Returns
     * {@code null} when the source is null or the computed size is empty.
     *
     * @param natural the natural-size source bitmap (may be {@code null})
     * @param inset   padding applied inside the cell on each side
     * @return the scaled bitmap, or {@code null} when it cannot be produced
     */
    private Bitmap scaleMark(Bitmap natural, int inset) {
        if (natural == null) {
            return null;
        }

        int[] size = BoardGeometry.scaledSize(
                natural.getWidth(), natural.getHeight(), mCellWidth, mCellHeight, inset);

        if (size[0] <= 0 || size[1] <= 0) {
            return null;
        }

        return Bitmap.createScaledBitmap(natural, size[0], size[1], true);
    }

    /**
     * Renders the board: the 3x3 grid lines and any player marks currently
     * present in the game state. The view holds no authoritative state; it draws
     * strictly from {@link TicTacToeGame#getBoardState(int)}.
     *
     * <p>Guards:
     * <ul>
     *   <li>If cell dimensions are not yet known ({@code mCellWidth <= 0} or
     *       {@code mCellHeight <= 0}, i.e. before the first {@code onSizeChanged}),
     *       nothing is drawn.</li>
     *   <li>If no game is attached ({@code mGame == null}), only the grid is
     *       drawn.</li>
     *   <li>Each mark bitmap is null-guarded so a failed decode/scale skips that
     *       mark rather than crashing.</li>
     * </ul>
     *
     * <p>Cell {@code i} occupies {@code col = i % 3}, {@code row = i / 3}, with
     * its top-left at {@code (col*mCellWidth, row*mCellHeight)}. Each mark bitmap
     * is centered within its cell. Open cells draw nothing.
     *
     * @param canvas the canvas to draw on
     */
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Not laid out yet: avoid drawing with zero-size cells.
        if (mCellWidth <= 0 || mCellHeight <= 0) {
            return;
        }

        int boardWidth = 3 * mCellWidth;
        int boardHeight = 3 * mCellHeight;

        // Two vertical grid lines split the width into three columns.
        canvas.drawLine(mCellWidth, 0, mCellWidth, boardHeight, mGridPaint);
        canvas.drawLine(2 * mCellWidth, 0, 2 * mCellWidth, boardHeight, mGridPaint);

        // Two horizontal grid lines split the height into three rows.
        canvas.drawLine(0, mCellHeight, boardWidth, mCellHeight, mGridPaint);
        canvas.drawLine(0, 2 * mCellHeight, boardWidth, 2 * mCellHeight, mGridPaint);

        // Without a game there is no state to render marks from.
        if (mGame == null) {
            return;
        }

        for (int i = 0; i < 9; i++) {
            BoardGeometry.Mark mark = BoardGeometry.markFor(mGame.getBoardState(i));

            Bitmap bitmap;
            if (mark == BoardGeometry.Mark.HUMAN) {
                bitmap = mHumanBitmap;
            } else if (mark == BoardGeometry.Mark.COMPUTER) {
                bitmap = mComputerBitmap;
            } else {
                // Open cell: draw nothing.
                continue;
            }

            // Null-guard against a failed decode/scale.
            if (bitmap == null) {
                continue;
            }

            int col = i % 3;
            int row = i / 3;
            int cellLeft = col * mCellWidth;
            int cellTop = row * mCellHeight;

            // Center the mark within its cell.
            int left = cellLeft + (mCellWidth - bitmap.getWidth()) / 2;
            int top = cellTop + (mCellHeight - bitmap.getHeight()) / 2;

            canvas.drawBitmap(bitmap, left, top, null);
        }
    }

    /**
     * Maps a touch to a candidate board cell and reports it upward.
     *
     * <p>On {@link MotionEvent#ACTION_UP}, the touch coordinates are resolved to
     * a position in {@code 0..8} via
     * {@link BoardGeometry#mapTouch(int, int, int, int, int)}. When the touch
     * lands on a valid cell ({@code position >= 0}) and a listener is
     * registered, {@link OnMoveListener#onMoveSelected(int)} is invoked and the
     * event is consumed.
     *
     * <p>The view reports only the candidate cell. It deliberately performs no
     * game-over, computer-turn, or occupancy gating — {@code MainActivity} owns
     * those decisions (Requirements 6.1, 6.2, 6.3).
     *
     * <p>{@code ACTION_DOWN} returns {@code true} so the gesture keeps being
     * delivered through to {@code ACTION_UP}; other events delegate to
     * {@code super}.
     *
     * @param event the motion event
     * @return {@code true} when the event is consumed, otherwise the result of
     *         {@code super.onTouchEvent(event)}
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                // Keep receiving the gesture so ACTION_UP is delivered.
                return true;

            case MotionEvent.ACTION_UP:
                int position = BoardGeometry.mapTouch(
                        (int) event.getX(), (int) event.getY(),
                        mCellWidth, mCellHeight, GRID_STROKE);

                if (position >= 0 && mListener != null) {
                    mListener.onMoveSelected(position);
                    return true;
                }
                return super.onTouchEvent(event);

            default:
                return super.onTouchEvent(event);
        }
    }

    /**
     * Associates a {@link TicTacToeGame} instance with this view. The view only
     * reads state from the game and never mutates it.
     *
     * @param game the game engine (non-owning reference)
     */
    public void setGame(TicTacToeGame game) {
        mGame = game;
    }

    /**
     * Registers the listener notified when a touch resolves to a candidate cell.
     *
     * @param listener the move listener, typically {@code MainActivity}
     */
    public void setOnMoveListener(OnMoveListener listener) {
        mListener = listener;
    }

    /**
     * Callback interface used to report a candidate cell selection upward.
     */
    public interface OnMoveListener {
        /**
         * Invoked when a touch resolves to a board cell.
         *
         * @param position the row-major board position in {@code 0..8}
         */
        void onMoveSelected(int position);
    }
}
