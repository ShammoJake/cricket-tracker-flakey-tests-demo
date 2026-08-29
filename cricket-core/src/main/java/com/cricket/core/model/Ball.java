package com.cricket.core.model;

import java.util.Objects;

/**
 * A single delivery. Immutable; built through {@link Builder}.
 *
 * <p>{@code over} is zero-based and {@code ballInOver} is one-based, so the third
 * delivery of the first over is over 0, ball 3, and displays as "0.3".
 */
public final class Ball {

    private final int over;
    private final int ballInOver;
    private final String bowlerId;
    private final String strikerId;
    private final String nonStrikerId;
    private final int runsOffBat;
    private final Extra extra;
    private final WicketEvent wicket;
    private final long timestampMillis;

    private Ball(Builder b) {
        this.over = b.over;
        this.ballInOver = b.ballInOver;
        this.bowlerId = b.bowlerId;
        this.strikerId = b.strikerId;
        this.nonStrikerId = b.nonStrikerId;
        this.runsOffBat = b.runsOffBat;
        this.extra = b.extra;
        this.wicket = b.wicket;
        this.timestampMillis = b.timestampMillis;
    }

    public int getOver() {
        return over;
    }

    public int getBallInOver() {
        return ballInOver;
    }

    public String getBowlerId() {
        return bowlerId;
    }

    public String getStrikerId() {
        return strikerId;
    }

    public String getNonStrikerId() {
        return nonStrikerId;
    }

    public int getRunsOffBat() {
        return runsOffBat;
    }

    /** May be null when the delivery conceded no extras. */
    public Extra getExtra() {
        return extra;
    }

    /** May be null when no wicket fell. */
    public WicketEvent getWicket() {
        return wicket;
    }

    public long getTimestampMillis() {
        return timestampMillis;
    }

    public boolean hasExtra() {
        return extra != null;
    }

    public boolean isWicket() {
        return wicket != null;
    }

    /** True when the delivery counts towards the six balls of the over. */
    public boolean isLegalDelivery() {
        return extra == null || extra.isLegalDelivery();
    }

    /** Runs off the bat plus penalty plus additional extra runs. */
    public int totalRuns() {
        return runsOffBat + extraRuns();
    }

    /** Runs recorded as extras, including the automatic penalty for wide/no-ball. */
    public int extraRuns() {
        if (extra == null) {
            return 0;
        }
        int penalty = extra.getType().requiresRebowl() ? 1 : 0;
        return penalty + extra.getRuns();
    }

    /** Runs charged against the bowler's analysis. */
    public int runsChargedToBowler() {
        int charged = runsOffBat;
        if (extra != null && extra.getType().isChargedToBowler()) {
            charged += 1 + extra.getRuns();
        }
        return charged;
    }

    /** Display form, e.g. "12.3". */
    public String address() {
        return over + "." + ballInOver;
    }

    public Builder toBuilder() {
        return new Builder()
                .over(over)
                .ballInOver(ballInOver)
                .bowler(bowlerId)
                .striker(strikerId)
                .nonStriker(nonStrikerId)
                .runsOffBat(runsOffBat)
                .extra(extra)
                .wicket(wicket)
                .timestampMillis(timestampMillis);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Ball)) {
            return false;
        }
        Ball other = (Ball) o;
        return over == other.over
                && ballInOver == other.ballInOver
                && runsOffBat == other.runsOffBat
                && Objects.equals(bowlerId, other.bowlerId)
                && Objects.equals(strikerId, other.strikerId)
                && Objects.equals(nonStrikerId, other.nonStrikerId)
                && Objects.equals(extra, other.extra)
                && Objects.equals(wicket, other.wicket);
    }

    @Override
    public int hashCode() {
        return Objects.hash(over, ballInOver, bowlerId, strikerId, nonStrikerId,
                runsOffBat, extra, wicket);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(address());
        sb.append(' ').append(runsOffBat);
        if (extra != null) {
            sb.append(' ').append(extra);
        }
        if (wicket != null) {
            sb.append(" W");
        }
        return sb.toString();
    }

    /** Builder for {@link Ball}. Validation of cricket rules lives in BallValidator. */
    public static final class Builder {
        private int over;
        private int ballInOver = 1;
        private String bowlerId;
        private String strikerId;
        private String nonStrikerId;
        private int runsOffBat;
        private Extra extra;
        private WicketEvent wicket;
        private long timestampMillis;

        public Builder over(int over) {
            this.over = over;
            return this;
        }

        public Builder ballInOver(int ballInOver) {
            this.ballInOver = ballInOver;
            return this;
        }

        public Builder bowler(String bowlerId) {
            this.bowlerId = bowlerId;
            return this;
        }

        public Builder striker(String strikerId) {
            this.strikerId = strikerId;
            return this;
        }

        public Builder nonStriker(String nonStrikerId) {
            this.nonStrikerId = nonStrikerId;
            return this;
        }

        public Builder runsOffBat(int runsOffBat) {
            this.runsOffBat = runsOffBat;
            return this;
        }

        public Builder extra(Extra extra) {
            this.extra = extra;
            return this;
        }

        public Builder extra(ExtraType type, int runs) {
            this.extra = new Extra(type, runs);
            return this;
        }

        public Builder wicket(WicketEvent wicket) {
            this.wicket = wicket;
            return this;
        }

        public Builder timestampMillis(long timestampMillis) {
            this.timestampMillis = timestampMillis;
            return this;
        }

        public Ball build() {
            if (bowlerId == null || bowlerId.trim().isEmpty()) {
                throw new IllegalArgumentException("ball requires a bowler");
            }
            if (strikerId == null || strikerId.trim().isEmpty()) {
                throw new IllegalArgumentException("ball requires a striker");
            }
            if (nonStrikerId == null || nonStrikerId.trim().isEmpty()) {
                throw new IllegalArgumentException("ball requires a non-striker");
            }
            return new Ball(this);
        }
    }
}
