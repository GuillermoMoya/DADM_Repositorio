package co.edu.unal.reto4_tictactoe;

import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
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

    // Buttons making up the board
    private Button[] mBoardButtons;

    // Display text for game status
    private TextView mInfoTextView;

    // Flag indicating if the game is over
    private boolean mGameOver;

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

        mBoardButtons = new Button[TicTacToeGame.BOARD_SIZE];
        mBoardButtons[0] = findViewById(R.id.one);
        mBoardButtons[1] = findViewById(R.id.two);
        mBoardButtons[2] = findViewById(R.id.three);
        mBoardButtons[3] = findViewById(R.id.four);
        mBoardButtons[4] = findViewById(R.id.five);
        mBoardButtons[5] = findViewById(R.id.six);
        mBoardButtons[6] = findViewById(R.id.seven);
        mBoardButtons[7] = findViewById(R.id.eight);
        mBoardButtons[8] = findViewById(R.id.nine);

        mInfoTextView = findViewById(R.id.information);

        mGame = new TicTacToeGame();

        startNewGame();
    }

    private void startNewGame() {
        mGame.clearBoard();
        mGameOver = false;

        // Reset all buttons
        for (int i = 0; i < mBoardButtons.length; i++) {
            mBoardButtons[i].setText("");
            mBoardButtons[i].setEnabled(true);
            mBoardButtons[i].setOnClickListener(new ButtonClickListener(i));
        }

        // Human goes first
        mInfoTextView.setText(R.string.first_human);
    }

    private void setMove(char player, int location) {
        mGame.setMove(player, location);
        mBoardButtons[location].setEnabled(false);
        mBoardButtons[location].setText(String.valueOf(player));
        if (player == TicTacToeGame.HUMAN_PLAYER) {
            mBoardButtons[location].setTextColor(Color.rgb(0, 128, 0));
        } else {
            mBoardButtons[location].setTextColor(Color.rgb(178, 34, 34));
        }
    }

    // Handles clicks on the board buttons
    private class ButtonClickListener implements View.OnClickListener {
        int location;

        public ButtonClickListener(int location) {
            this.location = location;
        }

        @Override
        public void onClick(View view) {
            if (mGameOver) {
                return;
            }

            if (mBoardButtons[location].isEnabled() && mGame.getBoardState(location) == TicTacToeGame.OPEN_SPOT) {
                setMove(TicTacToeGame.HUMAN_PLAYER, location);

                // Check if human won or tied
                int winner = mGame.checkForWinner();
                if (winner == 0) {
                    mInfoTextView.setText(R.string.turn_computer);
                    int move = mGame.getComputerMove();
                    if (move != -1) {
                        setMove(TicTacToeGame.COMPUTER_PLAYER, move);
                        winner = mGame.checkForWinner();
                    }
                }

                if (winner == 0) {
                    mInfoTextView.setText(R.string.turn_human);
                } else if (winner == 1) {
                    mInfoTextView.setText(R.string.result_tie);
                    mGameOver = true;
                } else if (winner == 2) {
                    mInfoTextView.setText(R.string.result_human_wins);
                    mGameOver = true;
                } else {
                    mInfoTextView.setText(R.string.result_computer_wins);
                    mGameOver = true;
                }
            }
        }
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

        int selectedIndex = 2;
        TicTacToeGame.DifficultyLevel currentLevel = mGame.getDifficultyLevel();
        if (currentLevel == TicTacToeGame.DifficultyLevel.Easy) {
            selectedIndex = 0;
        } else if (currentLevel == TicTacToeGame.DifficultyLevel.Harder) {
            selectedIndex = 1;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.difficulty_choose);
        builder.setSingleChoiceItems(levels, selectedIndex, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int item) {
                switch (item) {
                    case 0:
                        mGame.setDifficultyLevel(TicTacToeGame.DifficultyLevel.Easy);
                        break;
                    case 1:
                        mGame.setDifficultyLevel(TicTacToeGame.DifficultyLevel.Harder);
                        break;
                    case 2:
                        mGame.setDifficultyLevel(TicTacToeGame.DifficultyLevel.Expert);
                        break;
                }

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