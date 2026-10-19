package co.edu.unal.reto4_tictactoe;

import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import co.edu.unal.reto4_tictactoe.online.AuthProvider;
import co.edu.unal.reto4_tictactoe.online.BoardMapper;
import co.edu.unal.reto4_tictactoe.online.FirebaseAuthProvider;
import co.edu.unal.reto4_tictactoe.online.FirebaseMatchRepository;
import co.edu.unal.reto4_tictactoe.online.GameLifecycle;
import co.edu.unal.reto4_tictactoe.online.ListenerHandle;
import co.edu.unal.reto4_tictactoe.online.MatchRepository;
import co.edu.unal.reto4_tictactoe.online.MatchState;
import co.edu.unal.reto4_tictactoe.online.MoveCallback;

/**
 * Online match screen (Modo_Online). Renders a single match <strong>authoritatively</strong> from
 * the cloud and manages the real-time listener lifecycle (design "Components and Interfaces" §4,
 * §Lifecycle, §Reutilización de la lógica pura).
 *
 * <p><strong>Authoritative render (Req 20.1-20.3):</strong> this Activity never advances game state
 * locally. It holds a render-only {@link TicTacToeGame} whose board is <em>replaced</em> from the
 * authoritative {@link MatchState} on every {@code observeMatch} callback via
 * {@link BoardMapper#applyToGame(TicTacToeGame, String)} + {@link BoardView#invalidate()}. The
 * status text (turn / result) is derived from the same remote snapshot. Because every render comes
 * from the latest remote snapshot, recreation (rotation) and reconnection re-synchronise for free:
 * the first callback after re-attaching {@code observeMatch} restores the true state and any
 * unconfirmed local assumption is discarded (Req 16.3, 23.2).
 *
 * <p><strong>Listener lifecycle (Req 17.1-17.3):</strong> {@link #observeMatch} is attached in
 * {@link #onStart()} and the {@link ListenerHandle} is detached in {@link #onStop()}; presence is
 * registered in {@code onStart} so {@code onDisconnect} handling (installed by the repository)
 * reflects a transient drop without destroying an active {@code PLAYING} match.
 *
 * <p><strong>Mark assignment (design Decisión 5):</strong> Anfitrión = {@code 'X'} (moves first),
 * Invitado = {@code 'O'}. This device's mark is {@code X} when {@code myUid == hostUid} and
 * {@code O} when {@code myUid == guestUid}; it is used to gate input and to phrase the status.
 *
 * <p><strong>Move submission (task 10.3, Req 14.1-14.4, 23.6, 5.3, 16.1):</strong> a valid tap is
 * first passed through the reused pure {@link MoveGate#shouldApply(char, boolean, boolean)} as a
 * <em>UX gate only</em> — it reads the <em>locally rendered</em> board cell to avoid firing an
 * obviously illegal request, but it never recomputes game rules or mutates the local board. The
 * cloud remains authoritative: the move is sent via {@link MatchRepository#submitMove} and the
 * local board is only ever updated by the next {@code observeMatch} render (Req 16.3, 20.x). The
 * {@link MoveCallback} outcomes surface feedback without touching local state —
 * {@code onApplied} is silent (the authoritative listener re-renders), {@code onRejected} shows an
 * "invalid move / not your turn" message, and {@code onError} shows a connection-error message.
 *
 * <p><strong>End / abandon (task 10.3, Req 18.1, 19.1-19.3, 23.5):</strong> {@code renderFromState}
 * already phrases the {@code FINISHED} (won/lost/tie) and {@code ABANDONED} (opponent left) status.
 * Once either terminal state is reached, input stays blocked ({@link #isMyTurn(MatchState)} is
 * {@code false} for both) and the leave button relabels to "Volver al Lobby" to make it clear the
 * game is over and the only action left is to return. The leave button always calls
 * {@link MatchRepository#leaveMatch} and then {@link #finish()} to go back to the Lobby; the match
 * listener is detached in {@link #onStop()} on the way out (Req 19.2).
 */
public class OnlineGameActivity extends AppCompatActivity {

