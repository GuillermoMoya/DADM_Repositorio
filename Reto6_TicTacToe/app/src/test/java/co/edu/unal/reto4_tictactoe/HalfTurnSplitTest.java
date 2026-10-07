// Feature: orientation-and-state-persistence, Task 2.1: split half-turns in TurnOrchestrator
package co.edu.unal.reto4_tictactoe;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused JVM unit tests for the two pure half-turn methods
 * {@link TurnOrchestrator#applyHumanMove(Game, int, TurnOrchestrator.SoundCue)} and
 * {@link TurnOrchestrator#playComputerMove(Game, TurnOrchestrator.SoundCue)}.
 *
 * <p>Mirrors the existing {@code MoveSoundTest} style: a spy {@link TurnOrchestrator.SoundCue}
 * counts HUMAN vs COMPUTER plays while the orchestrator runs against a scripted fake {@link Game}.
 * The split guarantees each half-turn applies exactly one move and never replays the other.
 *
 * <p>Validates: Requirements 5.1, 11.11
 */
class HalfTurnSplitTest {

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
     * Scripted fake game. {@code openCell} controls whether {@code setMove} succeeds; the winner
     * script pins {@code checkForWinner} outcomes; {@code computerMove} is the index returned by
     * {@code getComputerMove}. Records applied moves so each half-turn can be asserted precisely.
     */
    private static final class FakeGame implements Game {
        private final boolean cellOpen;
        private final int computerMove;
        private final int[] winnerScript;
        private int checkIndex;
        int humanMoves;
        int computerMoves;

        FakeGame(boolean cellOpen, int computerMove, int... winnerScript) {
            this.cellOpen = cellOpen;
            this.computerMove = computerMove;
            this.winnerScript = winnerScript;
        }

        @Override
        public char getBoardState(int location) {
            return cellOpen ? TicTacToeGame.OPEN_SPOT : TicTacToeGame.HUMAN_PLAYER;
        }

        @Override
        public boolean setMove(char player, int location) {
            if (!cellOpen) {
                return false;
            }
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
    @DisplayName("applyHumanMove applies only the human move and enters the computer turn when the game continues")
    void applyHumanMoveContinuesGame() {
        SoundSpy spy = new SoundSpy();
        // Human move applied; winner check -> 0 (game continues).
        FakeGame game = new FakeGame(true, 4, 0);

        TurnOrchestrator.Result result = TurnOrchestrator.applyHumanMove(game, 0, spy);

        assertTrue(result.wasApplied(), "the human move on an open cell must be applied");
        assertTrue(result.enteredComputerTurn(), "the computer owes a move when the game continues");
        assertEquals(0, result.winner(), "winner code reflects a continuing game");
        assertEquals(1, game.humanMoves, "exactly one human move applied");
        assertEquals(0, game.computerMoves, "applyHumanMove must never apply a computer move");
        assertEquals(1, spy.humanPlays, "exactly one human sound");
        assertEquals(0, spy.computerPlays, "no computer sound in the human half-turn");
    }

    @Test
    @DisplayName("applyHumanMove that ends the game does not enter the computer turn")
    void applyHumanMoveEndsGame() {
        SoundSpy spy = new SoundSpy();
        // Human move wins immediately (code 2).
        FakeGame game = new FakeGame(true, 4, 2);

        TurnOrchestrator.Result result = TurnOrchestrator.applyHumanMove(game, 0, spy);

        assertTrue(result.wasApplied());
        assertFalse(result.enteredComputerTurn(), "a game-ending human move leaves no computer turn");
        assertEquals(2, result.winner());
        assertEquals(1, game.humanMoves);
        assertEquals(0, game.computerMoves);
        assertEquals(1, spy.humanPlays);
        assertEquals(0, spy.computerPlays);
    }

    @Test
    @DisplayName("applyHumanMove ignores an occupied/invalid cell with no side effects")
    void applyHumanMoveIgnoresOccupiedCell() {
        SoundSpy spy = new SoundSpy();
        // setMove returns false for an occupied cell.
        FakeGame game = new FakeGame(false, 4, 0);

        TurnOrchestrator.Result result = TurnOrchestrator.applyHumanMove(game, 0, spy);

        assertFalse(result.wasApplied(), "an occupied/invalid cell must be ignored");
        assertFalse(result.enteredComputerTurn());
        assertEquals(0, game.humanMoves, "no move applied on an occupied cell");
        assertEquals(0, spy.humanPlays, "no sound on an ignored tap");
        assertEquals(0, spy.computerPlays);
    }

    @Test
    @DisplayName("playComputerMove applies exactly one computer move and re-checks the winner")
    void playComputerMoveAppliesOneMove() {
        SoundSpy spy = new SoundSpy();
        // Computer plays index 4; winner check -> 3 (computer wins).
        FakeGame game = new FakeGame(true, 4, 3);

        TurnOrchestrator.Result result = TurnOrchestrator.playComputerMove(game, spy);

        assertTrue(result.wasApplied());
        assertEquals(3, result.winner(), "winner code reflects the computer's result");
        assertEquals(1, game.computerMoves, "exactly one computer move applied");
        assertEquals(0, game.humanMoves, "playComputerMove must never apply a human move");
        assertEquals(1, spy.computerPlays, "exactly one computer sound");
        assertEquals(0, spy.humanPlays, "no human sound in the computer half-turn");
    }

    @Test
    @DisplayName("playComputerMove ignores the turn when no cell is available")
    void playComputerMoveIgnoresWhenNoCell() {
        SoundSpy spy = new SoundSpy();
        // getComputerMove -> -1 (no cell available).
        FakeGame game = new FakeGame(true, -1, 0);

        TurnOrchestrator.Result result = TurnOrchestrator.playComputerMove(game, spy);

        assertFalse(result.wasApplied(), "no cell available must be ignored");
        assertEquals(0, game.computerMoves, "no computer move applied when getComputerMove is -1");
        assertEquals(0, spy.computerPlays, "no sound when the computer has no move");
        assertEquals(0, spy.humanPlays);
    }
}
