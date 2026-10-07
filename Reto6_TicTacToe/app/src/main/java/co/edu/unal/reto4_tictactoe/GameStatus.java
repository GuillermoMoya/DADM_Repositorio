package co.edu.unal.reto4_tictactoe;

/**
 * Pure, Android-free mapping of game outcomes to the semantic status a player sees.
 *
 * <p>The winner→status decision and the "new game" status previously lived inline in
 * {@code MainActivity} as {@code R.string} assignments, which cannot run on the JVM. This helper
 * extracts that decision so it is unit-testable without a device; {@code MainActivity} delegates
 * to it and translates each {@link GameStatus} value to the matching {@code R.string} resource
 * (see design "Unit / example tests (JVM)" and Requirements 8.1, 8.3).
 */
public enum GameStatus {

    /** A new game just started; the human moves first. Maps to {@code R.string.first_human}. */
    NEW_GAME_HUMAN_FIRST,

    /** The game continues and it is the human's turn. Maps to {@code R.string.turn_human}. */
    HUMAN_TURN,

    /** The game continues and it is the computer's turn. Maps to {@code R.string.turn_computer}. */
    COMPUTER_TURN,

    /** The game ended in a tie. Maps to {@code R.string.result_tie}. */
    TIE,

    /** The human won. Maps to {@code R.string.result_human_wins}. */
    HUMAN_WINS,

    /** The computer won. Maps to {@code R.string.result_computer_wins}. */
    COMPUTER_WINS;

    /**
     * Maps a {@link TicTacToeGame#checkForWinner()} code to the status the information text
     * should show once a move has been evaluated.
     *
     * @param winner the winner code: {@code 0} continue (human's turn), {@code 1} tie,
     *               {@code 2} human win, {@code 3} computer win
     * @return the matching {@link GameStatus}
     * @throws IllegalArgumentException if {@code winner} is outside {@code 0..3}
     */
    public static GameStatus statusFor(int winner) {
        switch (winner) {
            case 0:
                return HUMAN_TURN;
            case 1:
                return TIE;
            case 2:
                return HUMAN_WINS;
            case 3:
                return COMPUTER_WINS;
            default:
                throw new IllegalArgumentException("Unknown winner code: " + winner);
        }
    }

    /**
     * @return the status shown when a new game starts (the human moves first)
     */
    public static GameStatus newGameStatus() {
        return NEW_GAME_HUMAN_FIRST;
    }

    /**
     * @param winner a {@link TicTacToeGame#checkForWinner()} code
     * @return {@code true} iff {@code winner} is non-zero, i.e. the game is over (tie or a win)
     */
    public static boolean isGameOver(int winner) {
        return winner != 0;
    }
}
