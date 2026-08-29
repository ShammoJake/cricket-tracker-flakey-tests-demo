package com.cricket.core.scorecard;

/**
 * One bowler's line in the scorecard. Mutable; owned by a {@link ScoreCard}.
 *
 * <p>Overs are tracked as a count of legal deliveries, so "3.4 overs" is 22 legal
 * balls. Wides and no-balls do not advance the count.
 */
public final class BowlingLine {

    private final String playerId;
    private int legalBalls;
    private int runsConceded;
    private int wickets;
    private int maidens;
    private int wides;
    private int noBalls;

    public BowlingLine(String playerId) {
        if (playerId == null || playerId.trim().isEmpty()) {
            throw new IllegalArgumentException("playerId must not be blank");
        }
        this.playerId = playerId;
    }

    public String getPlayerId() {
        return playerId;
    }

    public int getLegalBalls() {
        return legalBalls;
    }

    public int getRunsConceded() {
        return runsConceded;
    }

    public int getWickets() {
        return wickets;
    }

    public int getMaidens() {
        return maidens;
    }

    public int getWides() {
        return wides;
    }

    public int getNoBalls() {
        return noBalls;
    }

    public void addLegalBall() {
        this.legalBalls++;
    }

    public void addRunsConceded(int runs) {
        if (runs < 0) {
            throw new IllegalArgumentException("conceded runs must not be negative");
        }
        this.runsConceded += runs;
    }

    public void addWicket() {
        this.wickets++;
    }

    public void addMaiden() {
        this.maidens++;
    }

    public void addWide() {
        this.wides++;
    }

    public void addNoBall() {
        this.noBalls++;
    }

    /** Completed overs, discarding the part-over. */
    public int completedOvers() {
        return legalBalls / 6;
    }

    /** Legal balls bowled in the current incomplete over. */
    public int ballsIntoCurrentOver() {
        return legalBalls % 6;
    }

    /** Overs as a decimal in cricket notation, e.g. 3.4 for 22 balls. */
    public double oversBowled() {
        return completedOvers() + (ballsIntoCurrentOver() / 10.0);
    }

    /** Runs per over; zero when nothing has been bowled. */
    public double economy() {
        if (legalBalls == 0) {
            return 0.0;
        }
        return (runsConceded * 6.0) / legalBalls;
    }

    /** Runs per wicket, or -1 when no wicket has fallen. */
    public double average() {
        if (wickets == 0) {
            return -1.0;
        }
        return (double) runsConceded / wickets;
    }

    /** Legal balls per wicket, or -1 when no wicket has fallen. */
    public double strikeRate() {
        if (wickets == 0) {
            return -1.0;
        }
        return (double) legalBalls / wickets;
    }

    /** Scorecard shorthand, e.g. "3.4-0-22-2". */
    public String figures() {
        return completedOvers() + "." + ballsIntoCurrentOver()
                + "-" + maidens + "-" + runsConceded + "-" + wickets;
    }

    @Override
    public String toString() {
        return playerId + " " + figures();
    }
}
