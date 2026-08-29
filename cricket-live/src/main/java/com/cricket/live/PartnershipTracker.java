package com.cricket.live;

import com.cricket.core.model.Partnership;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Keeps a running view of each innings' current stand and the best of the innings. */
public final class PartnershipTracker implements BallListener {

    /** A completed or in-progress stand, flattened for the query path. */
    public static final class Stand {
        private final String batterOneId;
        private final String batterTwoId;
        private final int wicketNumber;
        private int runs;
        private int balls;

        Stand(String batterOneId, String batterTwoId, int wicketNumber) {
            this.batterOneId = batterOneId;
            this.batterTwoId = batterTwoId;
            this.wicketNumber = wicketNumber;
        }

        public String getBatterOneId() {
            return batterOneId;
        }

        public String getBatterTwoId() {
            return batterTwoId;
        }

        public int getWicketNumber() {
            return wicketNumber;
        }

        public int getRuns() {
            return runs;
        }

        public int getBalls() {
            return balls;
        }

        @Override
        public String toString() {
            return wicketNumber + ": " + runs + " (" + balls + ")";
        }
    }

    private final Map<String, Stand> current = new HashMap<String, Stand>();
    private final List<Stand> completed = new ArrayList<Stand>();
    private int updates;

    @Override
    public String name() {
        return "partnership-tracker";
    }

    @Override
    public void onBall(BallEvent event) {
        String matchId = event.getMatchId();
        Partnership stand = event.getInnings().currentPartnership();
        if (stand == null) {
            return;
        }

        Stand tracked = current.get(matchId);
        if (tracked == null || tracked.wicketNumber != stand.getWicketNumber()) {
            if (tracked != null) {
                completed.add(tracked);
            }
            tracked = new Stand(stand.getBatterOneId(), stand.getBatterTwoId(),
                    stand.getWicketNumber());
            current.put(matchId, tracked);
        }

        tracked.runs = stand.getRuns();
        tracked.balls = stand.getBalls();
        updates++;
    }

    /** The stand in progress for a match, or null when none is tracked. */
    public Stand currentStand(String matchId) {
        return current.get(matchId);
    }

    public List<Stand> completedStands() {
        return new ArrayList<Stand>(completed);
    }

    /** Highest stand seen for a match, current one included. */
    public Stand best(String matchId) {
        Stand best = current.get(matchId);
        for (Stand s : completed) {
            if (best == null || s.getRuns() > best.getRuns()) {
                best = s;
            }
        }
        return best;
    }

    public int updateCount() {
        return updates;
    }

    public int trackedMatches() {
        return current.size();
    }

    public void reset() {
        current.clear();
        completed.clear();
        updates = 0;
    }
}
