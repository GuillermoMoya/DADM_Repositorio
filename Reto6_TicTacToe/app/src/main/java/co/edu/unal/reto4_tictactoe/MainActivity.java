package co.edu.unal.reto4_tictactoe;

import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity {

    // Durable scores live in this SharedPreferences file (MODE_PRIVATE),
    // separate from the transient per-game state kept in the savedInstanceState
    // Bundle. Difficulty (task 6) shares the same file.
    private static final String PREFS_NAME = "ttt_prefs";

    // SharedPreferences keys for the three persisted score counters.
    private static final String PREF_HUMAN_WINS = "humanWins";
    private static final String PREF_COMPUTER_WINS = "computerWins";
    private static final String PREF_TIES = "ties";

    // SharedPreferences key for the persisted difficulty, stored as the dialog
    // index (0 Easy, 1 Harder, 2 Expert) in the same "ttt_prefs" file.
    private static final String PREF_DIFFICULTY = "difficulty";

    // Bundle keys for the transient per-game state saved across Activity
    // recreation (rotation) in onSaveInstanceState and restored in onCreate.
    // These are distinct from the durable SharedPreferences keys above: the
    // Bundle holds the live snapshot of the in-progress game, SharedPreferences
    // holds durable scores and difficulty.
    private static final String KEY_BOARD = "board";
    private static final String KEY_GAME_OVER = "gameOver";
    private static final String KEY_STATUS_ORDINAL = "statusOrdinal";
    private static final String KEY_HUMAN_WINS = "humanWins";
    private static final String KEY_COMPUTER_WINS = "computerWins";
    private static final String KEY_TIES = "ties";
    private static final String KEY_GO_FIRST = "goFirst";
    private static final String KEY_COMPUTER_TURN = "computerTurn";
    private static final String KEY_PENDING_COMPUTER = "pendingComputer";

    // Delay (ms) by which the computer's reply is deferred so the "Activity
    // recreated between the human move and the computer's reply" scenario is
    // reproducible (Extra 2). Human moves stay immediate; only the computer
    // move is scheduled DELAY_MS later.
    private static final long DELAY_MS = 1000;

    // Represents the internal state of the game
    private TicTacToeGame mGame;

    // Custom view that draws the board and reports moves
    private BoardView mBoardView;

    // Display text for game status
    private TextView mInfoTextView;

    // Display text for the running scoreboard (shared id in portrait and landscape)
    private TextView mScoreTextView;

    // Running score counters. Incremented in the winner-handling path and shown
    // through displayScores(); zeroed by resetScores().
    private int mHumanWins;
    private int mComputerWins;
    private int mTies;

    // Flag indicating if the game is over
    private boolean mGameOver;

    // Flag indicating the computer's move is being applied
    private boolean mComputerTurn;

    // The computer still owes a move for the current turn. Saved/restored so a
    // recreation mid-turn can finish the owed move exactly once. The actual
    // scheduling/reschedule of the deferred computer move is wired in task 9;
    // task 8 only persists and restores this flag.
    private boolean pendingComputer;

    // Who takes the first move; true = human first. The app has no UI to change
    // this, so the field only preserves the who-goes-first state across
    // recreation. It is the single who-goes-first representation.
    private boolean mGoFirst = true;

    // The currently displayed GameStatus. Kept in sync wherever the info text is
    // set so onSaveInstanceState can persist its ordinal (semantic, locale-safe)
    // instead of a frozen localized string.
    private GameStatus mStatus;

    // Sound effects for moves
    private MediaPlayer mHumanMoveSound;
    private MediaPlayer mComputerMoveSound;

    // Handler bound to the main Looper that schedules the deferred computer
    // move. Created once in onCreate.
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    // Named Runnable field (not an anonymous fire-and-forget capture) that
    // performs the deferred computer half-turn and the follow-up UI updates on
    // the Activity that scheduled it. Kept as a field so onPause can cancel it
    // via removeCallbacks before the Activity is destroyed.
    private final Runnable mComputerMoveRunnable = new Runnable() {
        @Override
        public void run() {
            // Single-shot guard: capture the at-start-of-run condition and clear
            // pendingComputer FIRST so a concurrent save can't reschedule it.
            boolean wasPending = pendingComputer;
            pendingComputer = false;
            if (!wasPending || mGameOver) {
                return;
            }

            TurnOrchestrator.Result result = TurnOrchestrator.playComputerMove(
                    TurnOrchestrator.adapt(mGame),
                    sound -> playSound(mComputerMoveSound));
            mComputerTurn = false;

            if (result.wasApplied()) {
                int winner = result.winner();
                mStatus = GameStatus.statusFor(winner);
                mInfoTextView.setText(statusTextResId(mStatus));
                mGameOver = GameStatus.isGameOver(winner);
                applyWinnerToScores(winner);
                displayScores();
            }

            mBoardView.invalidate();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        mInfoTextView = findViewById(R.id.information);
        mScoreTextView = findViewById(R.id.scores);

        mGame = new TicTacToeGame();

        mBoardView = findViewById(R.id.board_view);
        mBoardView.setGame(mGame);
        mBoardView.setOnMoveListener(this::onMoveSelected);

        // Load the durable scores from SharedPreferences (default 0 for any
        // absent key) BEFORE the first render, so the initial scoreboard shows
        // the persisted totals rather than zeroes. On recreation the Bundle
        // snapshot overwrites these below (both are written from the same
        // fields, so they agree).
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        mHumanWins = prefs.getInt(PREF_HUMAN_WINS, 0);
        mComputerWins = prefs.getInt(PREF_COMPUTER_WINS, 0);
        mTies = prefs.getInt(PREF_TIES, 0);
        displayScores();

        // Apply the persisted difficulty right after constructing the model and
        // before any new/restored game is started, so a relaunch or recreation
        // resumes at the last chosen level rather than resetting to Expert. The
        // stored value is the dialog index; the Expert index is the first-run
        // default (matching TicTacToeGame's own default level). Applied in BOTH
        // branches below (this single statement runs before either path).
        int difficultyIndex = prefs.getInt(
                PREF_DIFFICULTY, Difficulty.indexFor(TicTacToeGame.DifficultyLevel.Expert));
        mGame.setDifficultyLevel(Difficulty.difficultyFor(difficultyIndex));

        if (savedInstanceState == null) {
            // Fresh start: human goes first and a new game is begun.
            mGoFirst = true;
            startNewGame();
        } else {
            restoreState(savedInstanceState);
        }
    }

    // Restores the transient per-game state previously written by
    // onSaveInstanceState. Validates the Bundle first; if the board or the
    // status ordinal is invalid, falls back to a clean fresh game rather than
    // applying a half-restored state.
    private void restoreState(Bundle savedInstanceState) {
        // 0. Validate first (invalid-restore fallback). The GameStatus enum has
        // 6 values, so the ordinal must be in 0..5.
        char[] board = savedInstanceState.getCharArray(KEY_BOARD);
        int ordinal = savedInstanceState.getInt(KEY_STATUS_ORDINAL, -1);
        if (board == null || board.length != 9 || ordinal < 0 || ordinal > 5) {
            mGoFirst = true;
            startNewGame();
            return;
        }

        // 1. Restore the authoritative board (array validated non-null, length 9).
        mGame.setBoardState(board);

        // 2. Restore the booleans (mGoFirst defaults true, the others false).
        mGameOver = savedInstanceState.getBoolean(KEY_GAME_OVER, false);
        mComputerTurn = savedInstanceState.getBoolean(KEY_COMPUTER_TURN, false);
        pendingComputer = savedInstanceState.getBoolean(KEY_PENDING_COMPUTER, false);
        mGoFirst = savedInstanceState.getBoolean(KEY_GO_FIRST, true);

        // 3. Restore the live score snapshot (default 0) and refresh the UI. The
        // Bundle values take precedence over the SharedPreferences load above.
        mHumanWins = savedInstanceState.getInt(KEY_HUMAN_WINS, 0);
        mComputerWins = savedInstanceState.getInt(KEY_COMPUTER_WINS, 0);
        mTies = savedInstanceState.getInt(KEY_TIES, 0);
        displayScores();

        // 4. Re-resolve the status from its ordinal (validated in 0..5) and
        // render it in the current locale.
        GameStatus status = GameStatus.values()[ordinal];
        mStatus = status;
        mInfoTextView.setText(statusTextResId(status));

        // 5. Repaint the board from the restored game state.
        mBoardView.invalidate();

        // 6. If the computer still owes a move for the current turn and the game
        // is not over, reschedule the deferred computer move on THIS (new)
        // Activity's Handler. The elapsed delay is not persisted; the new
        // Activity simply reschedules the full DELAY_MS. All UI updates and the
        // sound cue belong to the new Activity's instance.
        if (pendingComputer && !mGameOver) {
            mHandler.postDelayed(mComputerMoveRunnable, DELAY_MS);
        }
    }

    // Saves the transient per-game state so it survives Activity recreation
    // (rotation). Durable scores and difficulty are persisted separately in
    // onStop via SharedPreferences; this Bundle holds the live in-progress game.
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putCharArray(KEY_BOARD, mGame.getBoardState());
        outState.putBoolean(KEY_GAME_OVER, mGameOver);
        // Persist the status as its enum ordinal (semantic, locale-safe) rather
        // than a frozen localized string.
        outState.putInt(KEY_STATUS_ORDINAL, mStatus.ordinal());
        outState.putInt(KEY_HUMAN_WINS, mHumanWins);
        outState.putInt(KEY_COMPUTER_WINS, mComputerWins);
        outState.putInt(KEY_TIES, mTies);
        outState.putBoolean(KEY_GO_FIRST, mGoFirst);
        outState.putBoolean(KEY_COMPUTER_TURN, mComputerTurn);
        outState.putBoolean(KEY_PENDING_COMPUTER, pendingComputer);
    }

    // Relocated turn logic: BoardView only reports a candidate position;
    // MainActivity decides whether it is applied and drives the game flow.
    //
    // The turn is split into two pure half-turns (TurnOrchestrator.applyHumanMove
    // and TurnOrchestrator.playComputerMove), keeping TurnOrchestrator the single
    // source of truth for turn flow. The human move is applied immediately here;
    // the computer's reply is deferred by DELAY_MS and runs in
    // mComputerMoveRunnable (Extra 2). This method keeps only the Android-side
    // effects: gating input, redrawing the board, updating the status text, the
    // computer-turn flag, and scheduling the deferred computer move.
    private void onMoveSelected(int position) {
        // Input gate: reject human taps while the game is over or during the
        // computer's turn (including the ~1 s deferred-move window).
        if (mGameOver || mComputerTurn) {
            return;
        }

        // Apply ONLY the human move immediately (never the computer move).
        TurnOrchestrator.Result humanResult = TurnOrchestrator.applyHumanMove(
                TurnOrchestrator.adapt(mGame),
                position,
                sound -> playSound(mHumanMoveSound));
        mBoardView.invalidate();

        // A gated-out / occupied cell leaves everything unchanged.
        if (!humanResult.wasApplied()) {
            return;
        }

        int winner = humanResult.winner();
        mStatus = GameStatus.statusFor(winner);
        mInfoTextView.setText(statusTextResId(mStatus));
        mGameOver = GameStatus.isGameOver(winner);

        if (mGameOver) {
            // The human move ended the game: record the score and return WITHOUT
            // scheduling a computer reply (no pendingComputer, no postDelayed).
            applyWinnerToScores(winner);
            displayScores();
            return;
        }

        // Game continues -> the computer owes a move. Flip the turn, mark the
        // pending flag (so a recreation mid-delay can finish it exactly once),
        // show the computer's-turn status, and schedule the deferred move.
        mComputerTurn = true;
        pendingComputer = true;
        mStatus = GameStatus.COMPUTER_TURN;
        mInfoTextView.setText(R.string.turn_computer);
        mHandler.postDelayed(mComputerMoveRunnable, DELAY_MS);
    }

    // Applies a winner code to the running score counters. Winner codes:
    // 2 -> human, 3 -> computer, 1 -> tie; 0 (game continues) does nothing.
    // Single place that maps a winner to a score so the human and computer
    // half-turns stay consistent. Callers call displayScores() afterward.
    private void applyWinnerToScores(int winner) {
        if (winner == 2) {
            mHumanWins++;
        } else if (winner == 3) {
            mComputerWins++;
        } else if (winner == 1) {
            mTies++;
        }
    }

    // Single score-UI update point. Formats the three counters into the shared
    // score TextView using the positional score_format string (human, computer,
    // ties). Null-guarded so it is safe before the view is resolved.
    private void displayScores() {
        if (mScoreTextView != null) {
            mScoreTextView.setText(
                    getString(R.string.score_format, mHumanWins, mComputerWins, mTies));
        }
    }

    // Zeroes the three score counters, refreshes the UI, and persists the reset
    // immediately so it survives app restarts without waiting for onStop.
    private void resetScores() {
        mHumanWins = 0;
        mComputerWins = 0;
        mTies = 0;
        displayScores();

        // Persist the zeroed scores immediately rather than waiting for onStop,
        // so a reset survives even an abrupt termination. Only the three score
        // keys are touched; the persisted difficulty (task 6) is left unchanged.
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putInt(PREF_HUMAN_WINS, mHumanWins)
                .putInt(PREF_COMPUTER_WINS, mComputerWins)
                .putInt(PREF_TIES, mTies)
                .apply();
    }

    // Translates a pure GameStatus value to the matching R.string resource id. The
    // winner -> status decision lives in GameStatus (see Requirements 8.1, 8.3); this
    // method is the only Android-side piece and simply picks the string resource.
    private static int statusTextResId(GameStatus status) {
        switch (status) {
            case NEW_GAME_HUMAN_FIRST:
                return R.string.first_human;
            case HUMAN_TURN:
                return R.string.turn_human;
            case COMPUTER_TURN:
                return R.string.turn_computer;
            case TIE:
                return R.string.result_tie;
            case HUMAN_WINS:
                return R.string.result_human_wins;
            case COMPUTER_WINS:
                return R.string.result_computer_wins;
            default:
                throw new IllegalArgumentException("Unknown status: " + status);
        }
    }

    // Create the MediaPlayer instances for the move sounds when the activity
    // becomes active. Recreated on every resume so sounds play again after
    // the activity has been paused (which releases them).
    @Override
    protected void onResume() {
        super.onResume();
        mHumanMoveSound = MediaPlayer.create(this, R.raw.human_move);
        mComputerMoveSound = MediaPlayer.create(this, R.raw.computer_move);
    }

    // Release the MediaPlayer instances and free their resources when the
    // activity is paused. Fields are cleared so no released instance is retained.
    @Override
    protected void onPause() {
        super.onPause();

        // Cancel any still-pending deferred computer move before the Activity is
        // destroyed, so a recreated Activity's old callback can never fire
        // against this (dead) instance. A recreated Activity reschedules its own.
        mHandler.removeCallbacks(mComputerMoveRunnable);

        if (mHumanMoveSound != null) {
            mHumanMoveSound.release();
            mHumanMoveSound = null;
        }
        if (mComputerMoveSound != null) {
            mComputerMoveSound.release();
            mComputerMoveSound = null;
        }
    }

    // Persist the durable scores when the activity is no longer visible. onStop
    // (rather than onPause) owns persistence: onPause stays focused on releasing
    // the MediaPlayers, while onStop is the conventional place to flush durable
    // state before the activity may be destroyed. Scores also ride in the
    // savedInstanceState Bundle for rotation, so using onStop loses nothing.
    @Override
    protected void onStop() {
        super.onStop();
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putInt(PREF_HUMAN_WINS, mHumanWins)
                .putInt(PREF_COMPUTER_WINS, mComputerWins)
                .putInt(PREF_TIES, mTies)
                .putInt(PREF_DIFFICULTY, Difficulty.indexFor(mGame.getDifficultyLevel()))
                .apply();
    }

    // Playback helper. Null-guarded so it is safe to call before the players
    // are created or after they are released. The human and computer move
    // sounds fire back-to-back within a single synchronous turn, so we always
    // rewind to the start before starting playback. This guarantees the
    // computer clip reliably restarts even if the previous play is still
    // settling and isPlaying() has not yet reported true.
    private void playSound(MediaPlayer mp) {
        if (mp != null) {
            mp.seekTo(0);
            mp.start();
        }
    }

    private void startNewGame() {
        mGame.clearBoard();
        mGameOver = false;
        mComputerTurn = false;

        // Human goes first (status decided by the pure GameStatus helper).
        mStatus = GameStatus.newGameStatus();
        mInfoTextView.setText(statusTextResId(mStatus));

        // Redraw the cleared board so all nine cells display no mark.
        mBoardView.invalidate();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.options_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.new_game) {
            startNewGame();
            return true;
        } else if (id == R.id.ai_difficulty) {
            showDifficultyDialog();
            return true;
        } else if (id == R.id.reset_scores) {
            resetScores();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void showDifficultyDialog() {
        CharSequence[] levels = {
                getString(R.string.difficulty_easy),
                getString(R.string.difficulty_harder),
                getString(R.string.difficulty_expert)
        };

        // Pre-select the current level via the pure index<->level mapping (Requirement 8.6).
        int selectedIndex = Difficulty.indexFor(mGame.getDifficultyLevel());

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.difficulty_choose);
        builder.setSingleChoiceItems(levels, selectedIndex, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int item) {
                // The index -> DifficultyLevel decision lives in the pure Difficulty helper.
                mGame.setDifficultyLevel(Difficulty.difficultyFor(item));

                // Persist the chosen difficulty immediately (encoded via the pure
                // Difficulty helper) so it survives relaunch even without onStop.
                // Only the difficulty key is touched; scores are left unchanged.
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                        .edit()
                        .putInt(PREF_DIFFICULTY, Difficulty.indexFor(mGame.getDifficultyLevel()))
                        .apply();

                Toast.makeText(getApplicationContext(), levels[item], Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            }
        });

        builder.setNegativeButton(R.string.cancel, null);
        builder.create().show();
    }

    private void showQuitDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.quit_confirm_message)
               .setCancelable(false)
               .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                   public void onClick(DialogInterface dialog, int id) {
                       finish();
                   }
               })
               .setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                   public void onClick(DialogInterface dialog, int id) {
                       dialog.dismiss();
                   }
               });

        AlertDialog alert = builder.create();
        alert.show();
    }
}
