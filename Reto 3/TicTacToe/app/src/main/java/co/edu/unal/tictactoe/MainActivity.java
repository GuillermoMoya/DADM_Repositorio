package co.edu.unal.tictactoe;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    // Juego
    private TicTacToeGame game;

    // Botones del tablero
    private Button[] buttons;

    // Texto de información
    private TextView information;

    private Button newGameButton;
    private boolean mGameOver;

    private int wins = 0;
    private int losses = 0;
    private int ties = 0;

    private boolean humanStarts = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        // Crear el juego
        game = new TicTacToeGame();

        mGameOver = false;

        // Obtener el TextView
        information = findViewById(R.id.information);

        newGameButton = findViewById(R.id.newGameButton);

        // Crear arreglo de botones
        buttons = new Button[9];

        buttons[0] = findViewById(R.id.button0);
        buttons[1] = findViewById(R.id.button1);
        buttons[2] = findViewById(R.id.button2);
        buttons[3] = findViewById(R.id.button3);
        buttons[4] = findViewById(R.id.button4);
        buttons[5] = findViewById(R.id.button5);
        buttons[6] = findViewById(R.id.button6);
        buttons[7] = findViewById(R.id.button7);
        buttons[8] = findViewById(R.id.button8);

        // Configurar los clics
        for (int i = 0; i < buttons.length; i++) {

            final int location = i;

            buttons[i].setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    playTurn(location);
                }
            });
        }

        newGameButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startNewGame();
            }
        });
    }

    private void playTurn(int location) {

        // No permitir movimientos si el juego terminó
        if (mGameOver) {
            return;
        }

        // Movimiento del jugador
        game.setMove(TicTacToeGame.HUMAN_PLAYER, location);

        // Mostrar X
        buttons[location].setText(
                String.valueOf(TicTacToeGame.HUMAN_PLAYER)
        );

        // Comprobar resultado
        int winner = game.checkForWinner();

        if (winner == 2) {
            wins++;
            information.setText(
                    getString(R.string.you_win) +
                            "\nVictorias: " + wins +
                            " | Derrotas: " + losses +
                            " | Empates: " + ties
            );
            mGameOver = true;
            return;
        }

        if (winner == 1) {
            ties++;
            information.setText(
                    getString(R.string.tie_game) +
                            "\nVictorias: " + wins +
                            " | Derrotas: " + losses +
                            " | Empates: " + ties
            );
            mGameOver = true;
            return;
        }

        // Movimiento del computador
        int computerMove = game.getComputerMove();

        game.setMove(
                TicTacToeGame.COMPUTER_PLAYER,
                computerMove
        );

        // Mostrar O
        buttons[computerMove].setText(
                String.valueOf(TicTacToeGame.COMPUTER_PLAYER)
        );

        // Comprobar resultado
        winner = game.checkForWinner();

        if (winner == 3) {
            losses++;

            information.setText(
                    getString(R.string.computer_win) +
                            "\nVictorias: " + wins +
                            " | Derrotas: " + losses +
                            " | Empates: " + ties
            );

            mGameOver = true;

        } else if (winner == 1) {
            ties++;

            information.setText(
                    getString(R.string.tie_game) +
                            "\nVictorias: " + wins +
                            " | Derrotas: " + losses +
                            " | Empates: " + ties
            );

            mGameOver = true;

        } else {
            information.setText(R.string.your_turn);
        }
    }

    private void startNewGame() {

        // Limpiar el tablero interno
        game.clearBoard();

        // Limpiar los botones
        for (Button button : buttons) {
            button.setText("");
        }

        // Reactivar la partida
        mGameOver = false;

        // Alternar quién comienza
        humanStarts = !humanStarts;

        if (humanStarts) {

            information.setText(R.string.your_turn);

        } else {

            information.setText("El computador comienza");

            int computerMove = game.getComputerMove();

            game.setMove(
                    TicTacToeGame.COMPUTER_PLAYER,
                    computerMove
            );

            buttons[computerMove].setText(
                    String.valueOf(TicTacToeGame.COMPUTER_PLAYER)
            );
        }
    }
}