package co.edu.unal.reto4_tictactoe.online;

import java.util.HashMap;
import java.util.Map;

/**
 * Authoritative Estado_Partida of an online match, as stored under {@code /matches/<matchId>} in
 * the cloud (Firebase RTDB). The cloud is the single source of truth; the client reuses this state
 * only to render and gate UX (design §Data Models, Req 20).
 *
 * <p>This is a Firebase-friendly POJO: it has a public no-arg constructor and standard
 * getters/setters so Firebase RTDB can serialize and deserialize it. Fields mirror the RTDB
 * document exactly:
 *
 * <ul>
 *   <li>{@code matchId} — unique {@code push()} key; not stored inside the document, set from the
 *       snapshot key when mapping (Req 9.1, 9.3).</li>
 *   <li>{@code hostUid} — uid of the Anfitrión; plays mark {@code 'X'} (Req 2.2, 11.1).</li>
 *   <li>{@code guestUid} — uid of the Invitado; plays mark {@code 'O'}; {@code null} while
 *       WAITING (Req 1.1, 10.1).</li>
 *   <li>{@code board} — 9-char string; each char ∈ {@code {'X','O',' '}} (Req 9.2, 13.3).</li>
 *   <li>{@code turn} — {@code "X"} or {@code "O"}; the mark to move; starts at {@code "X"}
 *       (Req 11.3, 14.1).</li>
 *   <li>{@code status} — the {@link GameLifecycle} value, stored by name (Req 1, 5, 10, 12, 18,
 *       19).</li>
 *   <li>{@code winner} — int {@code 0|1|2|3}, aligned with
 *       {@code TicTacToeGame.checkForWinner}: 0 continue, 1 tie, 2 X wins, 3 O wins
 *       (Req 14.3).</li>
 *   <li>{@code createdAt} / {@code updatedAt} — epoch-ms timestamps (Lobby ordering + TTL).</li>
 *   <li>{@code presence} — map {@code uid → bool}, driven by {@code onDisconnect} (Req 18.2,
 *       23.2).</li>
 * </ul>
 */
public class MatchState {

    /** Empty board: nine {@link #EMPTY_CELL} characters. */
    public static final String EMPTY_BOARD = "         ";

    /** Empty-cell character, matching {@code TicTacToeGame.OPEN_SPOT}. */
    public static final char EMPTY_CELL = ' ';

    /** Host mark; the Anfitrión plays {@code 'X'} and moves first (Req 11.1, 11.3). */
    public static final String MARK_X = "X";

    /** Guest mark; the Invitado plays {@code 'O'} (Req 10.1). */
    public static final String MARK_O = "O";

    /** winner code: game continues (Req 14.3). */
    public static final int WINNER_NONE = 0;
    /** winner code: tie (Req 14.3). */
    public static final int WINNER_TIE = 1;
    /** winner code: X (host) wins (Req 14.3). */
    public static final int WINNER_X = 2;
    /** winner code: O (guest) wins (Req 14.3). */
    public static final int WINNER_O = 3;

    private String matchId;
    private String hostUid;
    private String guestUid;
    private String board = EMPTY_BOARD;
    private String turn = MARK_X;
    private String status = GameLifecycle.WAITING.name();
    private int winner = WINNER_NONE;
    private long createdAt;
    private long updatedAt;
    private Map<String, Boolean> presence = new HashMap<>();

    /** Required public no-arg constructor for Firebase RTDB deserialization. */
    public MatchState() {
        // Fields keep their default WAITING/empty-board values.
    }

    public String getMatchId() {
        return matchId;
    }

    public void setMatchId(String matchId) {
        this.matchId = matchId;
    }

    public String getHostUid() {
        return hostUid;
    }

    public void setHostUid(String hostUid) {
        this.hostUid = hostUid;
    }

    public String getGuestUid() {
        return guestUid;
    }

    public void setGuestUid(String guestUid) {
        this.guestUid = guestUid;
    }

    public String getBoard() {
        return board;
    }

    public void setBoard(String board) {
        this.board = board;
    }

    public String getTurn() {
        return turn;
    }

    public void setTurn(String turn) {
        this.turn = turn;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getWinner() {
        return winner;
    }

    public void setWinner(int winner) {
        this.winner = winner;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Map<String, Boolean> getPresence() {
        return presence;
    }

    public void setPresence(Map<String, Boolean> presence) {
        this.presence = presence;
    }

    // --- Typed convenience accessors (not serialized as extra RTDB fields) ---

    /**
     * Typed view of {@link #getStatus()} as a {@link GameLifecycle}.
     *
     * @return the parsed lifecycle, or {@code null} if {@code status} is unset or unrecognized
     */
    public GameLifecycle lifecycle() {
        if (status == null) {
            return null;
        }
        try {
            return GameLifecycle.valueOf(status);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /** Sets {@link #setStatus(String)} from a typed {@link GameLifecycle}. */
    public void setLifecycle(GameLifecycle lifecycle) {
        this.status = (lifecycle == null) ? null : lifecycle.name();
    }
}
