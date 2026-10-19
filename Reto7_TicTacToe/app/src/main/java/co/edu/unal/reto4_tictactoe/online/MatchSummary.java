package co.edu.unal.reto4_tictactoe.online;

/**
 * Lightweight Lobby view of a {@code WAITING} match, as stored under {@code /lobby/<matchId>} in
 * the cloud (Firebase RTDB).
 *
 * <p>The {@code /lobby} subtree is a small index of joinable matches so the Lobby's
 * {@code RecyclerView} can listen to a tiny node without downloading in-progress games
 * (design §Nodo {@code lobby}, Req 8.2, 8.3). It carries only what the list needs:
 *
 * <ul>
 *   <li>{@code matchId} — the match's {@code push()} key; set from the snapshot key when mapping
 *       (Req 9.1, 9.3).</li>
 *   <li>{@code hostUid} — uid of the Anfitrión who created the match (Req 2.2, 11.1).</li>
 *   <li>{@code createdAt} — epoch-ms creation timestamp, used to order the Lobby (Decisión 4).</li>
 * </ul>
 *
 * <p>Firebase-friendly POJO: public no-arg constructor plus getters/setters.
 */
public class MatchSummary {

    private String matchId;
    private String hostUid;
    private long createdAt;

    /** Required public no-arg constructor for Firebase RTDB deserialization. */
    public MatchSummary() {
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

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }
}
