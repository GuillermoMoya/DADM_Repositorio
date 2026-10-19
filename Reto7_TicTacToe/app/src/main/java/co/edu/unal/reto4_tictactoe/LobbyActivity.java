package co.edu.unal.reto4_tictactoe;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

import co.edu.unal.reto4_tictactoe.online.FirebaseMatchRepository;
import co.edu.unal.reto4_tictactoe.online.JoinCallback;
import co.edu.unal.reto4_tictactoe.online.ListenerHandle;
import co.edu.unal.reto4_tictactoe.online.MatchRepository;
import co.edu.unal.reto4_tictactoe.online.MatchSummary;
import co.edu.unal.reto4_tictactoe.online.ResultCallback;

/**
 * Lobby screen of the Modo_Online: shows the real-time list of {@code WAITING} matches and the
 * entry points to create a match (as Anfitrión) or join one (as Invitado).
 *
 * <p>Design mapping ("Components and Interfaces" §3 LobbyActivity, §Lifecycle): the Activity holds
 * a {@link MatchRepository} and, while in the active state, observes the shared global lobby via
 * {@link MatchRepository#observeOpenMatches(co.edu.unal.reto4_tictactoe.online.Listener)}. The
 * returned {@link ListenerHandle} is attached in {@link #onStart()} and detached in
 * {@link #onStop()} so observation stops when the screen is not visible, avoiding leaks and
 * reads after the view is gone (Req 17.1, 17.2).
 *
 * <p>Interactions:
 * <ul>
 *   <li><strong>Crear partida</strong> → {@link MatchRepository#createMatch(ResultCallback)}; on
 *       success navigates to the online game screen with the new {@code matchId} as host
 *       (Req 4.1, 7.1).</li>
 *   <li><strong>Unirse</strong> (per list item) → {@link MatchRepository#joinMatch(String,
 *       JoinCallback)}; on {@code onJoined} navigates to the game screen as guest, on
 *       {@code onUnavailable} shows "partida no disponible" and stays in the Lobby (Req 4.2, 4.3,
 *       16.2).</li>
 * </ul>
 *
 * <p>While the first {@code onChanged} has not arrived, a {@link ProgressBar} is shown; afterwards
 * the list or the empty-state view is shown depending on whether there are joinable matches
 * (Req 3.3).
 *
 * <p>Covers requirements: 3.1, 3.2, 3.3, 4.1, 4.2, 4.3, 7.1, 7.2, 8.1, 8.2, 8.3, 16.2, 17.1,
 * 17.2, 17.4.
 */
public class LobbyActivity extends AppCompatActivity {

    /**
     * Intent extra key carrying the {@code matchId} to the online game screen.
     *
     * <p>Declared here as a public constant so this Activity does not require a hard compile
     * dependency on {@code OnlineGameActivity} (created concurrently in task 10.2). The target
     * Activity exposes the same key value as {@code OnlineGameActivity.EXTRA_MATCH_ID}; both must
     * agree.
     */
    public static final String EXTRA_MATCH_ID = "co.edu.unal.reto4_tictactoe.MATCH_ID";

    /** Cloud abstraction (default ctor wires FirebaseAuthProvider + default FirebaseDatabase). */
    private MatchRepository repository;

    /** Real-time Lobby observation handle; non-null only while attached (onStart..onStop). */
    private ListenerHandle lobbyHandle;

