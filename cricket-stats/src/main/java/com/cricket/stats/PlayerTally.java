package com.cricket.stats;

/** Accumulated batting and bowling figures for one player across an innings or series. */
public final class PlayerTally {

    private final String playerId;
    private int runs;
    private int ballsFaced;
    private int fours;
    private int sixes;
    private int dismissals;
    private int wickets;
    private int runsConceded;
    private int legalBallsBowled;
    private int catches;

    public PlayerTally(String playerId) {
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

    public int getDismissals() {
        return dismissals;
    }

    public int getWickets() {
        return wickets;
    }

    public int getRunsConceded() {
        return runsConceded;
    }

    public int getLegalBallsBowled() {
        return legalBallsBowled;
    }

    public int getCatches() {
        return catches;
    }

    public void addBatting(int runsScored, int balls, int foursHit, int sixesHit) {
        this.runs += runsScored;
        this.ballsFaced += balls;
        this.fours += foursHit;
        this.sixes += sixesHit;
    }

    public void addDismissal() {
        this.dismissals++;
    }

    public void addBowling(int wicketsTaken, int conceded, int legalBalls) {
        this.wickets += wicketsTaken;
        this.runsConceded += conceded;
        this.legalBallsBowled += legalBalls;
    }

    public void addCatch() {
        this.catches++;
    }

    /** Runs per hundred balls; zero before facing a ball. */
    public double strikeRate() {
        return ballsFaced == 0 ? 0.0 : (runs * 100.0) / ballsFaced;
    }

    /** Runs per dismissal; equal to the runs when never out. */
    public double battingAverage() {
        return dismissals == 0 ? runs : (double) runs / dismissals;
    }

    /** Runs conceded per over; zero before bowling. */
    public double economy() {
        return legalBallsBowled == 0 ? 0.0 : (runsConceded * 6.0) / legalBallsBowled;
    }

    /** Runs conceded per wicket, or -1 when wicketless. */
    public double bowlingAverage() {
        return wickets == 0 ? -1.0 : (double) runsConceded / wickets;
    }

    public int boundaryRuns() {
        return fours * 4 + sixes * 6;
    }

    @Override
    public String toString() {
        return playerId + " " + runs + "r " + wickets + "w";
    }
}
