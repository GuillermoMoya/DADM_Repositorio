package co.edu.unal.tictactoe;

import java.util.Random;

public class TicTacToeGame {

    // Tamaño del tablero
    public static final int BOARD_SIZE = 9;

    // Jugadores
    public static final char HUMAN_PLAYER = 'X';
    public static final char COMPUTER_PLAYER = 'O';

    // Casilla vacía
    public static final char OPEN_SPOT = ' ';

    // Tablero interno
    private final char[] board;

    private final Random random;

    // Constructor
    public TicTacToeGame() {
        board = new char[BOARD_SIZE];
        random = new Random();
        clearBoard();
    }

    // Limpia el tablero
    public void clearBoard() {
        for (int i = 0; i < BOARD_SIZE; i++) {
            board[i] = OPEN_SPOT;
        }
    }

    // Coloca una ficha en una posición
    public void setMove(char player, int location) {
        if (location >= 0 &&
                location < BOARD_SIZE &&
                board[location] == OPEN_SPOT) {

            board[location] = player;
        }
    }

    // Comprueba si hay ganador o empate
    public int checkForWinner() {

        // Combinaciones ganadoras
        int[][] winningPositions = {
                {0, 1, 2},
                {3, 4, 5},
                {6, 7, 8},
                {0, 3, 6},
                {1, 4, 7},
                {2, 5, 8},
                {0, 4, 8},
                {2, 4, 6}
        };

        // Revisar las combinaciones ganadoras
        for (int[] position : winningPositions) {

            char first = board[position[0]];

            if (first != OPEN_SPOT &&
                    first == board[position[1]] &&
                    first == board[position[2]]) {

                if (first == HUMAN_PLAYER) {
                    return 2;
                } else if (first == COMPUTER_PLAYER) {
                    return 3;
                }
            }
        }

        // Comprobar si quedan casillas vacías
        for (int i = 0; i < BOARD_SIZE; i++) {
            if (board[i] == OPEN_SPOT) {
                return 0;
            }
        }

        // No quedan casillas y nadie ganó
        return 1;
    }

    public int getComputerMove() {

        // 1. Intentar ganar
        for (int i = 0; i < BOARD_SIZE; i++) {

            if (board[i] == OPEN_SPOT) {

                board[i] = COMPUTER_PLAYER;

                if (checkForWinner() == 3) {
                    board[i] = OPEN_SPOT;
                    return i;
                }

                board[i] = OPEN_SPOT;
            }
        }

        // 2. Intentar bloquear al jugador
        for (int i = 0; i < BOARD_SIZE; i++) {

            if (board[i] == OPEN_SPOT) {

                board[i] = HUMAN_PLAYER;

                if (checkForWinner() == 2) {
                    board[i] = OPEN_SPOT;
                    return i;
                }

                board[i] = OPEN_SPOT;
            }
        }

        // 3. Elegir una posición libre al azar
        int move;

        do {
            move = random.nextInt(BOARD_SIZE);
        } while (board[move] != OPEN_SPOT);

        return move;
    }
}