package com.cricket.live;

import com.cricket.core.scorecard.ScoreCard;

import java.util.HashMap;
import java.util.Map;

/**
 * Maintains the derived per-match view that the query path reads.
 *
 * <p>The scoring engine has already updated the innings by the time this listener
 * runs; this class keeps the denormalised totals that the scorecard endpoint serves,
 * so a reader does not have to walk the innings.
 */
public final class ScorecardUpdater implements BallListener {

    /** Denormalised snapshot for one match. */
    public static final class Snapshot {
        private int runs;
        private int wickets;
        private int legalBalls;
        private int boundaries;
        private long lastSequence;

        public int getRuns() {
            return runs;
        }

        public int getWickets() {
            return wickets;
        }

        public int getLegalBalls() {
            return legalBalls;
        }

        public int getBoundaries() {
            return boundaries;
        }

        public long getLastSequence() {
            return lastSequence;
        }

        public String summary() {
            return runs + "/" + wickets + " (" + (legalBalls / 6) + "." + (legalBalls % 6) + ")";
        }
    }

    private final Map<String, Snapshot> snapshots = new HashMap<String, Snapshot>();
    private int applied;

    @Override
    public String name() {
        return "scorecard-updater";
    }

    @Override
    public void onBall(BallEvent event) {
        Snapshot snapshot = snapshotFor(event.getMatchId());
        ScoreCard card = event.getInnings().getScoreCard();

        snapshot.runs = card.getTotalRuns();
        snapshot.wickets = card.getWickets();
        snapshot.legalBalls = card.getLegalBalls();
        if (event.getResult() != null && event.getResult().isBoundary()) {
            snapshot.boundaries++;
        }
        snapshot.lastSequence = event.getSequence();
        applied++;
    }

    private Snapshot snapshotFor(String matchId) {
        Snapshot snapshot = snapshots.get(matchId);
        if (snapshot == null) {
            snapshot = new Snapshot();
            snapshots.put(matchId, snapshot);
        }
        return snapshot;
    }

    /** The snapshot for a match, or null when nothing has been recorded for it. */
    public Snapshot snapshot(String matchId) {
        return snapshots.get(matchId);
    }

    public boolean tracks(String matchId) {
        return snapshots.containsKey(matchId);
    }

    public int trackedMatches() {
        return snapshots.size();
    }

    /** Deliveries this listener has handled. */
    public int appliedCount() {
        return applied;
    }

    public void reset() {
        snapshots.clear();
        applied = 0;
    }
}
