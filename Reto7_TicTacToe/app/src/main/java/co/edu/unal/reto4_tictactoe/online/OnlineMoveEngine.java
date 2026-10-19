package co.edu.unal.reto4_tictactoe.online;

import co.edu.unal.reto4_tictactoe.GameStatus;
import co.edu.unal.reto4_tictactoe.MoveGate;
import co.edu.unal.reto4_tictactoe.TicTacToeGame;

/**
 * Pure, Android-free, Firebase-free move guard/orchestrator shared by the whole Modo_Online.
 *
 * <p>This is the single source of truth for "is this online move legal, and if so what is the
 * resulting authoritative state?" Both {@code FakeMatchRepository} (tests) and
 * {@code FirebaseMatchRepository} (task 8.2, inside its RTDB transaction) call this helper so the
 * exact same validation runs everywhere (design "Reutilización de la lógica pura" and
 * §Concurrency and Integrity → "Aplicar un movimiento"). It is the pure function that Properties
 * 1, 4 and 5 (tasks 5.2–5.4) exercise.
 *
 * <h2>Legality (Property 1)</h2>
 * Given a {@link MatchState}, the requester's {@code uid} and a target {@code cell}, a move is
 * legal <b>iff</b> all of the following hold (Req 5.1, 5.2, 12.3, 14.1, 14.2, 23.1, 23.6):
 * <ul>
 *   <li>{@code status == PLAYING} — no moves while WAITING/FINISHED/ABANDONED;</li>
 *   <li>the requester is a participant: {@code hostUid} (mark {@code 'X'}) or {@code guestUid}
 *       (mark {@code 'O'});</li>
 *   <li>the requester's mark equals {@code turn} — it is actually their turn;</li>
 *   <li>the target cell is in range and free (reusing {@link MoveGate#shouldApply} for the
 *       cell-free / not-game-over gate).</li>
 * </ul>
 *
 * <h2>Outcome</h2>
 * When legal, the move is written to a <b>copy</b> of the board, {@code winner} is recomputed with
 * {@link TicTacToeGame#checkForWinner()} on the resulting board (authoritative, Property 4), and:
 * <ul>
 *   <li>if {@code winner != 0} the lifecycle becomes {@link GameLifecycle#FINISHED} and {@code turn}
 *       is left unchanged (Req 14.4);</li>
 *   <li>otherwise {@code turn} alternates {@code X ↔ O} (Req 15.2, Property 5), keeping the match
 *       {@code PLAYING}.</li>
 * </ul>
 * The result also carries the matching {@link GameStatus} text mapping of the recomputed
 * {@code winner} (Req 13.2).
 *
 * <h2>Purity</h2>
 * {@link #evaluate(MatchState, String, int)} never mutates its {@code input}; it returns a fresh
 * {@link Result}. On rejection the result exposes no new state and the caller's input is untouched
 * (Property 1 "state unchanged when rejected"). The {@code updatedAt} timestamp is intentionally
 * <b>not</b> set here (clock injection is a repository concern); callers stamp it after applying.
 *
 * <p>Utility class: not instantiable.
 */
public final class OnlineMoveEngine {

    private OnlineMoveEngine() {
        // Utility class: no instances.
    }

    /**
     * Pure outcome of evaluating a candidate online move.
     *
     * <p>Immutable value object. When {@link #isApplied()} is {@code false}, the move was rejected
     * and {@link #getNewState()} is {@code null} (the caller's state must stay unchanged). When
     * {@code true}, {@link #getNewState()} is a brand-new {@link MatchState} reflecting the applied
     * move (board, winner, lifecycle, turn), and {@link #getStatus()} is the {@link GameStatus}
     * mapping of the recomputed {@code winner}.
     */
    public static final class Result {

        private final boolean applied;
        private final MatchState newState;
        private final int winner;
        private final GameStatus status;

        private Result(boolean applied, MatchState newState, int winner, GameStatus status) {
            this.applied = applied;
            this.newState = newState;
            this.winner = winner;
            this.status = status;
        }

        /** The singleton-like rejected result (no state change). */
        private static final Result REJECTED = new Result(false, null, MatchState.WINNER_NONE, null);

        static Result rejected() {
            return REJECTED;
        }

        static Result applied(MatchState newState, int winner, GameStatus status) {
            return new Result(true, newState, winner, status);
        }

