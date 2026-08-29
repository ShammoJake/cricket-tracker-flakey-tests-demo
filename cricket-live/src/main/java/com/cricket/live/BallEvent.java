package com.cricket.live;

import com.cricket.core.engine.ScoringResult;
import com.cricket.core.model.Ball;
import com.cricket.core.model.Innings;

/** A delivery that has been applied to an innings, published to the listeners. */
public final class BallEvent {

    private final String matchId;
    private final Innings innings;
    private final Ball ball;
    private final ScoringResult result;
    private final long sequence;

    public BallEvent(String matchId, Innings innings, Ball ball, ScoringResult result, long sequence) {
        if (matchId == null || matchId.trim().isEmpty()) {
            throw new IllegalArgumentException("matchId must not be blank");
        }
        if (innings == null) {
            throw new IllegalArgumentException("innings must not be null");
        }
        if (ball == null) {
            throw new IllegalArgumentException("ball must not be null");
        }
        this.matchId = matchId;
        this.innings = innings;
        this.ball = ball;
        this.result = result;
        this.sequence = sequence;
    }

    public String getMatchId() {
        return matchId;
    }

    public Innings getInnings() {
        return innings;
    }

    public Ball getBall() {
        return ball;
    }

    public ScoringResult getResult() {
        return result;
    }

    /** Monotonic sequence number assigned at submission. */
    public long getSequence() {
        return sequence;
    }

    public String getStrikerId() {
        return ball.getStrikerId();
    }

    public String getBowlerId() {
        return ball.getBowlerId();
    }

    public boolean isWicket() {
        return ball.isWicket();
    }

    @Override
    public String toString() {
        return "#" + sequence + " " + matchId + " " + ball.address();
    }
}
