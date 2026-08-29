package com.cricket.core.engine;

import com.cricket.core.model.Ball;

/** What the scoring engine did with a delivery. */
public final class ScoringResult {

    private final Ball ball;
    private final int runsScored;
    private final int runsRun;
    private final boolean legalDelivery;
    private final boolean wicketFell;
    private final boolean strikeRotated;
    private final boolean overCompleted;
    private final boolean freeHitNext;

    ScoringResult(Ball ball, int runsScored, int runsRun, boolean legalDelivery,
                  boolean wicketFell, boolean strikeRotated, boolean overCompleted,
                  boolean freeHitNext) {
        this.ball = ball;
        this.runsScored = runsScored;
        this.runsRun = runsRun;
        this.legalDelivery = legalDelivery;
        this.wicketFell = wicketFell;
        this.strikeRotated = strikeRotated;
        this.overCompleted = overCompleted;
        this.freeHitNext = freeHitNext;
    }

    public Ball getBall() {
        return ball;
    }

    /** Every run added to the team total, penalties included. */
    public int getRunsScored() {
        return runsScored;
    }

    /** Runs physically run or hit to the boundary, excluding automatic penalties. */
    public int getRunsRun() {
        return runsRun;
    }

    public boolean isLegalDelivery() {
        return legalDelivery;
    }

    public boolean isWicketFell() {
        return wicketFell;
    }

    public boolean isStrikeRotated() {
        return strikeRotated;
    }

    public boolean isOverCompleted() {
        return overCompleted;
    }

    /** True when the next delivery is a free hit. */
    public boolean isFreeHitNext() {
        return freeHitNext;
    }

    public boolean isDot() {
        return runsScored == 0 && !wicketFell;
    }

    public boolean isBoundary() {
        return ball != null && (ball.getRunsOffBat() == 4 || ball.getRunsOffBat() == 6);
    }

    @Override
    public String toString() {
        return (ball == null ? "?" : ball.address()) + " +" + runsScored
                + (wicketFell ? " W" : "") + (overCompleted ? " (over)" : "");
    }
}
