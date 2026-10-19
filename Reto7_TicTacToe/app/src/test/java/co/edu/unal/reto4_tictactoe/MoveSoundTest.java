// Feature: custom-boardview-graphics-sound, Task 9.3: sound played once per human/computer move
package co.edu.unal.reto4_tictactoe;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Example (non-property) unit tests that a move sound is played exactly once per applied move.
 *
 * <p>{@code android.media.MediaPlayer} cannot be instantiated on the JVM, so the "play the move
 * sound" side effect is exercised through the pure {@link TurnOrchestrator.SoundCue} seam that
 * {@code MainActivity} already delegates to. A spy {@code SoundCue} counts HUMAN vs COMPUTER plays
 * while the orchestrator runs against a scripted fake {@link Game}, so we can assert one human
 * sound on a valid human move and one computer sound on the following computer move &mdash; and no
 * sound when a tap is gated out.
 *
 * <p>Validates: Requirements 9.2, 9.3
 */
class MoveSoundTest {

    /** Spy sound callback: records how many times each move sound is played. */
    private static final class SoundSpy implements TurnOrchestrator.SoundCue {
        int humanPlays;
        int computerPlays;

        @Override
        public void play(TurnOrchestrator.Sound sound) {
            if (sound == TurnOrchestrator.Sound.HUMAN) {
                humanPlays++;
            } else {
                computerPlays++;
            }
        }
    }

    /**
     * Scripted fake game. The board reports every queried cell as open, and {@code checkForWinner}
     * returns a fixed sequence of codes so the flow can be pinned deterministically. Records the
     * moves applied so we can confirm exactly one human and (optionally) one computer move.
     */
    private static final class FakeGame implements Game {
        private final int[] winnerScript;
        private final int computerMove;
        private int checkIndex;
        int humanMoves;
        int computerMoves;

        FakeGame(int computerMove, int... winnerScript) {
            this.computerMove = computerMove;
            this.winnerScript = winnerScript;
        }

        @Override
        public char getBoardState(int location) {
            return TicTacToeGame.OPEN_SPOT;
        }

        @Override
        public boolean setMove(char player, int location) {
            if (player == TicTacToeGame.HUMAN_PLAYER) {
                humanMoves++;
            } else {
                computerMoves++;
            }
            return true;
        }

        @Override
        public int checkForWinner() {
            int code = winnerScript[Math.min(checkIndex, winnerScript.length - 1)];
            checkIndex++;
            return code;
        }

        @Override
        public int getComputerMove() {
            return computerMove;
        }
    }

    @Test
    @DisplayName("a valid human move that continues the game plays one human then one computer sound")
    void oneHumanAndOneComputerSoundWhenGameContinues() {
        SoundSpy spy = new SoundSpy();
        // First check -> 0 (continue, so computer moves); second check -> 0 (still continuing).
        FakeGame game = new FakeGame(4, 0, 0);

        TurnOrchestrator.Result result =
                TurnOrchestrator.onMoveSelected(game, 0, false, false, spy);

        assertEquals(true, result.wasApplied(), "the tap on an open cell must be applied");
        assertEquals(1, spy.humanPlays, "exactly one human move sound");
        assertEquals(1, spy.computerPlays, "exactly one computer move sound");
        assertEquals(1, game.humanMoves, "exactly one human move applied");
        assertEquals(1, game.computerMoves, "exactly one computer move applied");
    }

    @Test
    @DisplayName("a human move that ends the game plays one human sound and no computer sound")
    void onlyHumanSoundWhenHumanMoveEndsGame() {
        SoundSpy spy = new SoundSpy();
        // Human move immediately wins (code 2) -> no computer turn, no computer sound.
        FakeGame game = new FakeGame(4, 2);

        TurnOrchestrator.Result result =
                TurnOrchestrator.onMoveSelected(game, 0, false, false, spy);

        assertEquals(true, result.wasApplied());
        assertEquals(1, spy.humanPlays, "exactly one human move sound");
        assertEquals(0, spy.computerPlays, "no computer sound when the human move ends the game");
        assertEquals(1, game.humanMoves);
        assertEquals(0, game.computerMoves);
    }

    @Test
    @DisplayName("a tap that does not result in a valid move plays no sound")
    void noSoundWhenTapGatedOut() {
        SoundSpy spy = new SoundSpy();
        FakeGame game = new FakeGame(4, 0);

        // Game already over -> the tap is gated out; no move, no sound (Requirement 9.4).
        TurnOrchestrator.Result result =
                TurnOrchestrator.onMoveSelected(game, 0, true, false, spy);

        assertEquals(false, result.wasApplied());
        assertEquals(0, spy.humanPlays, "no human sound on a gated-out tap");
        assertEquals(0, spy.computerPlays, "no computer sound on a gated-out tap");
        assertEquals(0, game.humanMoves);
        assertEquals(0, game.computerMoves);
    }
}
