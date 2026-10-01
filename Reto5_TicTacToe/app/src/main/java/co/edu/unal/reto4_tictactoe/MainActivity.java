package co.edu.unal.reto4_tictactoe;

import android.content.DialogInterface;
import android.media.MediaPlayer;
import android.os.Bundle;
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

    // Represents the internal state of the game
    private TicTacToeGame mGame;

    // Custom view that draws the board and reports moves
    private BoardView mBoardView;

    // Display text for game status
    private TextView mInfoTextView;

    // Flag indicating if the game is over
    private boolean mGameOver;

    // Flag indicating the computer's move is being applied
    private boolean mComputerTurn;

    // Sound effects for moves
    private MediaPlayer mHumanMoveSound;
    private MediaPlayer mComputerMoveSound;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        mInfoTextView = findViewById(R.id.information);

        mGame = new TicTacToeGame();

        mBoardView = findViewById(R.id.board_view);
        mBoardView.setGame(mGame);
        mBoardView.setOnMoveListener(this::onMoveSelected);

        startNewGame();
    }

    // Relocated turn logic: BoardView only reports a candidate position;
    // MainActivity decides whether it is applied and drives the game flow.
    //
    // The pure turn-orchestration sequence (gate, human move + sound, optional
    // computer move + sound, winner re-check) is delegated to TurnOrchestrator so
    // there is a single, JVM-testable source of truth (see Property 3). This method
    // keeps only the Android-side effects: redrawing the board, flipping the
    // computer-turn flag around the delegated call, and updating the status text.
    private void onMoveSelected(int position) {
        TurnOrchestrator.Result result = TurnOrchestrator.onMoveSelected(
                TurnOrchestrator.adapt(mGame),
                position,
                mGameOver,
                mComputerTurn,
                sound -> {
                    if (sound == TurnOrchestrator.Sound.HUMAN) {
                        playSound(mHumanMoveSound);
                        // The game continues into the computer turn; reflect it in the status.
                        mComputerTurn = true;
                        mInfoTextView.setText(R.string.turn_computer);
                    } else {
                        playSound(mComputerMoveSound);
                    }
                    mBoardView.invalidate();
                });

        // The computer turn (if any) is fully applied synchronously here.
        mComputerTurn = false;

        // A gated-out tap leaves everything unchanged.
        if (!result.wasApplied()) {
            return;
        }

        int winner = result.winner();

        // Map the winner code to a status via the pure GameStatus helper (single source of
        // truth, JVM-testable), then reflect it in the info text and the game-over flag.
        mInfoTextView.setText(statusTextResId(GameStatus.statusFor(winner)));
        mGameOver = GameStatus.isGameOver(winner);
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
        if (mHumanMoveSound != null) {
            mHumanMoveSound.release();
            mHumanMoveSound = null;
        }
        if (mComputerMoveSound != null) {
            mComputerMoveSound.release();
            mComputerMoveSound = null;
        }
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
        mInfoTextView.setText(statusTextResId(GameStatus.newGameStatus()));

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
        } else if (id == R.id.quit) {
            showQuitDialog();
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
