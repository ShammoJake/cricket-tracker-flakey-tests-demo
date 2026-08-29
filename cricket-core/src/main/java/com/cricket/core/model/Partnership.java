package com.cricket.core.model;

/** A stand between two batters. Mutable; ends when one of them is dismissed. */
public final class Partnership {

    private final String batterOneId;
    private final String batterTwoId;
    private final int wicketNumber;
    private int runs;
    private int balls;
    private boolean broken;

    public Partnership(String batterOneId, String batterTwoId, int wicketNumber) {
        if (batterOneId == null || batterTwoId == null) {
            throw new IllegalArgumentException("partnership requires two batters");
        }
        if (batterOneId.equals(batterTwoId)) {
            throw new IllegalArgumentException("partnership requires two distinct batters");
        }
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

    /** Which wicket this stand is for; 1 means the opening partnership. */
    public int getWicketNumber() {
        return wicketNumber;
    }

    public int getRuns() {
        return runs;
    }

    public int getBalls() {
        return balls;
    }

    public boolean isBroken() {
        return broken;
    }

    public void addRuns(int scored) {
        if (scored < 0) {
            throw new IllegalArgumentException("runs must not be negative");
        }
        this.runs += scored;
    }

    public void addBall() {
        this.balls++;
    }

    public void breakStand() {
        this.broken = true;
    }

    public boolean involves(String playerId) {
        return batterOneId.equals(playerId) || batterTwoId.equals(playerId);
    }

    /** Runs per hundred balls for the stand; zero before a ball is faced. */
    public double runRate() {
        if (balls == 0) {
            return 0.0;
        }
        return (runs * 6.0) / balls;
    }

    @Override
    public String toString() {
        return wicketNumber + "th wicket: " + runs + " (" + balls + ")";
    }
}
