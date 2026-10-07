// Feature: custom-boardview-graphics-sound, Property 3: A computer move follows a valid human move exactly once while the game continues
package co.edu.unal.reto4_tictactoe;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Property 3: A computer move follows a valid human move exactly once while the game continues.
 *
 * <p>The turn-orchestration flow used to live inside {@code MainActivity.onMoveSelected}, an
 * Android {@code Activity} that cannot run on the JVM. It was extracted into the pure
 * {@link TurnOrchestrator}, which drives the {@link Game} seam and a {@link TurnOrchestrator.SoundCue}
 * callback. This test exercises that flow against a <em>scripted fake</em> game (canned
 * {@code checkForWinner}/{@code getComputerMove}) plus a spy that counts every
 * {@code setMove(COMPUTER_PLAYER, m)} and every computer-sound cue.
 *
 * <p>The property asserts: when a valid human move leaves the game continuing
 * ({@code checkForWinner() == 0}), the orchestrator applies <em>exactly one</em>
 * {@code setMove(COMPUTER_PLAYER, m)} with {@code m} equal to {@code getComputerMove()} (and plays
 * one computer sound); when the human move ends the game (winner code {@code != 0}), it applies
 * <em>none</em>. A gated-out tap applies neither a human nor a computer move.
 *
 * <p>Validates: Requirements 5.3, 8.2
 */
class ComputerMovePropertyTest {

    /**
     * Scripted fake {@link Game}. The board cell state, the two successive {@code checkForWinner}
     * results (before and after the computer move), and the computer move index are all supplied
     * by the test. Every {@code setMove} call is recorded so the spy can assert exactly-once.
     */
    private static final class ScriptedGame implements Game {

        private final char cellState;
        private final int winnerAfterHuman;
        private final int winnerAfterComputer;
        private final int computerMove;

        private int checkForWinnerCalls = 0;

        /** Records each setMove(player, location) as "player@location". */
        final List<String> setMoves = new ArrayList<>();
        int computerSetMoves = 0;
        int lastComputerLocation = Integer.MIN_VALUE;

        ScriptedGame(char cellState, int winnerAfterHuman, int winnerAfterComputer, int computerMove) {
            this.cellState = cellState;
            this.winnerAfterHuman = winnerAfterHuman;
            this.winnerAfterComputer = winnerAfterComputer;
            this.computerMove = computerMove;
        }

        @Override
        public char getBoardState(int location) {
            return cellState;
        }

        @Override
        public boolean setMove(char player, int location) {
            setMoves.add(player + "@" + location);
            if (player == TicTacToeGame.COMPUTER_PLAYER) {
                computerSetMoves++;
                lastComputerLocation = location;
            }
            return true;
        }

        @Override
        public int checkForWinner() {
            // First check (after the human move) returns winnerAfterHuman; any subsequent check
            // (after the computer move) returns winnerAfterComputer, mirroring the real flow.
            checkForWinnerCalls++;
            return checkForWinnerCalls == 1 ? winnerAfterHuman : winnerAfterComputer;
        }

        @Override
        public int getComputerMove() {
            return computerMove;
        }
    }

    /** Counts sound cues so we can assert one human + (zero|one) computer sounds. */
    private static final class SoundSpy implements TurnOrchestrator.SoundCue {
        int human = 0;
        int computer = 0;

        @Override
        public void play(TurnOrchestrator.Sound sound) {
            if (sound == TurnOrchestrator.Sound.HUMAN) {
                human++;
            } else {
                computer++;
            }
        }
    }

    /** Winner codes the model can report: 0 continue, 1 tie, 2 human win, 3 computer win. */
    @Provide
    Arbitrary<Integer> winnerCodes() {
        return Arbitraries.integers().between(0, 3);
    }

    /**
     * When a valid human move (open cell, input allowed) leaves the game continuing, exactly one
     * computer move is applied at {@code getComputerMove()} and exactly one computer sound plays.
     */
    @Property(tries = 200)
    void continuingGameAppliesExactlyOneComputerMove(
            @ForAll @IntRange(min = 0, max = 8) int position,
            @ForAll @IntRange(min = 0, max = 8) int computerMove,
            @ForAll("winnerCodes") int winnerAfterComputer) {

        // Human move leaves the game continuing (checkForWinner() == 0), so the computer moves.
        ScriptedGame game =
                new ScriptedGame(TicTacToeGame.OPEN_SPOT, 0, winnerAfterComputer, computerMove);
        SoundSpy sound = new SoundSpy();

        TurnOrchestrator.Result result =
                TurnOrchestrator.onMoveSelected(game, position, false, false, sound);

        assertTrue(result.wasApplied(), "a valid human tap must be applied");
        assertTrue(result.enteredComputerTurn(), "a continuing game must enter the computer turn");

        assertEquals(1, game.computerSetMoves,
                "exactly one computer move must be applied while the game continues");
        assertEquals(computerMove, game.lastComputerLocation,
                "the computer move must be applied at getComputerMove()");
        assertEquals(1, sound.human, "the human move must play exactly one human sound");
        assertEquals(1, sound.computer, "the computer move must play exactly one computer sound");
        assertEquals(winnerAfterComputer, result.winner(),
                "the reported winner must be the post-computer-move check");
    }