    /**
     * Intent extra carrying the match id this screen should observe. Referenced by
     * {@code LobbyActivity} (task 9.2) when launching this Activity for the Anfitrión/Invitado.
     */
    public static final String EXTRA_MATCH_ID = "co.edu.unal.reto4_tictactoe.extra.MATCH_ID";

    /** Cloud abstraction (RTDB behind the interface); never imports Firebase here. */
    private MatchRepository repository;

    /** Identifies this device's stable {@code uid} to know which mark it plays. */
    private AuthProvider auth;

    /** Render-only board model; its state is replaced from each authoritative snapshot. */
    private TicTacToeGame game;

    /** Custom view that renders {@link #game} and reports candidate cell taps. */
    private BoardView boardView;

    /** Status / turn indicator text. */
    private TextView statusText;

    /** Leave control; actual wiring (leaveMatch + navigate) is task 10.3. */
    private MaterialButton leaveButton;

    /** The match being observed, read from {@link #EXTRA_MATCH_ID}. */
    private String matchId;

    /** This device's stable uid once signed in; {@code null} until auth completes. */
    private String myUid;

    /** Active match listener; held so it can be detached in {@link #onStop()} (Req 17.2). */
    private ListenerHandle matchHandle;

    /** Last authoritative snapshot received; used to gate input (task 10.3 reads it too). */
    private MatchState lastState;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_online_game);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        statusText = findViewById(R.id.online_status);
        boardView = findViewById(R.id.board_view);
        leaveButton = findViewById(R.id.online_leave);

        matchId = getIntent().getStringExtra(EXTRA_MATCH_ID);

        repository = new FirebaseMatchRepository();
        auth = new FirebaseAuthProvider();

        // Render-only model: the cloud is authoritative; this never advances the game locally.
        game = new TicTacToeGame();
        boardView.setGame(game);

        // Input is gated by the authoritative state (see onCellSelected). Task 10.3 performs the
        // actual submitMove; here taps are accepted by the view but ignored unless it is our turn.
        boardView.setOnMoveListener(this::onCellSelected);

        // Learn this device's uid so we can tell which mark we play (X=host, O=guest). The uid may
        // already be available from the Lobby's earlier sign-in; otherwise we resolve it async.
        myUid = auth.currentUid();
        auth.ensureSignedIn(
                uid -> {
                    myUid = uid;
                    // Re-render with the known uid if a snapshot already arrived.
                    if (lastState != null) {
                        renderFromState(lastState);
                    }
                },
                error -> {
                    // Non-fatal for render; without a uid we simply cannot claim a turn. Task 10.3
                    // surfaces connection errors to the user.
                });

        // Leave button: wiring (leaveMatch + navigate back to the Lobby) is task 10.3.
        leaveButton.setOnClickListener(v -> onLeaveClicked());
    }

    /**
     * Attaches the authoritative match listener and registers presence (Req 17.1). Every callback
     * re-renders from the remote snapshot, which is what makes reconnection/recreation
     * self-synchronising.
     */
    @Override
    protected void onStart() {
        super.onStart();
        if (matchId == null) {
            return;
        }
        matchHandle = repository.observeMatch(matchId, this::renderFromState);
        repository.registerPresence(matchId);
    }

    /**
     * Detaches the match listener so no callbacks arrive while the screen is not visible, avoiding
     * leaks and stale renders (Req 17.2, 17.3).
     */
    @Override
    protected void onStop() {
        super.onStop();
        if (matchHandle != null) {
            matchHandle.detach();
            matchHandle = null;
        }
    }

    /**
     * Renders the screen <strong>entirely</strong> from the authoritative remote snapshot
     * (Req 20.1-20.3): replaces the render-only board, repaints, and derives the status text and
     * input gating from the match lifecycle, turn and winner. Called on every {@code observeMatch}
     * change, which is why reconnection/recreation re-synchronise automatically (Req 16.3, 23.2).
     *
     * @param state the latest authoritative {@link MatchState}; a {@code null} value is ignored
     */
    private void renderFromState(MatchState state) {
        if (state == null) {
            return;
        }
        lastState = state;

        // Always render the board from the authoritative snapshot.
        BoardMapper.applyToGame(game, state.getBoard());
        boardView.invalidate();

        // Status text + turn/result, phrased from this device's point of view.
        statusText.setText(statusTextFor(state));

        // End-of-game UX (Req 19.1, 23.5): once the match is FINISHED or ABANDONED the game is over
        // and input is already blocked (isMyTurn is false for both); make the exit intent explicit
        // by relabeling the leave control to "Volver al Lobby". While WAITING/PLAYING it stays
        // "Salir". The click handler (onLeaveClicked) is identical in both cases.
        GameLifecycle lifecycle = state.lifecycle();
        boolean over = lifecycle == GameLifecycle.FINISHED || lifecycle == GameLifecycle.ABANDONED;
        leaveButton.setText(over ? R.string.online_back_to_lobby : R.string.online_leave);
    }

    /**
     * Computes the status string for the given authoritative state from this device's perspective.
     *
     * <ul>
     *   <li>{@code WAITING} → "esperando al rival".</li>
     *   <li>{@code PLAYING} → our turn ? "tu turno (marca)" : "turno del rival (marca)".</li>
     *   <li>{@code FINISHED} → map {@code winner} to won/lost/tie using our own mark.</li>
     *   <li>{@code ABANDONED} → "el rival abandonó".</li>
     * </ul>
     */
    private String statusTextFor(MatchState state) {
        GameLifecycle lifecycle = state.lifecycle();
        if (lifecycle == null) {
            return getString(R.string.online_status_waiting);
        }
        switch (lifecycle) {
            case WAITING:
                return getString(R.string.online_status_waiting);
            case PLAYING:
                String turnMark = state.getTurn();
                if (isMyTurn(state)) {
                    return getString(R.string.online_status_your_turn, turnMark);
                }
                return getString(R.string.online_status_opponent_turn, turnMark);
            case FINISHED:
                return finishedStatusText(state);
            case ABANDONED:
                return getString(R.string.online_status_opponent_left);
            default:
                return getString(R.string.online_status_waiting);
        }
    }

    /**
     * Maps a {@code FINISHED} winner code to won/lost/tie text relative to this device's mark.
     * Winner codes follow {@code TicTacToeGame.checkForWinner}: 1 tie, 2 X wins, 3 O wins
     * (Req 14.3). A tie, or an unknown winner before our mark is resolved, falls back to tie text.
     */
    private String finishedStatusText(MatchState state) {
        int winner = state.getWinner();
        if (winner == MatchState.WINNER_TIE) {
            return getString(R.string.online_status_tie);
        }
        String myMark = myMark(state);
        if (myMark == null) {
            // Can't phrase won/lost without knowing our mark; show a neutral tie-like end state.
            return getString(R.string.online_status_tie);
        }
        boolean iWon = (winner == MatchState.WINNER_X && MatchState.MARK_X.equals(myMark))
                || (winner == MatchState.WINNER_O && MatchState.MARK_O.equals(myMark));
        return getString(iWon ? R.string.online_status_you_won : R.string.online_status_you_lost);
    }

    /**
     * This device's mark for the match: {@code "X"} if we are the host, {@code "O"} if we are the
     * guest, or {@code null} if our uid is unknown or we are not a participant.
     */
    @Nullable
    private String myMark(MatchState state) {
        if (myUid == null) {
            return null;
        }
        if (myUid.equals(state.getHostUid())) {
            return MatchState.MARK_X;
        }
        if (myUid.equals(state.getGuestUid())) {
            return MatchState.MARK_O;
        }
        return null;
    }

    /**
     * Whether it is this device's turn to move: the match is {@code PLAYING} and the authoritative
     * {@code turn} equals our mark. Used to gate input (Req 5.2, 12.3) and phrase the status.
     */
    private boolean isMyTurn(MatchState state) {
        if (state.lifecycle() != GameLifecycle.PLAYING) {
            return false;
        }
        String myMark = myMark(state);
        return myMark != null && myMark.equals(state.getTurn());
    }

    /**
     * Board tap handler (Req 5.3, 14.1, 14.2, 14.4, 16.1, 16.3, 23.6). Submits a move through the
     * authoritative cloud; it never advances or mutates the local board.
     *
     * <p>Gating happens in two layers, both read-only on the local state:
     * <ol>
     *   <li><strong>Turn gate</strong> — ignore the tap unless the match is {@code PLAYING} and it
     *       is our turn ({@link #isMyTurn(MatchState)}). This also blocks input in
     *       {@code WAITING/FINISHED/ABANDONED} (Req 5.2, 12.3).</li>
     *   <li><strong>UX gate</strong> — reuse the pure {@link MoveGate#shouldApply(char, boolean, boolean)}
     *       against the <em>locally rendered</em> cell to avoid firing an obviously illegal
     *       request (cell already occupied). The game is in progress and it is our turn here, so
     *       {@code gameOver} and {@code computerTurn} are both {@code false}; the gate therefore
     *       reduces to "is the rendered cell open". This is purely a UX convenience — it does not
     *       recompute game rules, and the cloud re-validates everything in its transaction.</li>
     * </ol>
     *
     * <p>On a move that clears both gates we call {@link MatchRepository#submitMove} and react to
     * its {@link MoveCallback} <em>without changing local state</em>: {@code onApplied} is silent
     * because the next {@code observeMatch} render shows the authoritative board (Req 16.3, 20.x);
     * {@code onRejected} shows an "invalid move / not your turn" toast (Req 14.4, 23.6, 5.3); and
     * {@code onError} shows a connection-error toast (Req 16.1).
     *
     * @param cell the candidate board cell (0-8) reported by {@link BoardView}
     */
    private void onCellSelected(int cell) {
        if (lastState == null || !isMyTurn(lastState)) {
            // Not our turn / not playing (covers WAITING/FINISHED/ABANDONED): block interaction.
            return;
        }

        // UX gate only: read the locally rendered cell. We are PLAYING and it is our turn, so the
        // only thing left for MoveGate to veto is an already-occupied cell. No rules recomputed and
        // no local mutation; the cloud is authoritative.
        char rendered = game.getBoardState(cell);
        boolean gameOver = false;      // isMyTurn(...) == true implies the match is PLAYING.
        boolean computerTurn = false;  // Online has no computer turn; it is our turn here.
        if (!MoveGate.shouldApply(rendered, gameOver, computerTurn)) {
            // Cell already taken in the rendered board: ignore the tap (no request, no feedback).
            return;
        }

        repository.submitMove(matchId, cell, new MoveCallback() {
            @Override
            public void onApplied() {
                // No local state change: the authoritative observeMatch render reflects the move
                // (Req 16.3, 20.x). Nothing to do here.
            }

            @Override
            public void onRejected() {
                // The cloud rejected an illegal attempt (not our turn, cell taken, or not PLAYING)
                // (Req 14.4, 23.6, 5.3). Inform the user; do NOT change local state — the next
                // authoritative render stays the source of truth.
                int message = isMyTurn(lastState)
                        ? R.string.online_move_invalid
                        : R.string.online_move_not_your_turn;
                Toast.makeText(OnlineGameActivity.this, message, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(Throwable error) {
                // Connection/write error (Req 16.1): surface it; local state is untouched and the
                // app stays consistent (the transaction applied nothing).
                Toast.makeText(
                        OnlineGameActivity.this,
                        R.string.online_error_connection,
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Leave-control handler (Req 18.1, 19.1, 19.2). Marks the match abandoned (if {@code PLAYING})
     * or removes it (if a {@code WAITING} match owned by the Anfitrión) via
     * {@link MatchRepository#leaveMatch}, then {@link #finish()} to return to the Lobby. When the
     * game has already ended ({@code FINISHED}/{@code ABANDONED}) this button reads "Volver al
     * Lobby" and simply navigates back. The active match listener is detached in {@link #onStop()}
     * as the Activity leaves the foreground (Req 19.2).
     */
    private void onLeaveClicked() {
        if (matchId != null) {
            repository.leaveMatch(matchId);
        }
        finish();
    }
}
