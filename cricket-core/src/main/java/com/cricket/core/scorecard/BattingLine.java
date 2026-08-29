package com.cricket.core.scorecard;

import com.cricket.core.model.Dismissal;

/** One batter's line in the scorecard. Mutable; owned by a {@link ScoreCard}. */
public final class BattingLine {

    private final String playerId;
    private int runs;
    private int ballsFaced;
    private int fours;
    private int sixes;
    private boolean out;
    private Dismissal dismissal;
    private String dismissedBy;

    public BattingLine(String playerId) {
        if (playerId == null || playerId.trim().isEmpty()) {
            throw new IllegalArgumentException("playerId must not be blank");
        }
        this.playerId = playerId;
    }

    public String getPlayerId() {
        return playerId;
    }

    public int getRuns() {
        return runs;
    }

    public int getBallsFaced() {
        return ballsFaced;
    }

    public int getFours() {
        return fours;
    }

    public int getSixes() {
        return sixes;
    }

    public boolean isOut() {
        return out;
    }

    public Dismissal getDismissal() {
        return dismissal;
    }

    public String getDismissedBy() {
        return dismissedBy;
    }

    /** Credits runs off the bat and tracks boundaries. */
    public void addRuns(int scored) {
        if (scored < 0) {
            throw new IllegalArgumentException("scored runs must not be negative");
        }
        this.runs += scored;
        if (scored == 4) {
            this.fours++;
        } else if (scored == 6) {
            this.sixes++;
        }
    }

    public void addBallFaced() {
        this.ballsFaced++;
    }

    public void markOut(Dismissal how, String bowlerId) {
        this.out = true;
        this.dismissal = how;
        this.dismissedBy = how != null && how.isCreditedToBowler() ? bowlerId : null;
    }

    /** Runs per hundred balls; zero when no ball has been faced. */
    public double strikeRate() {
        if (ballsFaced == 0) {
            return 0.0;
        }
        return (runs * 100.0) / ballsFaced;
    }

    /** Runs from boundaries only. */
    public int boundaryRuns() {
        return fours * 4 + sixes * 6;
    }

    public boolean isMilestone(int threshold) {
        return runs >= threshold;
    }

    /** Scorecard shorthand, e.g. "47* (32)" or "47 (32)". */
    public String summary() {
        return runs + (out ? "" : "*") + " (" + ballsFaced + ")";
    }

    @Override
    public String toString() {
        return playerId + " " + summary();
    }
}
