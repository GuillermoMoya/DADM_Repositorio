package co.edu.unal.reto4_tictactoe;

/**
 * Pure, Android-free turn-orchestration logic extracted from
 * {@code MainActivity.onMoveSelected}.
 *
 * <p>The turn flow that decides whether a tap is applied and drives the human-then-computer
 * sequence originally lived inside an Android {@code Activity}, which is not JVM-unit-testable
 * without an emulator. This helper hoists that flow behind two pure seams &mdash; the {@link Game}
 * model interface and a {@link SoundCue} callback &mdash; so it can be exercised against a scripted
 * fake game and a spy (see design "Extract-for-testability" and Property 3). {@code MainActivity}
 * delegates to {@link #onMoveSelected} so there is a single source of truth for the flow.
 *
 * <p>The sequence mirrors the original exactly:
 * <ol>
 *   <li>Gate the tap via {@link MoveGate#shouldApply(char, boolean, boolean)}; if it must not
 *       apply, return {@link Result#ignored()} with no side effects.</li>
 *   <li>Apply the human move and play the human sound.</li>
 *   <li>Evaluate {@link Game#checkForWinner()}. If it is {@code 0} (continue), take the computer
 *       turn: fetch {@link Game#getComputerMove()}, and when it is not {@code -1} apply it, play
 *       the computer sound, and re-check the winner.</li>
 * </ol>
 */
public final class TurnOrchestrator {

    private TurnOrchestrator() {
        // Utility class; not instantiable.
    }

    /** Identifies which move sound to play. Keeps the orchestrator free of Android audio APIs. */
    public enum Sound {
        HUMAN,
        COMPUTER
    }

    /** Sound side-effect callback so the orchestrator stays Android-free. */
    public interface SoundCue {
        void play(Sound sound);
    }

    /** Outcome of a single {@link #onMoveSelected} call. */
    public static final class Result {

        private final boolean applied;
        private final boolean enteredComputerTurn;
        private final int winner;

        private Result(boolean applied, boolean enteredComputerTurn, int winner) {
            this.applied = applied;
            this.enteredComputerTurn = enteredComputerTurn;
            this.winner = winner;
        }

        /** @return an outcome for a tap that was gated out (no side effects occurred). */
        static Result ignored() {
            return new Result(false, false, 0);
        }

        /** @return {@code true} if the human move was applied (the tap passed the gate). */
        public boolean wasApplied() {
            return applied;
        }

        /** @return {@code true} if the computer turn was entered (human move left the game continuing). */
        public boolean enteredComputerTurn() {
            return enteredComputerTurn;
        }

        /** @return the winner code after the turn: {@code 0} continue, {@code 1} tie, {@code 2} human, {@code 3} computer. */
        public int winner() {
            return winner;
        }
    }

    /**
     * Runs the turn-orchestration flow for a tap at {@code position}.
     *
     * @param game       the game model seam (adapt {@link TicTacToeGame} via {@link #adapt(TicTacToeGame)})
     * @param position   the tapped board index {@code 0..8}
     * @param gameOver   whether the game has already ended
     * @param computerTurn whether it is currently the computer's turn
     * @param sound      callback used to play the human/computer move sounds
     * @return the {@link Result} describing whether the move applied and the resulting winner code
     */
    public static Result onMoveSelected(
            Game game,
            int position,
            boolean gameOver,
            boolean computerTurn,
            SoundCue sound) {

        // Gate the move: ignore taps while the game is over, while the computer is moving, or on
        // a cell that is not open (no sound in that case).
        if (!MoveGate.shouldApply(game.getBoardState(position), gameOver, computerTurn)) {
            return Result.ignored();
        }

        // Apply the human move.
        game.setMove(TicTacToeGame.HUMAN_PLAYER, position);
        sound.play(Sound.HUMAN);

        int winner = game.checkForWinner();
        boolean enteredComputerTurn = false;

        // If the game continues, let the computer take its turn.
        if (winner == 0) {
            enteredComputerTurn = true;

            int move = game.getComputerMove();
            if (move != -1) {
                game.setMove(TicTacToeGame.COMPUTER_PLAYER, move);
                sound.play(Sound.COMPUTER);
                winner = game.checkForWinner();
            }
        }

        return new Result(true, enteredComputerTurn, winner);
    }

    /**
     * Adapts a concrete {@link TicTacToeGame} to the {@link Game} seam. The method signatures
     * already match, so this is a thin, no-logic delegation wrapper.
     *
     * @param game the concrete game
     * @return a {@link Game} view over {@code game}
     */
    public static Game adapt(final TicTacToeGame game) {
        return new Game() {
            @Override
            public char getBoardState(int location) {
                return game.getBoardState(location);
            }

            @Override
            public boolean setMove(char player, int location) {
                return game.setMove(player, location);
            }

            @Override
            public int checkForWinner() {
                return game.checkForWinner();
            }

            @Override
            public int getComputerMove() {
                return game.getComputerMove();
            }
        };
    }
}
