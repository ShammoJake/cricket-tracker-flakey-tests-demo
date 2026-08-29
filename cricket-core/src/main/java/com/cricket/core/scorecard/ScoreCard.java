package com.cricket.core.scorecard;

import com.cricket.core.engine.ScoringRules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Running totals for one innings.
 *
 * <p>Batting and bowling lines are created lazily on first reference, so callers can
 * record a delivery without pre-registering the players involved.
 */
public final class ScoreCard {

    private final String inningsId;

    private int totalRuns;
    private int wickets;
    private int legalBalls;

    private int wides;
    private int noBalls;
    private int byes;
    private int legByes;
    private int penalties;

    private final Map<String, BattingLine> batting = new HashMap<String, BattingLine>();
    private final Map<String, BowlingLine> bowling = new HashMap<String, BowlingLine>();

    public ScoreCard(String inningsId) {
        if (inningsId == null || inningsId.trim().isEmpty()) {
            throw new IllegalArgumentException("inningsId must not be blank");
        }
        this.inningsId = inningsId;
    }

    public String getInningsId() {
        return inningsId;
    }

    public int getTotalRuns() {
        return totalRuns;
    }

    public int getWickets() {
        return wickets;
    }

    public int getLegalBalls() {
        return legalBalls;
    }

    public int getWides() {
        return wides;
    }

    public int getNoBalls() {
        return noBalls;
    }

    public int getByes() {
        return byes;
    }

    public int getLegByes() {
        return legByes;
    }

    public int getPenalties() {
        return penalties;
    }

    public void addRuns(int runs) {
        if (runs < 0) {
            throw new IllegalArgumentException("runs must not be negative");
        }
        this.totalRuns += runs;
    }

    public void addWicket() {
        this.wickets++;
    }

    public void addLegalBall() {
        this.legalBalls++;
    }

    public void addWides(int runs) {
        this.wides += runs;
    }

    public void addNoBalls(int runs) {
        this.noBalls += runs;
    }

    public void addByes(int runs) {
        this.byes += runs;
    }

    public void addLegByes(int runs) {
        this.legByes += runs;
    }

    public void addPenalties(int runs) {
        this.penalties += runs;
    }

    /** Total runs recorded as extras. */
    public int totalExtras() {
        return wides + noBalls + byes + legByes + penalties;
    }

    /** Completed overs, discarding the part-over. */
    public int completedOvers() {
        return legalBalls / ScoringRules.ballsPerOver();
    }

    public int ballsIntoCurrentOver() {
        return legalBalls % ScoringRules.ballsPerOver();
    }

    /** Overs as a decimal in cricket notation, e.g. 12.3. */
    public double oversFaced() {
        return completedOvers() + (ballsIntoCurrentOver() / 10.0);
    }

    /** Runs per over; zero before a legal ball has been bowled. */
    public double runRate() {
        if (legalBalls == 0) {
            return 0.0;
        }
        return (totalRuns * (double) ScoringRules.ballsPerOver()) / legalBalls;
    }

    /** True once ten wickets have fallen. */
    public boolean isAllOut() {
        return wickets >= 10;
    }

    public BattingLine battingLine(String playerId) {
        BattingLine line = batting.get(playerId);
        if (line == null) {
            line = new BattingLine(playerId);
            batting.put(playerId, line);
        }
        return line;
    }

    public BowlingLine bowlingLine(String playerId) {
        BowlingLine line = bowling.get(playerId);
        if (line == null) {
            line = new BowlingLine(playerId);
            bowling.put(playerId, line);
        }
        return line;
    }

    public boolean hasBatted(String playerId) {
        return batting.containsKey(playerId);
    }

    public boolean hasBowled(String playerId) {
        return bowling.containsKey(playerId);
    }

    public Map<String, BattingLine> getBatting() {
        return Collections.unmodifiableMap(batting);
    }

    public Map<String, BowlingLine> getBowling() {
        return Collections.unmodifiableMap(bowling);
    }

    public List<BattingLine> battingLines() {
        return new ArrayList<BattingLine>(batting.values());
    }

    public List<BowlingLine> bowlingLines() {
        return new ArrayList<BowlingLine>(bowling.values());
    }

    /** Scoreline shorthand, e.g. "147/3 (16.2)". */
    public String summary() {
        return totalRuns + "/" + wickets + " (" + completedOvers() + "." + ballsIntoCurrentOver() + ")";
    }

    @Override
    public String toString() {
        return inningsId + " " + summary();
    }
}