    private RecyclerView matchesList;
    private MatchAdapter adapter;
    private View emptyView;
    private ProgressBar progress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_lobby);

        Toolbar toolbar = findViewById(R.id.lobby_toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.lobby_root), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        matchesList = findViewById(R.id.lobby_matches_list);
        emptyView = findViewById(R.id.lobby_empty_view);
        progress = findViewById(R.id.lobby_progress);

        adapter = new MatchAdapter(this::onJoinClicked);
        matchesList.setAdapter(adapter);

        MaterialButton createButton = findViewById(R.id.lobby_create_button);
        createButton.setOnClickListener(v -> onCreateMatchClicked());

        repository = new FirebaseMatchRepository();
    }

    // --- Lifecycle: attach/detach the Lobby listener -----------------------------------------

    @Override
    protected void onStart() {
        super.onStart();
        // Show the loading state until the first onChanged arrives (Req 3.3).
        showLoading();
        // Attach the real-time observer and KEEP the handle so onStop can detach it (Req 17.1).
        lobbyHandle = repository.observeOpenMatches(this::onMatchesChanged);
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Stop observing when leaving the visible state so no callbacks fire against a gone view
        // and the listener does not leak (Req 17.2, 17.4).
        if (lobbyHandle != null) {
            lobbyHandle.detach();
            lobbyHandle = null;
        }
    }

    // --- Lobby updates ------------------------------------------------------------------------

    /**
     * Receives the current list of joinable matches. Hides the loading indicator and shows either
     * the list or the empty-state view depending on whether the list is empty (Req 3.2, 3.3).
     */
    private void onMatchesChanged(List<MatchSummary> matches) {
        List<MatchSummary> safe = matches != null ? matches : new ArrayList<>();
        progress.setVisibility(View.GONE);
        if (safe.isEmpty()) {
            matchesList.setVisibility(View.GONE);
            emptyView.setVisibility(View.VISIBLE);
        } else {
            emptyView.setVisibility(View.GONE);
            matchesList.setVisibility(View.VISIBLE);
        }
        adapter.submit(safe);
    }

    private void showLoading() {
        progress.setVisibility(View.VISIBLE);
        matchesList.setVisibility(View.GONE);
        emptyView.setVisibility(View.GONE);
    }

    // --- Actions ------------------------------------------------------------------------------

    /** "Crear partida": creates a WAITING match and enters it as host (Req 4.1, 7.1). */
    private void onCreateMatchClicked() {
        repository.createMatch(new ResultCallback<String>() {
            @Override
            public void onSuccess(String matchId) {
                openGame(matchId);
            }

            @Override
            public void onError(Throwable error) {
                toastError();
            }
        });
    }

    /** Per-item "Unirse": claims the guest slot and enters as guest (Req 4.2, 4.3, 16.2). */
    private void onJoinClicked(MatchSummary summary) {
        if (summary == null || summary.getMatchId() == null) {
            return;
        }
        final String matchId = summary.getMatchId();
        repository.joinMatch(matchId, new JoinCallback() {
            @Override
            public void onJoined() {
                openGame(matchId);
            }

            @Override
            public void onUnavailable() {
                // Expected competing-join outcome: inform and stay in the Lobby (Req 16.2, 4.3).
                Toast.makeText(LobbyActivity.this,
                        R.string.lobby_match_unavailable, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(Throwable error) {
                toastError();
            }
        });
    }

    private void toastError() {
        Toast.makeText(this, R.string.online_error_generic, Toast.LENGTH_SHORT).show();
    }

    /**
     * Navigates to the online game screen carrying the {@code matchId}. The target is referenced
     * by class; {@code OnlineGameActivity} is created in task 10.2 and exposes
     * {@code EXTRA_MATCH_ID} with the same value as {@link #EXTRA_MATCH_ID}.
     */
    private void openGame(String matchId) {
        Intent intent = new Intent(this, OnlineGameActivity.class);
        intent.putExtra(OnlineGameActivity.EXTRA_MATCH_ID, matchId);
        startActivity(intent);
    }

    // --- Adapter ------------------------------------------------------------------------------

    /** Callback for a tapped "Unirse" button on a lobby row. */
    interface OnJoinListener {
        void onJoin(MatchSummary summary);
    }

    /**
     * {@link RecyclerView.Adapter} rendering the lobby's {@code WAITING} matches using the
     * {@code item_match} layout. Each row shows the host (shortened uid) and the waiting status,
     * and its join button delegates to {@link OnJoinListener} (Req 3.1, 3.2, 8.1).
     */
    static final class MatchAdapter extends RecyclerView.Adapter<MatchAdapter.MatchViewHolder> {

        private final List<MatchSummary> items = new ArrayList<>();
        private final OnJoinListener joinListener;

        MatchAdapter(OnJoinListener joinListener) {
            this.joinListener = joinListener;
        }

        void submit(List<MatchSummary> newItems) {
            items.clear();
            if (newItems != null) {
                items.addAll(newItems);
            }
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public MatchViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_match, parent, false);
            return new MatchViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull MatchViewHolder holder, int position) {
            holder.bind(items.get(position), joinListener);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        /** Holds the per-row views of {@code item_match.xml}. */
        static final class MatchViewHolder extends RecyclerView.ViewHolder {

            private final TextView host;
            private final TextView status;
            private final MaterialButton joinButton;

            MatchViewHolder(@NonNull View itemView) {
                super(itemView);
                host = itemView.findViewById(R.id.item_match_host);
                status = itemView.findViewById(R.id.item_match_status);
                joinButton = itemView.findViewById(R.id.item_match_join_button);
            }

            void bind(MatchSummary summary, OnJoinListener listener) {
                String hostUid = summary.getHostUid() != null ? summary.getHostUid() : "";
                String shortUid = hostUid.length() > 8 ? hostUid.substring(0, 8) : hostUid;
                host.setText(itemView.getContext()
                        .getString(R.string.lobby_host_label, shortUid));
                status.setText(R.string.lobby_match_waiting);
                joinButton.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onJoin(summary);
                    }
                });
            }
        }
    }
}
