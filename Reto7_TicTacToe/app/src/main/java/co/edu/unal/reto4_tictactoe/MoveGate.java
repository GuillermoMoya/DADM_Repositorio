package co.edu.unal.reto4_tictactoe;

/**
 * Pure, Android-free helper that decides whether a human move should be applied.
 *
 * <p>Extracted from {@code MainActivity} so the move-gating rule is JVM-testable without a
 * device (see design "Extract-for-testability" and Property 2). {@code MainActivity} delegates
 * its occupancy/flow gating to this helper.
 */
public final class MoveGate {

    private MoveGate() {
        // Utility class; not instantiable.
    }

    /**
     * Decides whether a human move on the target cell should be applied.
     *
     * @param state       the current state of the target cell (e.g. {@link TicTacToeGame#OPEN_SPOT})
     * @param gameOver    whether the game has already ended
     * @param computerTurn whether it is currently the computer's turn
     * @return {@code true} iff the cell is open ({@code state == } {@link TicTacToeGame#OPEN_SPOT})
     *         and the game is not over and it is not the computer's turn; {@code false} otherwise
     */
    public static boolean shouldApply(char state, boolean gameOver, boolean computerTurn) {
        return state == TicTacToeGame.OPEN_SPOT && !gameOver && !computerTurn;
    }
}
