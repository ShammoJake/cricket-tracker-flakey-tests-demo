package com.cricket.core.engine;

import com.cricket.core.model.MatchFormat;

/**
 * Playing conditions applied by the scoring engine.
 *
 * <p>Held as process-wide static configuration because the conditions are fixed for
 * a competition and read on the hot path of every delivery. {@link #reloadDefaults()}
 * restores the shipped values.
 */
public final class ScoringRules {

    public static final int DEFAULT_BALLS_PER_OVER = 6;
    public static final int DEFAULT_WIDE_PENALTY = 1;
    public static final int DEFAULT_NO_BALL_PENALTY = 1;
    public static final boolean DEFAULT_FREE_HIT_AFTER_NO_BALL = true;
    public static final boolean DEFAULT_WIDE_COUNTS_AS_BALL = false;

    private static int ballsPerOver = DEFAULT_BALLS_PER_OVER;
    private static int widePenalty = DEFAULT_WIDE_PENALTY;
    private static int noBallPenalty = DEFAULT_NO_BALL_PENALTY;
    private static boolean freeHitAfterNoBall = DEFAULT_FREE_HIT_AFTER_NO_BALL;
    private static boolean wideCountsAsBall = DEFAULT_WIDE_COUNTS_AS_BALL;

    private ScoringRules() {
    }

    public static int ballsPerOver() {
        return ballsPerOver;
    }

    public static int widePenalty() {
        return widePenalty;
    }

    public static int noBallPenalty() {
        return noBallPenalty;
    }

    public static boolean freeHitAfterNoBall() {
        return freeHitAfterNoBall;
    }

    public static boolean wideCountsAsBall() {
        return wideCountsAsBall;
    }

    public static void setBallsPerOver(int value) {
        if (value < 1 || value > 12) {
            throw new IllegalArgumentException("balls per over must be between 1 and 12");
        }
        ballsPerOver = value;
    }

    public static void setWidePenalty(int value) {
        if (value < 0) {
            throw new IllegalArgumentException("wide penalty must not be negative");
        }
        widePenalty = value;
    }

    public static void setNoBallPenalty(int value) {
        if (value < 0) {
            throw new IllegalArgumentException("no-ball penalty must not be negative");
        }
        noBallPenalty = value;
    }

    public static void setFreeHitAfterNoBall(boolean value) {
        freeHitAfterNoBall = value;
    }

    public static void setWideCountsAsBall(boolean value) {
        wideCountsAsBall = value;
    }

    /** Applies the conditions customary for the given format. */
    public static void applyFormatDefaults(MatchFormat format) {
        if (format == null) {
            throw new IllegalArgumentException("format must not be null");
        }
        ballsPerOver = DEFAULT_BALLS_PER_OVER;
        widePenalty = DEFAULT_WIDE_PENALTY;
        noBallPenalty = DEFAULT_NO_BALL_PENALTY;
        wideCountsAsBall = DEFAULT_WIDE_COUNTS_AS_BALL;
        freeHitAfterNoBall = format.isFreeHitByDefault();
    }

    /** Restores every setting to its shipped default. */
    public static void reloadDefaults() {
        ballsPerOver = DEFAULT_BALLS_PER_OVER;
        widePenalty = DEFAULT_WIDE_PENALTY;
        noBallPenalty = DEFAULT_NO_BALL_PENALTY;
        freeHitAfterNoBall = DEFAULT_FREE_HIT_AFTER_NO_BALL;
        wideCountsAsBall = DEFAULT_WIDE_COUNTS_AS_BALL;
    }

    /** True when every setting currently matches its shipped default. */
    public static boolean isDefault() {
        return ballsPerOver == DEFAULT_BALLS_PER_OVER
                && widePenalty == DEFAULT_WIDE_PENALTY
                && noBallPenalty == DEFAULT_NO_BALL_PENALTY
                && freeHitAfterNoBall == DEFAULT_FREE_HIT_AFTER_NO_BALL
                && wideCountsAsBall == DEFAULT_WIDE_COUNTS_AS_BALL;
    }

    public static String describe() {
        return "ballsPerOver=" + ballsPerOver
                + ", widePenalty=" + widePenalty
                + ", noBallPenalty=" + noBallPenalty
                + ", freeHit=" + freeHitAfterNoBall
                + ", wideCountsAsBall=" + wideCountsAsBall;
    }
}
