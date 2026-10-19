// Feature: custom-boardview-graphics-sound, Property 2: A move is applied — and its sound played — exactly when the target cell is open and input is allowed
package co.edu.unal.reto4_tictactoe;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Property 2: A move is applied — and its sound played — exactly when the target cell is open
 * and input is allowed.
 *
 * <p>Exercises the pure gating rule {@link MoveGate#shouldApply(char, boolean, boolean)} that
 * {@code MainActivity} delegates to before applying a human tap. The rule must return
 * {@code true} <em>iff</em> the target cell is open ({@link TicTacToeGame#OPEN_SPOT}) and both
 * the {@code gameOver} and {@code computerTurn} flags are {@code false}.
 *
 * <p>Validates: Requirements 6.4, 6.5, 6.6, 8.4, 9.4, 11.2, 13.3
 */
class MoveGatePropertyTest {

    /**
     * Generates cell states from the meaningful board alphabet: the two players and the open
     * spot. This constrains generation to the actual input space a cell can hold.
     */
    @Provide
    Arbitrary<Character> cellStates() {
        return Arbitraries.of(
                TicTacToeGame.HUMAN_PLAYER,    // 'X'
                TicTacToeGame.COMPUTER_PLAYER, // 'O'
                TicTacToeGame.OPEN_SPOT        // ' '
        );
    }

    /**
     * A move is applied exactly when the target cell is open and both flags are false.
     */
    @Property(tries = 200)
    void appliesIffCellOpenAndInputAllowed(
            @ForAll("cellStates") char state,
            @ForAll boolean gameOver,
            @ForAll boolean computerTurn) {

        boolean expected = (state == TicTacToeGame.OPEN_SPOT) && !gameOver && !computerTurn;

        assertEquals(
                expected,
                MoveGate.shouldApply(state, gameOver, computerTurn),
                "shouldApply must be true iff the cell is open and both flags are false");
    }

    /**
     * The decision is a pure function of its inputs: re-querying the gate across simulated
     * redraws of the board always yields the same result.
     */
    @Property(tries = 200)
    void resultIsStableAcrossSimulatedRedraws(
            @ForAll("cellStates") char state,
            @ForAll boolean gameOver,
            @ForAll boolean computerTurn) {

        boolean first = MoveGate.shouldApply(state, gameOver, computerTurn);

        for (int redraw = 0; redraw < 5; redraw++) {
            assertEquals(
                    first,
                    MoveGate.shouldApply(state, gameOver, computerTurn),
                    "gating decision must be stable across redraws");
        }
    }

    /**
     * While a delayed computer move is pending ({@code computerTurn == true}), no human move is
     * ever applied, regardless of the cell state or whether the game is over.
     */
    @Property(tries = 200)
    void computerTurnAlwaysBlocksMove(
            @ForAll("cellStates") char state,
            @ForAll boolean gameOver) {

        assertFalse(
                MoveGate.shouldApply(state, gameOver, true),
                "a pending computer turn must always block a human move");
    }
}
