package co.edu.unal.reto4_tictactoe;

/**
 * Pure, Android-free seam over the game model used by {@link TurnOrchestrator}.
 *
 * <p>The method signatures match {@link TicTacToeGame} exactly so the real game can be adapted
 * to this interface with a thin wrapper (see {@link TurnOrchestrator#adapt(TicTacToeGame)}) and
 * so a scripted fake can drive the turn-orchestration logic on the JVM without a device (see
 * design "Extract-for-testability" and Property 3).
 */
public interface Game {

    /**
     * @param location board index {@code 0..8}
     * @return the current state of the cell (e.g. {@link TicTacToeGame#OPEN_SPOT})
     */
    char getBoardState(int location);

    /**
     * Apply a move for the given player at the given location.
     *
     * @param player   the player mark to write
     * @param location board index {@code 0..8}
     * @return {@code true} if the move was applied, {@code false} otherwise
     */
    boolean setMove(char player, int location);

    /**
     * @return the winner code: {@code 0} continue, {@code 1} tie, {@code 2} human, {@code 3} computer
     */
    int checkForWinner();

    /**
     * @return the computer's chosen board index {@code 0..8}, or {@code -1} if no valid move
     */
    int getComputerMove();
}
