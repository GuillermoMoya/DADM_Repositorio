package co.edu.unal.reto4_tictactoe.online;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * {@link MatchRepository} backed by the Firebase Realtime Database (RTDB), the authoritative
 * {@code Servicio_Nube} for the Modo_Online (design "Components and Interfaces" §2, "Data Models").
 *
 * <p><strong>This is the only class in the feature allowed to import {@code com.google.firebase}
 * packages.</strong> Every other class (Activities, pure logic, callbacks) talks to the cloud
 * exclusively through the {@link MatchRepository} abstraction, which keeps the provider swappable
 * for tests — the in-memory {@code FakeMatchRepository} stands in for this class without a network
 * (Req 6.1, 20.4). Authentication is likewise isolated behind {@link AuthProvider} /
 * {@link FirebaseAuthProvider}.
 *
 * <h2>RTDB layout (design §Modelo RTDB)</h2>
 * Two subtrees hang off the configured root:
 * <ul>
 *   <li>{@code /matches/<matchId>} — the authoritative {@link MatchState} document.</li>
 *   <li>{@code /lobby/<matchId>} — a lightweight {@link MatchSummary} index of joinable
 *       {@code WAITING} matches, so the Lobby can observe a tiny node without downloading games in
 *       progress (Req 8.2, 8.3).</li>
 * </ul>
 *
 * <h2>Serialization note (tasks 8.1 → 8.2 → 8.3)</h2>
 * This class is built incrementally across three sub-tasks that edit the <em>same</em> file in
 * strict order:
 * <ul>
 *   <li><strong>8.1 (this task):</strong> {@link #createMatch(ResultCallback)},
 *       {@link #observeOpenMatches(Listener)} and {@link #observeMatch(String, Listener)}, plus the
 *       scaffolding — path constants, the snapshot→{@link MatchState}/{@link MatchSummary} mapping
 *       helpers and the {@link ValueEventListenerHandle} — so the remaining tasks extend without
 *       rework.</li>
 *   <li><strong>8.2:</strong> {@link #joinMatch(String, JoinCallback)} and
 *       {@link #submitMove(String, int, MoveCallback)} as RTDB {@code runTransaction} calls reusing
 *       {@link OnlineJoinEngine} / {@link OnlineMoveEngine}.</li>
 *   <li><strong>8.3:</strong> {@link #leaveMatch(String)} and {@link #registerPresence(String)}
 *       with {@code onDisconnect} handling.</li>
 * </ul>
 * With 8.3 complete the class is fully implemented; no method throws
 * {@link UnsupportedOperationException}.
 *
 * <p>Maps requirements: 3.1, 3.2, 6.1, 6.3, 8.1, 8.2, 8.3, 9.1, 9.2, 9.3, 15.3, 17.1, 17.2, 18.1,
 * 18.2, 18.3, 18.4, 19.1, 19.2, 19.3, 20.4, 23.2, 23.4, 23.5.
 */
public final class FirebaseMatchRepository implements MatchRepository {

    /** Logcat tag for the online match repository (debug only; never logs tokens/credentials). */
    private static final String TAG = "OnlineMatchRepo";

    /** RTDB subtree holding the authoritative {@link MatchState} documents. */
    static final String NODE_MATCHES = "matches";

    /** RTDB subtree holding the lightweight {@link MatchSummary} Lobby index. */
    static final String NODE_LOBBY = "lobby";

    /** Identifies the Player (anonymous {@code uid}); never imports Firebase Auth types directly. */
    private final AuthProvider auth;

    /** Root of the RTDB tree; {@code /matches} and {@code /lobby} hang off it. */
    private final DatabaseReference root;

    /**
     * Convenience constructor using the default Firebase instances: the default RTDB root and a
     * {@link FirebaseAuthProvider} over the default {@code FirebaseAuth} (anonymous sign-in).
     */
    public FirebaseMatchRepository() {
        this(new FirebaseAuthProvider(), FirebaseDatabase.getInstance().getReference());
    }

    /**
     * Creates a repository over an explicit {@link FirebaseDatabase} (its root reference is used).
     *
     * @param auth     the player-identity provider; must be non-{@code null}
     * @param database the Firebase database whose root reference backs {@code /matches} and
     *                 {@code /lobby}; must be non-{@code null}
     */
    public FirebaseMatchRepository(AuthProvider auth, FirebaseDatabase database) {
        this(auth, requireNonNull(database, "database").getReference());
    }

    /**
     * Creates a repository over an explicit root {@link DatabaseReference}, the most testable form.
     *
     * @param auth the player-identity provider; must be non-{@code null}
     * @param root the RTDB root under which {@code /matches} and {@code /lobby} live; must be
     *             non-{@code null}
     */
    public FirebaseMatchRepository(AuthProvider auth, DatabaseReference root) {
        this.auth = requireNonNull(auth, "auth");
        this.root = requireNonNull(root, "root");
    }

    // --- Lobby --------------------------------------------------------------------------------

    @Override
    public ListenerHandle observeOpenMatches(Listener<List<MatchSummary>> listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener must be non-null");
        }
        final DatabaseReference lobbyRef = lobbyRef();
        ValueEventListener vel = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                // Map every child under /lobby to a MatchSummary; absent node → empty list (Req 3.3).
                listener.onChanged(toSummaries(snapshot));
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // Observation cancelled (e.g. permission denied); surface an empty Lobby so the UI
                // can show its empty state rather than stale data.
                listener.onChanged(new ArrayList<>());
            }
        };
        lobbyRef.addValueEventListener(vel);
        return new ValueEventListenerHandle(lobbyRef, vel);
    }

    // --- Creation / join ----------------------------------------------------------------------

    @Override
    public void createMatch(ResultCallback<String> onMatchId) {
        if (onMatchId == null) {
            throw new IllegalArgumentException("onMatchId must be non-null");
        }
        // Ensure an identified Player before writing (Req 2.1, 22.2); the uid is the hostUid.
        auth.ensureSignedIn(
                hostUid -> writeNewMatch(hostUid, onMatchId),
                onMatchId::onError);
    }

    /**
     * Writes a fresh {@code WAITING} match plus its Lobby entry once the host {@code uid} is known.
     * The match id is the {@code push()} key under {@code /matches} (Req 9.1, 9.3); the host plays
     * {@code 'X'} and moves first (Req 11.1, 11.3). The {@code /matches} and {@code /lobby} writes
     * are batched into one multi-location update so they land atomically.
     */
    private void writeNewMatch(String hostUid, ResultCallback<String> onMatchId) {
        if (hostUid == null) {
            onMatchId.onError(new IllegalStateException("sign-in reported a null uid"));
            return;
        }
        final DatabaseReference matchRef = matchesRef().push();
        final String matchId = matchRef.getKey();
        if (matchId == null) {
            onMatchId.onError(new IllegalStateException("RTDB push() returned a null key"));
            return;
        }

        long now = System.currentTimeMillis();

        MatchState state = new MatchState();
        state.setHostUid(hostUid);
        state.setGuestUid(null);
        state.setBoard(MatchState.EMPTY_BOARD);
        state.setTurn(MatchState.MARK_X);
        state.setLifecycle(GameLifecycle.WAITING);
        state.setWinner(MatchState.WINNER_NONE);
        state.setCreatedAt(now);
        state.setUpdatedAt(now);
        Map<String, Boolean> presence = new HashMap<>();
        presence.put(hostUid, true);
        state.setPresence(presence);
        // matchId lives in the snapshot key, not inside the document (Req 9.3); leave it unset.

        MatchSummary summary = new MatchSummary();
        summary.setHostUid(hostUid);
        summary.setCreatedAt(now);

        Map<String, Object> update = new HashMap<>();
        update.put(NODE_MATCHES + "/" + matchId, state);
        update.put(NODE_LOBBY + "/" + matchId, summary);

        root.updateChildren(update, (error, ref) -> {
            if (error != null) {
                onMatchId.onError(error.toException());
            } else {
                onMatchId.onSuccess(matchId);
            }
        });
    }

    @Override
    public void joinMatch(String matchId, JoinCallback callback) {
        if (callback == null) {
            throw new IllegalArgumentException("callback must be non-null");
        }
        final DatabaseReference matchRef = matchRef(matchId);
        // Ensure an identified Player before the transaction so currentUid() is available inside
        // the handler (Req 2.1, 22.2). The uid is the candidate guestUid.
        auth.ensureSignedIn(
                requesterUid -> {
                    Log.d(TAG, "joinMatch: signed in, matchId=" + matchId
                            + " uid=" + shortUid(requesterUid));
                    runJoinTransaction(matchRef, requesterUid, callback);
                },
                callback::onError);
    }

    /**
     * Claims the Invitado slot of {@code /matches/<id>} with an RTDB {@code runTransaction}, reusing
     * the pure {@link OnlineJoinEngine} so the exact same rule runs here and in
     * {@code FakeMatchRepository} (design §Concurrency and Integrity → "Unión (claim del slot de
     * Invitado)"). RTDB auto-retries the transaction under contention, so of two concurrent joins
     * exactly one commits and the loser re-evaluates against the now-occupied slot and aborts as
     * unavailable (Req 10.2, 23.3).
     *
     * <p>The {@code unavailable} flag distinguishes a normal "slot already taken" abort — which
     * maps to {@link JoinCallback#onUnavailable()} — from a genuine {@link DatabaseError}, which
     * maps to {@link JoinCallback#onError(Throwable)} (Req 16.2, 23.3). On a committed claim the
     * {@code /lobby/<id>} entry is removed since the match is no longer joinable (Req 8.3, 19.3).
     */
    private void runJoinTransaction(DatabaseReference matchRef, String requesterUid,
                                    JoinCallback callback) {
        if (requesterUid == null) {
            callback.onError(new IllegalStateException("sign-in reported a null uid"));
            return;
        }
        final String matchId = matchRef.getKey();
        // Flag shared between doTransaction and onComplete to tell "unavailable abort" apart from
        // a real error (both surface as "not committed" with a null DatabaseError).
        final AtomicBoolean unavailable = new AtomicBoolean(false);
        // Counts doTransaction invocations. The RTDB SDK runs the handler the FIRST time against a
        // LOCAL estimate; a client with no /matches/<id> in cache (the guest only observes /lobby)
        // sees getValue()==null on that first pass. Aborting on that null makes the SDK give up
        // without ever consulting the server (committed=false, error=null → onUnavailable), so a
        // legitimate join never happens. We instead return success(mutableData) on the first null
        // to force the SDK to fetch the server value and re-run; only a SECOND null (after the
        // fetch) means the match truly does not exist → abort as unavailable.
        final int[] passes = {0};

        matchRef.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData mutableData) {
                int pass = passes[0]++;
                MatchState current = mutableData.getValue(MatchState.class);
                Log.d(TAG, "join doTransaction pass=" + pass
                        + " currentNull=" + (current == null)
                        + " status=" + (current != null ? current.getStatus() : "-")
                        + " guestPresent=" + (current != null && current.getGuestUid() != null));
                if (current == null) {
                    if (pass == 0) {
                        // First pass against an empty local estimate (guest's cold cache):
                        // force the SDK to fetch the server value and re-invoke doTransaction.
                        return Transaction.success(mutableData);
                    }
                    // Still absent after consulting the server → genuinely not available.
                    unavailable.set(true);
                    return Transaction.abort();
                }
                OnlineJoinEngine.Result result = OnlineJoinEngine.evaluate(current, requesterUid);
                if (!result.isJoined()) {
                    // Slot already taken or not WAITING: expected competing-join outcome.
                    unavailable.set(true);
                    return Transaction.abort();
                }
                // Claim committed: update ONLY the fields that change when joining, preserving the
                // rest of the document (in particular presence/<host>, which the presence/$uid rule
                // forbids the guest from rewriting). Writing the whole document back with
                // setValue(next) re-sent presence/<host> under the guest's auth.uid and the
                // ".validate": "$uid === auth.uid" rejected it as Permission denied (-3). Updating
                // only guestUid/status/turn/updatedAt leaves the presence subtree untouched, so that
                // rule is never evaluated for the host's node. This stays a single atomic
                // transaction: we mutate several children of mutableData inside the same
                // doTransaction instead of replacing the whole node.
                MatchState next = result.getNewState(); // engine decision: guestUid, status=PLAYING, turn=X
                mutableData.child("guestUid").setValue(next.getGuestUid());
                mutableData.child("status").setValue(next.getStatus());
                mutableData.child("turn").setValue(next.getTurn());
                mutableData.child("updatedAt").setValue(System.currentTimeMillis());
                return Transaction.success(mutableData);
            }

            @Override
            public void onComplete(DatabaseError error, boolean committed,
                                   DataSnapshot currentData) {
                Log.d(TAG, "join onComplete committed=" + committed
                        + " error=" + (error != null ? (error.getCode() + ":" + error.getMessage())
                                                      : "none"));
                if (error != null) {
                    // Permission/connection/write failure (e.g. code -3 PERMISSION_DENIED).
                    Log.w(TAG, "join error code=" + error.getCode()
                            + " msg=" + error.getMessage());
                    callback.onError(error.toException());
                    return;
                }
                if (committed) {
                    // The match is now PLAYING and no longer joinable: drop its Lobby entry
                    // (Req 8.3, 19.3), then report success regardless of the removal outcome.
                    if (matchId != null) {
                        lobbyRef().child(matchId).removeValue();
                    }
                    callback.onJoined();
                } else {
                    // Not committed without an error → the slot was unavailable (Req 16.2, 23.3).
                    callback.onUnavailable();
                }
            }
        });
    }

    // --- Match in progress --------------------------------------------------------------------

    @Override
    public ListenerHandle observeMatch(String matchId, Listener<MatchState> listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener must be non-null");
        }
        final DatabaseReference matchRef = matchRef(matchId);
        ValueEventListener vel = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                MatchState state = toMatchState(snapshot);
                if (state != null) {
                    // Absent node (match removed) delivers nothing; present node re-syncs the UI
                    // from the authoritative state on every change (Req 13.1, 17.2).
                    listener.onChanged(state);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // Observation cancelled; nothing authoritative to deliver.
            }
        };
        matchRef.addValueEventListener(vel);
        return new ValueEventListenerHandle(matchRef, vel);
    }

    @Override
    public void submitMove(String matchId, int cell, MoveCallback callback) {
        if (callback == null) {
            throw new IllegalArgumentException("callback must be non-null");
        }
        final DatabaseReference matchRef = matchRef(matchId);
        // Ensure an identified Player before the transaction so currentUid() is available inside
        // the handler (Req 5.1, 22.2); the uid identifies the moving participant.
        auth.ensureSignedIn(
                requesterUid -> runMoveTransaction(matchRef, requesterUid, cell, callback),
                callback::onError);
    }

    /**
     * Applies a move to {@code /matches/<id>} with an RTDB {@code runTransaction}, reusing the pure
     * {@link OnlineMoveEngine} so the exact same guard runs here and in {@code FakeMatchRepository}
     * (design §Concurrency and Integrity → "Aplicar un movimiento"). RTDB auto-retries under
     * contention, so a second concurrent move re-evaluates against the updated board/turn and is
     * rejected — the behavior Property 1 describes (Req 15.2, 23.1, 23.6).
     *
     * <p>The {@code rejected} flag distinguishes a normal illegal-move abort — which maps to
     * {@link MoveCallback#onRejected()} — from a genuine {@link DatabaseError}, which maps to
     * {@link MoveCallback#onError(Throwable)} (Req 14.4, 16.1, 23.6).
     */
    private void runMoveTransaction(DatabaseReference matchRef, String requesterUid, int cell,
                                    MoveCallback callback) {
        if (requesterUid == null) {
            callback.onError(new IllegalStateException("sign-in reported a null uid"));
            return;
        }
        // Flag shared between doTransaction and onComplete to tell a rejected (illegal) move apart
        // from a real error (both surface as "not committed" with a null DatabaseError).
        final AtomicBoolean rejected = new AtomicBoolean(false);
        // Defensive mirror of the join fix: the first doTransaction pass runs against a local
        // estimate that may be null on a cold cache. OnlineGameActivity usually warms the cache via
        // observeMatch, but if it is cold, aborting on the first null would wrongly reject a legal
        // move. First null → success(mutableData) to force a server fetch and re-run; a SECOND null
        // (after the fetch) means the match truly does not exist → reject.
        final int[] passes = {0};

        matchRef.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData mutableData) {
                int pass = passes[0]++;
                MatchState current = mutableData.getValue(MatchState.class);
                Log.d(TAG, "move doTransaction pass=" + pass
                        + " currentNull=" + (current == null));
                if (current == null) {
                    if (pass == 0) {
                        // First pass against an empty local estimate (cold cache):
                        // force the SDK to fetch the server value and re-invoke doTransaction.
                        return Transaction.success(mutableData);
                    }
                    // Still absent after consulting the server → nothing to move on → reject.
                    rejected.set(true);
                    return Transaction.abort();
                }
                OnlineMoveEngine.Result result =
                        OnlineMoveEngine.evaluate(current, requesterUid, cell);
                if (!result.isApplied()) {
                    // Illegal against the authoritative state: leave it unchanged (Req 14.4, 23.6).
                    rejected.set(true);
                    return Transaction.abort();
                }
                // Legal: write the engine's resulting board/turn/status/winner, stamping updatedAt.
                MatchState next = result.getNewState();
                next.setUpdatedAt(System.currentTimeMillis());
                mutableData.setValue(next);
                return Transaction.success(mutableData);
            }

            @Override
            public void onComplete(DatabaseError error, boolean committed,
                                   DataSnapshot currentData) {
                Log.d(TAG, "move onComplete committed=" + committed
                        + " error=" + (error != null ? (error.getCode() + ":" + error.getMessage())
                                                      : "none"));
                if (error != null) {
                    Log.w(TAG, "move error code=" + error.getCode()
                            + " msg=" + error.getMessage());
                    callback.onError(error.toException());
                } else if (committed) {
                    callback.onApplied();
                } else {
                    // Not committed without an error → the move was illegal (Req 14.4, 23.6).
                    callback.onRejected();
                }
            }
        });
    }

    // --- Leaving / lifecycle ------------------------------------------------------------------

    @Override
    public void leaveMatch(String matchId) {
        final DatabaseReference matchRef = matchRef(matchId);
        // Ensure an identified Player before the transaction so we can tell host from guest inside
        // the handler (Req 22.2). The uid is the one performing the explicit, voluntary exit.
        auth.ensureSignedIn(
                requesterUid -> runLeaveTransaction(matchRef, requesterUid),
                error -> { /* Not signed in: nothing to leave; mirror the Fake's silent no-op. */ });
    }

    /**
     * Applies the explicit, voluntary exit decision to {@code /matches/<id>} with an RTDB
     * {@code runTransaction}, mirroring {@code FakeMatchRepository.leaveMatch} exactly so both
     * backends behave identically (design §Lifecycle "Salida y limpieza"). The decision table is:
     *
     * <ul>
     *   <li><strong>PLAYING</strong> → set {@code status = ABANDONED}, stamp {@code updatedAt}: this
     *       is the definitive abandonment of an active game, distinct from a transient disconnect
     *       (Req 18.1, 23.5). The match document is kept (not removed) so the remaining Player's
     *       listener observes {@code ABANDONED} and can report "el oponente salió".</li>
     *   <li><strong>WAITING owned by the host</strong> → remove the match entirely
     *       ({@code setValue(null)}): an unfilled match the host walks away from disappears from the
     *       Lobby (Req 18.3, 19.2).</li>
     *   <li><strong>otherwise</strong> (FINISHED/ABANDONED, or a non-host on WAITING) → no-op.</li>
     * </ul>
     *
     * In both mutating branches the {@code /lobby/<id>} entry is removed in {@code onComplete} since
     * an abandoned/removed match is never joinable (Req 8.3, 19.3). The removal is best-effort and
     * its outcome is not surfaced ({@link MatchRepository#leaveMatch} returns {@code void}).
     */
    private void runLeaveTransaction(DatabaseReference matchRef, String requesterUid) {
        if (requesterUid == null) {
            // Sign-in reported a null uid: cannot decide host vs guest, so do nothing (no-op).
            return;
        }
        final String matchId = matchRef.getKey();
        // Flag shared with onComplete: true when the transaction left the match
        // abandoned-or-removed, so the Lobby entry must be dropped. A plain no-op leaves it false.
        final AtomicBoolean clearLobby = new AtomicBoolean(false);

        matchRef.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData mutableData) {
                MatchState current = mutableData.getValue(MatchState.class);
                if (current == null) {
                    // Unknown/already-removed match: nothing to leave.
                    return Transaction.success(mutableData);
                }
                GameLifecycle lifecycle = current.lifecycle();
                if (lifecycle == GameLifecycle.PLAYING) {
                    // Explicit exit of an active game → ABANDONED (definitive, not a disconnect).
                    current.setLifecycle(GameLifecycle.ABANDONED);
                    current.setUpdatedAt(System.currentTimeMillis());
                    mutableData.setValue(current);
                    clearLobby.set(true);
                    return Transaction.success(mutableData);
                }
                if (lifecycle == GameLifecycle.WAITING
                        && requesterUid.equals(current.getHostUid())) {
                    // Orphaned WAITING owned by the host → remove the whole document.
                    mutableData.setValue(null);
                    clearLobby.set(true);
                    return Transaction.success(mutableData);
                }
                // FINISHED/ABANDONED, or a non-host leaving WAITING: leave the document unchanged.
                return Transaction.success(mutableData);
            }

            @Override
            public void onComplete(DatabaseError error, boolean committed,
                                   DataSnapshot currentData) {
                // Drop the Lobby index entry for an abandoned/removed match (best-effort).
                if (error == null && committed && clearLobby.get() && matchId != null) {
                    lobbyRef().child(matchId).removeValue();
                }
            }
        });
    }

    @Override
    public void registerPresence(String matchId) {
        final DatabaseReference matchRef = matchRef(matchId);
        // Ensure an identified Player so presence is keyed by a stable uid (Req 22.2); the uid is
        // the participant whose presence/onDisconnect we are wiring up.
        auth.ensureSignedIn(
                requesterUid -> wirePresence(matchRef, requesterUid),
                error -> { /* Not signed in: no presence to register; mirror the Fake's no-op. */ });
    }

    /**
     * Wires presence and the {@code onDisconnect} handlers for {@code requesterUid} on
     * {@code /matches/<id>}, distinguishing a <em>transient disconnect</em> from a
     * <em>definitive abandonment</em> (design §Reconexión + §Presencia / {@code onDisconnect};
     * mirrors {@code FakeMatchRepository.simulateDisconnect}).
     *
     * <p><strong>Transient disconnect (the common case).</strong> We mark the participant present
     * now ({@code presence/<uid> = true}) and schedule {@code onDisconnect().setValue(false)} on
     * that same presence node. When the client drops, RTDB flips only the presence flag to
     * {@code false}; the {@code PLAYING} match document is <em>preserved</em> so the Player can
     * reconnect and re-sync from the authoritative state (Req 18.2, 23.2, 23.4). We never schedule
     * an {@code onDisconnect} that abandons or removes a {@code PLAYING} match — abandonment is only
     * ever the explicit {@link #leaveMatch(String)} path (Req 18.1).
     *
     * <p><strong>Orphaned WAITING cleanup.</strong> Only when the match is {@code WAITING}, owned by
     * this {@code requesterUid} as host, and still has no guest, we additionally schedule
     * {@code onDisconnect().removeValue()} on {@code /matches/<id>} and {@code /lobby/<id>} so a host
     * who leaves before a guest arrives makes the match disappear from the Lobby (Req 18.3, 19.2,
     * 23.4). Because this is gated on {@code WAITING} with no guest, it can never remove a match that
     * has advanced to {@code PLAYING}: once a guest joins the match is {@code PLAYING}, so this
     * cleanup is not armed and the transient-disconnect branch above governs instead.
     *
     * <p>We read the current state once (a single-value listener) to decide which {@code onDisconnect}
     * handlers to arm; the authoritative turn/board logic is unaffected. {@code onDisconnect} and
     * presence behavior are only fully exercisable on a real device/emulator with a live RTDB
     * connection, so they are validated manually rather than by the JVM property tests
     * (design §Testing Strategy).
     */
    private void wirePresence(DatabaseReference matchRef, String requesterUid) {
        if (requesterUid == null) {
            return; // Sign-in reported a null uid: nothing to key presence by.
        }
        final DatabaseReference presenceRef = matchRef.child("presence").child(requesterUid);
        final String matchId = matchRef.getKey();

        // Mark present immediately and reflect absence (not abandonment) on a transient disconnect.
        presenceRef.setValue(true);
        presenceRef.onDisconnect().setValue(false);

        // Decide, from the current authoritative state, whether to also arm orphaned-WAITING cleanup.
        matchRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                MatchState current = toMatchState(snapshot);
                if (current == null) {
                    return; // Match gone; nothing to arm.
                }
                boolean orphanWaitingHost =
                        current.lifecycle() == GameLifecycle.WAITING
                                && requesterUid.equals(current.getHostUid())
                                && current.getGuestUid() == null;
                if (orphanWaitingHost) {
                    // Host of an unfilled WAITING match: on disconnect, remove the match and its
                    // Lobby entry so it does not linger as a dead entry (Req 18.3, 19.2, 23.4).
                    // Safe because this is gated on WAITING-with-no-guest and therefore never
                    // targets a PLAYING match.
                    matchRef.onDisconnect().removeValue();
                    if (matchId != null) {
                        lobbyRef().child(matchId).onDisconnect().removeValue();
                    }
                }
                // PLAYING (or guest, or non-WAITING): leave only the presence=false handler armed so
                // a disconnect reflects absence without destroying the active match (Req 18.2, 23.4).
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // Could not read the state (e.g. permission); keep only the presence=false handler.
            }
        });
    }

    // --- RTDB references ----------------------------------------------------------------------

    /** @return the {@code /matches} subtree reference. */
    private DatabaseReference matchesRef() {
        return root.child(NODE_MATCHES);
    }

    /** @return the {@code /lobby} subtree reference. */
    private DatabaseReference lobbyRef() {
        return root.child(NODE_LOBBY);
    }

    /** @return the {@code /matches/<matchId>} document reference. */
    private DatabaseReference matchRef(String matchId) {
        if (matchId == null) {
            throw new IllegalArgumentException("matchId must be non-null");
        }
        return matchesRef().child(matchId);
    }

    // --- Snapshot mapping (single seam; reused by 8.2/8.3) ------------------------------------

    /**
     * Maps a {@code /lobby} snapshot to an ordered {@link MatchSummary} list, setting each
     * {@code matchId} from its child key (Req 9.3). A missing/empty node maps to an empty list so
     * the Lobby can render its empty state (Req 3.3).
     *
     * @param lobbySnapshot the snapshot of the {@code /lobby} node
     * @return a never-{@code null} list of joinable summaries, ordered by {@code createdAt}
     */
    static List<MatchSummary> toSummaries(DataSnapshot lobbySnapshot) {
        List<MatchSummary> out = new ArrayList<>();
        if (lobbySnapshot == null || !lobbySnapshot.exists()) {
            return out;
        }
        for (DataSnapshot child : lobbySnapshot.getChildren()) {
            MatchSummary summary = child.getValue(MatchSummary.class);
            if (summary == null) {
                summary = new MatchSummary();
            }
            summary.setMatchId(child.getKey()); // id lives in the key, not the document (Req 9.3).
            out.add(summary);
        }
        // Lobby ordering: by creation time, then id for stable ties (Decisión 4).
        out.sort((a, b) -> {
            int byTime = Long.compare(a.getCreatedAt(), b.getCreatedAt());
            if (byTime != 0) {
                return byTime;
            }
            String ka = a.getMatchId() == null ? "" : a.getMatchId();
            String kb = b.getMatchId() == null ? "" : b.getMatchId();
            return ka.compareTo(kb);
        });
        return out;
    }

    /**
     * Maps a {@code /matches/<id>} snapshot to a {@link MatchState}, setting {@code matchId} from
     * the snapshot key (Req 9.1, 9.3).
     *
     * @param matchSnapshot the snapshot of a single match document
     * @return the deserialized {@link MatchState}, or {@code null} if the node is absent
     */
    static MatchState toMatchState(DataSnapshot matchSnapshot) {
        if (matchSnapshot == null || !matchSnapshot.exists()) {
            return null;
        }
        MatchState state = matchSnapshot.getValue(MatchState.class);
        if (state == null) {
            return null;
        }
        state.setMatchId(matchSnapshot.getKey());
        return state;
    }

    /**
     * Returns a short, non-sensitive prefix of a uid for debug logs (a uid is not a secret, but we
     * keep logs minimal). Never used for tokens or credentials. {@code null} maps to {@code "-"}.
     */
    private static String shortUid(String uid) {
        if (uid == null) {
            return "-";
        }
        return uid.substring(0, Math.min(6, uid.length()));
    }

    private static <T> T requireNonNull(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must be non-null");
        }
        return value;
    }

    /**
     * {@link ListenerHandle} that removes a {@link ValueEventListener} from its reference. Idempotent:
     * {@link #detach()} may be called more than once; subsequent calls are no-ops (Req 17.2).
     */
    private static final class ValueEventListenerHandle implements ListenerHandle {

        private final DatabaseReference ref;
        private ValueEventListener listener;

        ValueEventListenerHandle(DatabaseReference ref, ValueEventListener listener) {
            this.ref = ref;
            this.listener = listener;
        }

        @Override
        public void detach() {
            if (listener != null) {
                ref.removeEventListener(listener);
                listener = null; // Idempotent: a second detach() is a no-op.
            }
        }
    }
}