        /** @return {@code true} iff the move was legal and applied. */
        public boolean isApplied() {
            return applied;
        }

        /**
         * @return a fresh {@link MatchState} with the applied move when {@link #isApplied()} is
         *     {@code true}; {@code null} when the move was rejected.
         */
        public MatchState getNewState() {
            return newState;
        }

        /**
         * @return the recomputed winner code ({@code 0|1|2|3}) when applied; {@code 0} when
         *     rejected.
         */
        public int getWinner() {
            return winner;
        }

        /**
         * @return the {@link GameStatus} mapping of the recomputed {@code winner} when applied;
         *     {@code null} when rejected.
         */
        public GameStatus getStatus() {
            return status;
        }
    }

    /**
     * Evaluates whether {@code requester} may play {@code cell} on {@code input}, and if so computes
     * the resulting authoritative state — without mutating {@code input}.
     *
     * @param input     the current authoritative {@link MatchState}; not mutated.
     * @param requester the uid attempting the move.
     * @param cell      the target board cell index.
     * @return an applied {@link Result} carrying a fresh state when the move is legal, or
     *     {@link Result#rejected()} (no state change) otherwise.
     */
    public static Result evaluate(MatchState input, String requester, int cell) {
        if (input == null || requester == null) {
            return Result.rejected();
        }
        // 1. Only PLAYING matches accept moves (Req 5.2, 12.3, 14.4).
        if (input.lifecycle() != GameLifecycle.PLAYING) {
            return Result.rejected();
        }
        // 2. Cell must be in range (defensive; MoveGate indexes the board below).
        if (cell < 0 || cell >= TicTacToeGame.BOARD_SIZE) {
            return Result.rejected();
        }
        // 3. Requester must be a participant; determine their mark (Req 5.1, 22.2).
        String mark;
        if (requester.equals(input.getHostUid())) {
            mark = MatchState.MARK_X;
        } else if (requester.equals(input.getGuestUid())) {
            mark = MatchState.MARK_O;
        } else {
            return Result.rejected();
        }
        // 4. Must be the requester's turn (Req 14.1, 23.1).
        if (!mark.equals(input.getTurn())) {
            return Result.rejected();
        }
        // 5. Board must be a valid 9-char board and the cell free (Req 14.2, 23.6).
        String boardString = input.getBoard();
        if (boardString == null || boardString.length() != TicTacToeGame.BOARD_SIZE) {
            return Result.rejected();
        }
        char[] board = BoardMapper.toCharArray(boardString);
        // Reuse MoveGate for the cell-free / not-game-over gate. There is no computer turn online,
        // so computerTurn is always false; the game is not over because status == PLAYING here.
        if (!MoveGate.shouldApply(board[cell], false, false)) {
            return Result.rejected();
        }

        // --- Legal: build a fresh resulting state (input stays untouched). ---
        board[cell] = mark.charAt(0);

        TicTacToeGame game = new TicTacToeGame();
        game.setBoardState(board);
        int winner = game.checkForWinner();

        MatchState next = copyOf(input);
        next.setBoard(BoardMapper.toBoardString(board));
        next.setWinner(winner);
        if (winner != MatchState.WINNER_NONE) {
            next.setLifecycle(GameLifecycle.FINISHED);
        } else {
            next.setTurn(mark.equals(MatchState.MARK_X) ? MatchState.MARK_O : MatchState.MARK_X);
        }

        return Result.applied(next, winner, GameStatus.statusFor(winner));
    }

    /** Deep-ish copy of a {@link MatchState} so {@link #evaluate} never mutates its input. */
    private static MatchState copyOf(MatchState s) {
        MatchState c = new MatchState();
        c.setMatchId(s.getMatchId());
        c.setHostUid(s.getHostUid());
        c.setGuestUid(s.getGuestUid());
        c.setBoard(s.getBoard());
        c.setTurn(s.getTurn());
        c.setStatus(s.getStatus());
        c.setWinner(s.getWinner());
        c.setCreatedAt(s.getCreatedAt());
        c.setUpdatedAt(s.getUpdatedAt());
        c.setPresence(s.getPresence() == null
                ? new java.util.HashMap<>()
                : new java.util.HashMap<>(s.getPresence()));
        return c;
    }
}
