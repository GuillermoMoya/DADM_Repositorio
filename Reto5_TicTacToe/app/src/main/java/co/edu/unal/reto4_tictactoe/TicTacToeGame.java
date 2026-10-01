package co.edu.unal.reto4_tictactoe;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class TicTacToeGame {

    public enum DifficultyLevel {
        Easy, Harder, Expert
    }

    public static final int BOARD_SIZE = 9;

    public static final char HUMAN_PLAYER = 'X';
    public static final char COMPUTER_PLAYER = 'O';
    public static final char OPEN_SPOT = ' ';

    private final char[] mBoard = new char[BOARD_SIZE];
    private final Random mRand = new Random();
    private DifficultyLevel mDifficultyLevel = DifficultyLevel.Expert;

    public TicTacToeGame() {
        clearBoard();
    }

    public void clearBoard() {
        for (int i = 0; i < BOARD_SIZE; i++) {
            mBoard[i] = OPEN_SPOT;
        }
    }

    public boolean setMove(char player, int location) {
        if (location >= 0 && location < BOARD_SIZE && mBoard[location] == OPEN_SPOT) {
            mBoard[location] = player;
            return true;
        }
        return false;
    }

    public char getBoardState(int location) {
        if (location >= 0 && location < BOARD_SIZE) {
            return mBoard[location];
        }
        return OPEN_SPOT;
    }

    public DifficultyLevel getDifficultyLevel() {
        return mDifficultyLevel;
    }

    public void setDifficultyLevel(DifficultyLevel difficultyLevel) {
        mDifficultyLevel = difficultyLevel;
    }

    /**
     * Check for a winner.
     * @return 0 if no winner or tie yet (game continues)
     *         1 if it's a tie
     *         2 if HUMAN_PLAYER ('X') won
     *         3 if COMPUTER_PLAYER ('O') won
     */
    public int checkForWinner() {
        // Horizontal wins
        for (int i = 0; i <= 6; i += 3) {
            if (mBoard[i] != OPEN_SPOT &&
                mBoard[i] == mBoard[i + 1] &&
                mBoard[i + 1] == mBoard[i + 2]) {
                return mBoard[i] == HUMAN_PLAYER ? 2 : 3;
            }
        }

        // Vertical wins
        for (int i = 0; i <= 2; i++) {
            if (mBoard[i] != OPEN_SPOT &&
                mBoard[i] == mBoard[i + 3] &&
                mBoard[i + 3] == mBoard[i + 6]) {
                return mBoard[i] == HUMAN_PLAYER ? 2 : 3;
            }
        }

        // Diagonal wins
        if (mBoard[0] != OPEN_SPOT &&
            mBoard[0] == mBoard[4] &&
            mBoard[4] == mBoard[8]) {
            return mBoard[0] == HUMAN_PLAYER ? 2 : 3;
        }
        if (mBoard[2] != OPEN_SPOT &&
            mBoard[2] == mBoard[4] &&
            mBoard[4] == mBoard[6]) {
            return mBoard[2] == HUMAN_PLAYER ? 2 : 3;
        }

        // Check for open spots
        for (int i = 0; i < BOARD_SIZE; i++) {
            if (mBoard[i] == OPEN_SPOT) {
                return 0; // Game continues
            }
        }

        return 1; // Tie
    }

    /**
     * Finds a random valid move.
     * @return Board index (0-8) or -1 if no move available.
     */
    public int getRandomMove() {
        List<Integer> openSpots = new ArrayList<>();
        for (int i = 0; i < BOARD_SIZE; i++) {
            if (mBoard[i] == OPEN_SPOT) {
                openSpots.add(i);
            }
        }
        if (!openSpots.isEmpty()) {
            return openSpots.get(mRand.nextInt(openSpots.size()));
        }
        return -1;
    }

    /**
     * Checks if the computer can win in 1 move.
     * @return Winning board index (0-8) or -1 if none.
     */
    public int getWinningMove() {
        for (int i = 0; i < BOARD_SIZE; i++) {
            if (mBoard[i] == OPEN_SPOT) {
                mBoard[i] = COMPUTER_PLAYER;
                if (checkForWinner() == 3) { // Computer wins
                    mBoard[i] = OPEN_SPOT; // Undo move
                    return i;
                }
                mBoard[i] = OPEN_SPOT; // Undo move
            }
        }
        return -1;
    }

    /**
     * Checks if the human can win on their next move and returns the blocking move index.
     * @return Blocking board index (0-8) or -1 if none.
     */
    public int getBlockingMove() {
        for (int i = 0; i < BOARD_SIZE; i++) {
            if (mBoard[i] == OPEN_SPOT) {
                mBoard[i] = HUMAN_PLAYER;
                if (checkForWinner() == 2) { // Human wins
                    mBoard[i] = OPEN_SPOT; // Undo move
                    return i;
                }
                mBoard[i] = OPEN_SPOT; // Undo move
            }
        }
        return -1;
    }

    /**
     * Calculates computer move according to the current difficulty level.
     * @return Board index (0-8) or -1 if no valid move.
     */
    public int getComputerMove() {
        int move = -1;

        if (mDifficultyLevel == DifficultyLevel.Easy) {
            move = getRandomMove();
        } else if (mDifficultyLevel == DifficultyLevel.Harder) {
            move = getWinningMove();
            if (move == -1) {
                move = getRandomMove();
            }
        } else if (mDifficultyLevel == DifficultyLevel.Expert) {
            move = getWinningMove();
            if (move == -1) {
                move = getBlockingMove();
            }
            if (move == -1) {
                move = getRandomMove();
            }
        }

        return move;
    }
}