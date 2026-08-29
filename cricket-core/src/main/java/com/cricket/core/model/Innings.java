package com.cricket.core.model;

import com.cricket.core.scorecard.ScoreCard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One innings of a match: the deliveries bowled, the running scorecard, and the
 * live state (who is on strike, which over is in progress).
 *
 * <p>Mutable and not synchronised. The ingest pipeline is expected to serialise
 * writes per innings.
 */
public final class Innings {

    private final String id;
    private final int number;
    private final Team battingTeam;
    private final Team bowlingTeam;
    private final ScoreCard scoreCard;

    private final List<Ball> balls = new ArrayList<Ball>();
    private final List<Over> overs = new ArrayList<Over>();
    private final List<Partnership> partnerships = new ArrayList<Partnership>();

    private String strikerId;
    private String nonStrikerId;
    private int oversLimit = -1;
    private int target = -1;
    private boolean closed;

    public Innings(String id, int number, Team battingTeam, Team bowlingTeam) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("innings id must not be blank");
        }
        if (number < 1) {
            throw new IllegalArgumentException("innings number starts at 1");
        }
        if (battingTeam == null || bowlingTeam == null) {
            throw new IllegalArgumentException("innings requires both teams");
        }
        if (battingTeam.getId().equals(bowlingTeam.getId())) {
            throw new IllegalArgumentException("a team cannot bat and bowl in the same innings");
        }
        this.id = id;
        this.number = number;
        this.battingTeam = battingTeam;
        this.bowlingTeam = bowlingTeam;
        this.scoreCard = new ScoreCard(id);
    }

    public String getId() {
        return id;
    }

    public int getNumber() {
        return number;
    }

    public Team getBattingTeam() {
        return battingTeam;
    }

    public Team getBowlingTeam() {
        return bowlingTeam;
    }

    public ScoreCard getScoreCard() {
        return scoreCard;
    }

    public List<Ball> getBalls() {
        return Collections.unmodifiableList(balls);
    }

    public List<Over> getOvers() {
        return Collections.unmodifiableList(overs);
    }

    public List<Partnership> getPartnerships() {
        return Collections.unmodifiableList(partnerships);
    }

    public String getStrikerId() {
        return strikerId;
    }

    public String getNonStrikerId() {
        return nonStrikerId;
    }

    public int getOversLimit() {
        return oversLimit;
    }

    public int getTarget() {
        return target;
    }

    public boolean isClosed() {
        return closed;
    }

    public void setOversLimit(int oversLimit) {
        this.oversLimit = oversLimit;
    }

    /** Runs required to win; -1 when this innings is not a chase. */
    public void setTarget(int target) {
        this.target = target;
    }

    public boolean isChase() {
        return target > 0;
    }

    public void openWith(String striker, String nonStriker) {
        if (striker == null || nonStriker == null) {
            throw new IllegalArgumentException("both openers are required");
        }
        if (striker.equals(nonStriker)) {
            throw new IllegalArgumentException("openers must be different players");
        }
        this.strikerId = striker;
        this.nonStrikerId = nonStriker;
        this.partnerships.add(new Partnership(striker, nonStriker, 1));
    }

    /** Appends a delivery to the innings record. */
    public void recordBall(Ball ball) {
        if (ball == null) {
            throw new IllegalArgumentException("ball must not be null");
        }
        balls.add(ball);
    }

    public int ballCount() {
        return balls.size();
    }

    public Ball lastBall() {
        return balls.isEmpty() ? null : balls.get(balls.size() - 1);
    }

    /** The over currently in progress, or null before the first over starts. */
    public Over currentOver() {
        return overs.isEmpty() ? null : overs.get(overs.size() - 1);
    }

    public Over startOver(int number, String bowlerId) {
        Over over = new Over(number, bowlerId);
        overs.add(over);
        return over;
    }

    public Partnership currentPartnership() {
        for (int i = partnerships.size() - 1; i >= 0; i--) {
            Partnership p = partnerships.get(i);
            if (!p.isBroken()) {
                return p;
            }
        }
        return null;
    }

    public Partnership startPartnership(String batterOne, String batterTwo, int wicketNumber) {
        Partnership p = new Partnership(batterOne, batterTwo, wicketNumber);
        partnerships.add(p);
        return p;
    }

    /** Swaps the batters, as happens on an odd run or at the end of an over. */
    public void rotateStrike() {
        String tmp = strikerId;
        strikerId = nonStrikerId;
        nonStrikerId = tmp;
    }

    /** Replaces the dismissed batter with the incoming one. */
    public void replaceBatter(String outgoingId, String incomingId) {
        if (incomingId == null) {
            throw new IllegalArgumentException("incoming batter must not be null");
        }
        if (outgoingId == null) {
            throw new IllegalArgumentException("outgoing batter must not be null");
        }
        if (outgoingId.equals(strikerId)) {
            strikerId = incomingId;
        } else if (outgoingId.equals(nonStrikerId)) {
            nonStrikerId = incomingId;
        } else {
            throw new IllegalArgumentException(outgoingId + " is not at the crease");
        }
    }

    public void close() {
        this.closed = true;
    }

    /** Legal balls remaining, or -1 when the innings has no over limit. */
    public int ballsRemaining() {
        if (oversLimit <= 0) {
            return -1;
        }
        return Math.max(0, oversLimit * 6 - scoreCard.getLegalBalls());
    }

    /** Runs still needed to reach the target, or -1 when not chasing. */
    public int runsRequired() {
        if (!isChase()) {
            return -1;
        }
        return Math.max(0, target - scoreCard.getTotalRuns());
    }

    /** Required run rate for a chase, or -1 when not chasing or no balls remain. */
    public double requiredRunRate() {
        int remaining = ballsRemaining();
        if (!isChase() || remaining <= 0) {
            return -1.0;
        }
        return (runsRequired() * 6.0) / remaining;
    }

    /** True once the over limit is reached or ten wickets have fallen. */
    public boolean isComplete() {
        if (closed) {
            return true;
        }
        if (scoreCard.isAllOut()) {
            return true;
        }
        return oversLimit > 0 && scoreCard.getLegalBalls() >= oversLimit * 6;
    }

    @Override
    public String toString() {
        return battingTeam.getName() + " " + scoreCard.summary();
    }
}
