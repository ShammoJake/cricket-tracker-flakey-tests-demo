package com.cricket.core.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One over: the deliveries bowled by a single bowler from one end. */
public final class Over {

    private final int number;
    private final String bowlerId;
    private final List<Ball> balls = new ArrayList<Ball>();

    public Over(int number, String bowlerId) {
        if (number < 0) {
            throw new IllegalArgumentException("over number must not be negative");
        }
        if (bowlerId == null || bowlerId.trim().isEmpty()) {
            throw new IllegalArgumentException("over requires a bowler");
        }
        this.number = number;
        this.bowlerId = bowlerId;
    }

    public int getNumber() {
        return number;
    }

    public String getBowlerId() {
        return bowlerId;
    }

    public List<Ball> getBalls() {
        return Collections.unmodifiableList(balls);
    }

    public void record(Ball ball) {
        if (ball == null) {
            throw new IllegalArgumentException("ball must not be null");
        }
        balls.add(ball);
    }

    /** Deliveries that count towards the six of the over. */
    public int legalBalls() {
        int count = 0;
        for (Ball b : balls) {
            if (b.isLegalDelivery()) {
                count++;
            }
        }
        return count;
    }

    public boolean isComplete() {
        return legalBalls() >= 6;
    }

    public int runsConceded() {
        int runs = 0;
        for (Ball b : balls) {
            runs += b.totalRuns();
        }
        return runs;
    }

    public int wickets() {
        int count = 0;
        for (Ball b : balls) {
            if (b.isWicket()) {
                count++;
            }
        }
        return count;
    }

    /**
     * A maiden is a completed over from which no runs were scored off the bat and
     * no runs were charged to the bowler. Byes and leg-byes do not spoil a maiden.
     */
    public boolean isMaiden() {
        if (!isComplete()) {
            return false;
        }
        for (Ball b : balls) {
            if (b.runsChargedToBowler() > 0) {
                return false;
            }
        }
        return true;
    }

    /** Scorecard shorthand for the over, e.g. "1 . 4 W 2 ." */
    public String sequence() {
        StringBuilder sb = new StringBuilder();
        for (Ball b : balls) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            if (b.isWicket()) {
                sb.append('W');
            } else if (b.hasExtra() && b.getExtra().getType() == ExtraType.WIDE) {
                sb.append("wd");
            } else if (b.hasExtra() && b.getExtra().getType() == ExtraType.NO_BALL) {
                sb.append("nb");
            } else if (b.totalRuns() == 0) {
                sb.append('.');
            } else {
                sb.append(b.totalRuns());
            }
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return "Over " + number + " [" + sequence() + "]";
    }
}
