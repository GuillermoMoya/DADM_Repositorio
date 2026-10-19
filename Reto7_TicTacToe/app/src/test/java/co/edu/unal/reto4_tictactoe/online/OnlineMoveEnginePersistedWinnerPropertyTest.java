package co.edu.unal.reto4_tictactoe.online;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.edu.unal.reto4_tictactoe.TicTacToeGame;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Tuple;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Property-based test for the authoritative persisted result of an online move.
 *
 * <p>// Feature: online-multiplayer, Property 4: winner == checkForWinner
 *
 * <p><b>Validates: Requirements 14.3, 14.4</b>
 *
 * <p>Property 4 (design §Correctness Properties): for every <i>valid</i> move applied to a
 * {@code PLAYING} match, the persisted {@code winner} is exactly
 * {@link TicTacToeGame#checkForWinner()} evaluated on the resulting board (0 continue, 1 tie,
 * 2 X wins, 3 O wins), and the match transitions to {@link GameLifecycle#FINISHED} if and only if
 * that code is non-zero (otherwise it stays {@code PLAYING}).
 *
 * <p>This exercises {@link OnlineMoveEngine#evaluate(MatchState, String, int)} — the single pure
 * move guard/orchestrator shared by {@code FakeMatchRepository} and {@code FirebaseMatchRepository}.
 * The generator only produces <i>legal</i> moves (a reachable {@code PLAYING} board, a requester
 * whose mark equals {@code turn}, and a free target cell), so {@code evaluate} must always apply
 * and the assertions above must always hold. Minimum 100 iterations.
 */
class OnlineMoveEnginePersistedWinnerPropertyTest {

    // Feature: online-multiplayer, Property 4: winner == checkForWinner
    @Property(tries = 100)
    void persistedWinnerMatchesCheckForWinnerAndFinishedIffNonZero(
            @ForAll("legalMoves") ValidMove move) {

        MatchState before = move.state;
        String requester = move.requester;
        int cell = move.cell;

        // Snapshot the input so we can later confirm evaluate() did not mutate it (purity).
        String boardBefore = before.getBoard();
        String turnBefore = before.getTurn();
        String statusBefore = before.getStatus();
        int winnerBefore = before.getWinner();

        OnlineMoveEngine.Result result = OnlineMoveEngine.evaluate(before, requester, cell);

        // The generator only yields legal moves, so evaluate must apply.
        assertTrue(result.isApplied(),
                () -> "legal move must be applied (board=" + boardBefore + ", turn=" + turnBefore
                        + ", requester=" + requester + ", cell=" + cell + ")");

        MatchState after = result.getNewState();
        assertNotNull(after, "applied result must expose a new state");

        // Compute the authoritative winner independently from the resulting board.
        char[] resultingBoard = BoardMapper.toCharArray(after.getBoard());
        TicTacToeGame oracle = new TicTacToeGame();
        oracle.setBoardState(resultingBoard);
        int expectedWinner = oracle.checkForWinner();

        // Property 4a: persisted winner == checkForWinner(resulting board) (Req 14.3).
        assertEquals(expectedWinner, after.getWinner(),
                "persisted winner must equal checkForWinner(resulting board)");
        assertEquals(expectedWinner, result.getWinner(),
                "Result.getWinner() must agree with the persisted winner");

        // Property 4b: FINISHED if and only if winner != 0; otherwise still PLAYING (Req 14.4).
        if (expectedWinner != MatchState.WINNER_NONE) {
            assertEquals(GameLifecycle.FINISHED, after.lifecycle(),
                    "match must transition to FINISHED exactly when winner != 0");
        } else {
            assertEquals(GameLifecycle.PLAYING, after.lifecycle(),
                    "match must stay PLAYING while winner == 0");
        }

        // Purity: evaluate() must not mutate the caller's input state.
        assertEquals(boardBefore, before.getBoard(), "input board must be unchanged");
        assertEquals(turnBefore, before.getTurn(), "input turn must be unchanged");
        assertEquals(statusBefore, before.getStatus(), "input status must be unchanged");
        assertEquals(winnerBefore, before.getWinner(), "input winner must be unchanged");
    }

    /**
     * Generates only <b>legal</b> candidate moves: a reachable {@code PLAYING} {@link MatchState}
     * (produced by alternating X/O moves from an empty board, stopping before any win/tie so a
     * legal next move exists), a requester uid whose mark equals the match {@code turn}, and a
     * free target cell. The resulting move after application may or may not win — both outcomes are
     * exercised and asserted.
     */
    @Provide
    Arbitrary<ValidMove> legalMoves() {
        Arbitrary<String> hostUids = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(8);
        Arbitrary<String> guestUids = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(8);
        Arbitrary<Long> seeds = Arbitraries.longs();
        // Number of plies already made before the move under test (0..8); clamped to a legal,
        // non-terminal position inside buildReachable.
        Arbitrary<Integer> plies = Arbitraries.integers().between(0, 8);

        return Combinators.combine(hostUids, guestUids, seeds, plies)
                .as(Tuple::of)
                .map(t -> buildReachable(t.get1(), "O_" + t.get2(), t.get3(), t.get4()))
                .filter(vm -> vm != null);
    }

    /**
     * Builds a non-terminal {@code PLAYING} board by applying up to {@code targetPlies} alternating
     * legal moves (X first) from an empty board, then selects a legal next move (requester == the
     * player whose turn it is, and a free cell). Returns {@code null} if no legal next move exists
     * (board already full) so the generator can discard it.
     */
    private ValidMove buildReachable(String hostUid, String guestUid, long seed, int targetPlies) {
        if (hostUid.equals(guestUid)) {
            guestUid = guestUid + "x";
        }
        Random rnd = new Random(seed);
        char[] board = new char[TicTacToeGame.BOARD_SIZE];
        Arrays.fill(board, MatchState.EMPTY_CELL);

        char current = 'X';
        for (int ply = 0; ply < targetPlies; ply++) {
            // Stop early if the current position is already terminal (a win/tie) — we need a
            // non-terminal PLAYING state with at least one free cell left.
            if (isTerminal(board)) {
                break;
            }
            List<Integer> free = freeCells(board, rnd);
            if (free.isEmpty()) {
                break;
            }
            int pick = free.get(0);
            board[pick] = current;
            current = (current == 'X') ? 'O' : 'X';
        }

        // After building, the position must be non-terminal with a free cell so a legal move exists.
        if (isTerminal(board)) {
            return null;
        }
        List<Integer> free = freeCells(board, rnd);
        if (free.isEmpty()) {
            return null;
        }
        int cell = free.get(0);

        String turn = String.valueOf(current);
        String requester = current == 'X' ? hostUid : guestUid;

        MatchState state = new MatchState();
        state.setMatchId("m-" + Math.abs(seed));
        state.setHostUid(hostUid);
        state.setGuestUid(guestUid);
        state.setBoard(new String(board));
        state.setTurn(turn);
        state.setLifecycle(GameLifecycle.PLAYING);
        state.setWinner(MatchState.WINNER_NONE);

        return new ValidMove(state, requester, cell);
    }

    private static boolean isTerminal(char[] board) {
        TicTacToeGame game = new TicTacToeGame();
        game.setBoardState(board);
        return game.checkForWinner() != 0;
    }

    private static List<Integer> freeCells(char[] board, Random rnd) {
        List<Integer> free = new ArrayList<>();
        for (int i = 0; i < board.length; i++) {
            if (board[i] == MatchState.EMPTY_CELL) {
                free.add(i);
            }
        }
        Collections.shuffle(free, rnd);
        return free;
    }

    /** Immutable carrier for a generated legal move. */
    static final class ValidMove {
        final MatchState state;
        final String requester;
        final int cell;

        ValidMove(MatchState state, String requester, int cell) {
            this.state = state;
            this.requester = requester;
            this.cell = cell;
        }

        @Override
        public String toString() {
            return "ValidMove{board='" + state.getBoard() + "', turn=" + state.getTurn()
                    + ", requester=" + requester + ", cell=" + cell + "}";
        }
    }
}
