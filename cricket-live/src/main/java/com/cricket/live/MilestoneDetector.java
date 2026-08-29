package com.cricket.live;

import com.cricket.core.scorecard.BattingLine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Raises a notice when a batter passes a round score, or when a bowler takes a
 * five-wicket haul. Each milestone fires once per player per innings.
 */
public final class MilestoneDetector implements BallListener {

    /** Batting milestones, in ascending order. */
    public static final int[] BATTING_MILESTONES = {50, 100, 150, 200};

    /** A milestone that has been reached. */
    public static final class Milestone {
        private final String playerId;
        private final int threshold;
        private final int balls;
        private final String kind;

        Milestone(String playerId, int threshold, int balls, String kind) {
            this.playerId = playerId;
            this.threshold = threshold;
            this.balls = balls;
            this.kind = kind;
        }

        public String getPlayerId() {
            return playerId;
        }

        public int getThreshold() {
            return threshold;
        }

        public int getBalls() {
            return balls;
        }

        /** Either "batting" or "bowling". */
        public String getKind() {
            return kind;
        }

        @Override
        public String toString() {
            return playerId + " " + kind + " " + threshold;
        }
    }

    private final List<Milestone> reached = new ArrayList<Milestone>();
    private final Set<String> alreadyFired = new HashSet<String>();
    private final Map<String, Integer> lastKnownScore = new HashMap<String, Integer>();

    /** Set once a milestone has been detected; observed by the live feed. */
    private volatile boolean milestoneReached;

    @Override
    public String name() {
        return "milestone-detector";
    }

    @Override
    public void onBall(BallEvent event) {
        String strikerId = event.getStrikerId();
        BattingLine line = event.getInnings().getScoreCard().battingLine(strikerId);
        lastKnownScore.put(strikerId, line.getRuns());

        for (int threshold : BATTING_MILESTONES) {
            if (line.getRuns() < threshold) {
                continue;
            }
            String key = strikerId + "@" + threshold;
            if (alreadyFired.add(key)) {
                reached.add(new Milestone(strikerId, threshold, line.getBallsFaced(), "batting"));
                milestoneReached = true;
            }
        }

        if (event.isWicket()) {
            String bowlerId = event.getBowlerId();
            int wickets = event.getInnings().getScoreCard().bowlingLine(bowlerId).getWickets();
            if (wickets >= 5) {
                String key = bowlerId + "@5w";
                if (alreadyFired.add(key)) {
                    reached.add(new Milestone(bowlerId, 5, wickets, "bowling"));
                    milestoneReached = true;
                }
            }
        }
    }

    /** True once any milestone has been detected. */
    public boolean isMilestoneReached() {
        return milestoneReached;
    }

    public List<Milestone> getReached() {
        return new ArrayList<Milestone>(reached);
    }

    public int count() {
        return reached.size();
    }

    /** Milestones reached by one player. */
    public List<Milestone> forPlayer(String playerId) {
        List<Milestone> result = new ArrayList<Milestone>();
        for (Milestone m : reached) {
            if (m.getPlayerId().equals(playerId)) {
                result.add(m);
            }
        }
        return result;
    }

    public boolean hasReached(String playerId, int threshold) {
        return alreadyFired.contains(playerId + "@" + threshold);
    }

    /** Highest score seen for a player, or 0 when unseen. */
    public int lastKnownScore(String playerId) {
        Integer score = lastKnownScore.get(playerId);
        return score == null ? 0 : score;
    }

    public void reset() {
        reached.clear();
        alreadyFired.clear();
        lastKnownScore.clear();
        milestoneReached = false;
    }
}