    /**
     * When the human move ends the game (winner code non-zero), no computer move is applied and
     * no computer sound plays.
     */
    @Property(tries = 200)
    void gameEndingHumanMoveAppliesNoComputerMove(
            @ForAll @IntRange(min = 0, max = 8) int position,
            @ForAll @IntRange(min = 1, max = 3) int winnerAfterHuman,
            @ForAll @IntRange(min = 0, max = 8) int computerMove) {

        ScriptedGame game =
                new ScriptedGame(TicTacToeGame.OPEN_SPOT, winnerAfterHuman, 0, computerMove);
        SoundSpy sound = new SoundSpy();

        TurnOrchestrator.Result result =
                TurnOrchestrator.onMoveSelected(game, position, false, false, sound);

        assertTrue(result.wasApplied(), "a valid human tap must be applied");
        assertFalse(result.enteredComputerTurn(),
                "a game-ending human move must not enter the computer turn");

        assertEquals(0, game.computerSetMoves,
                "no computer move may be applied when the human move ends the game");
        assertEquals(1, sound.human, "the human move must still play exactly one human sound");
        assertEquals(0, sound.computer, "no computer sound may play when the game has ended");
        assertEquals(winnerAfterHuman, result.winner(),
                "the reported winner must be the game-ending code from the human move");
    }

    /**
     * When the game continues but {@code getComputerMove()} returns {@code -1} (no valid move),
     * the guard prevents any computer {@code setMove} and no computer sound plays.
     */
    @Property(tries = 200)
    void continuingGameWithNoComputerMoveAppliesNone(
            @ForAll @IntRange(min = 0, max = 8) int position) {

        ScriptedGame game = new ScriptedGame(TicTacToeGame.OPEN_SPOT, 0, 0, -1);
        SoundSpy sound = new SoundSpy();

        TurnOrchestrator.Result result =
                TurnOrchestrator.onMoveSelected(game, position, false, false, sound);

        assertTrue(result.enteredComputerTurn(),
                "a continuing game still enters the computer turn even with no move available");
        assertEquals(0, game.computerSetMoves,
                "a getComputerMove() of -1 must not apply any computer move");
        assertEquals(0, sound.computer, "no computer sound plays when there is no computer move");
    }

    /**
     * A gated-out tap (occupied cell, game over, or computer turn) applies neither a human nor a
     * computer move and plays no sound.
     */
    @Property(tries = 200)
    void gatedTapAppliesNothing(
            @ForAll @IntRange(min = 0, max = 8) int position,
            @ForAll("gatedInputs") char[] gate) {

        // gate[0] = cell state, gate[1] = gameOver flag, gate[2] = computerTurn flag
        char cellState = gate[0];
        boolean gameOver = gate[1] == 1;
        boolean computerTurn = gate[2] == 1;

        // Ensure this combination is actually gated out (not the allowed case).
        boolean allowed = cellState == TicTacToeGame.OPEN_SPOT && !gameOver && !computerTurn;
        if (allowed) {
            return; // covered by the other properties
        }

        ScriptedGame game = new ScriptedGame(cellState, 0, 0, 0);
        SoundSpy sound = new SoundSpy();

        TurnOrchestrator.Result result =
                TurnOrchestrator.onMoveSelected(game, position, gameOver, computerTurn, sound);

        assertFalse(result.wasApplied(), "a gated-out tap must not be applied");
        assertTrue(game.setMoves.isEmpty(), "a gated-out tap must apply no moves at all");
        assertEquals(0, sound.human, "a gated-out tap must play no human sound");
        assertEquals(0, sound.computer, "a gated-out tap must play no computer sound");
    }

    /** Cell state + gameOver/computerTurn flag combinations, encoded as {state, gameOver, turn}. */
    @Provide
    Arbitrary<char[]> gatedInputs() {
        Arbitrary<Character> states = Arbitraries.of(
                TicTacToeGame.HUMAN_PLAYER,
                TicTacToeGame.COMPUTER_PLAYER,
                TicTacToeGame.OPEN_SPOT);
        Arbitrary<Character> flags = Arbitraries.of((char) 0, (char) 1);
        return Combinators.combine(states, flags, flags)
                .as((state, gameOver, turn) -> new char[] {state, gameOver, turn});
    }
}
