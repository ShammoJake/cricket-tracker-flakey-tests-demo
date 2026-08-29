package com.cricket.core.engine;

/**
 * Duckworth-Lewis-Stern style resource calculations for rain-affected matches.
 *
 * <p>Built on the two-parameter exponential-decay model that underpins the published
 * resource tables: the resource still available to a side with {@code u} overs left
 * and {@code w} wickets down is
 *
 * <pre>Z(u, w) = Z0(w) * (1 - exp(-b(w) * u))</pre>
 *
 * <p>expressed as a percentage of {@code Z(50, 0)}, a full 50-over innings.
 */
public final class DlsCalculator {

    /** Asymptotic average total for a side that has lost w wickets. */
    private static final double[] Z0 = {
            285.0, 258.4, 228.5, 197.1, 166.1, 136.4, 108.9, 84.5, 63.1, 44.7
    };

    /** Decay rate for a side that has lost w wickets. */
    private static final double[] B = {
            0.0357, 0.0369, 0.0392, 0.0430, 0.0481, 0.0559, 0.0684, 0.0887, 0.1216, 0.1740
    };

    /** Runs a fully-resourced 50-over innings is worth under the model. */
    public static final int G50 = 245;

    private static final double FULL_INNINGS = raw(50.0, 0);

    private static double raw(double oversRemaining, int wicketsLost) {
        double z0 = Z0[wicketsLost];
        double b = B[wicketsLost];
        return z0 * (1.0 - Math.exp(-b * oversRemaining));
    }

    /**
     * Percentage of a full 50-over innings still available.
     *
     * @param oversRemaining overs left, 0 to 50
     * @param wicketsLost    wickets down, 0 to 9
     */
    public double resourcePercentage(double oversRemaining, int wicketsLost) {
        if (oversRemaining < 0) {
            throw new IllegalArgumentException("overs remaining must not be negative");
        }
        if (oversRemaining > 50) {
            throw new IllegalArgumentException("overs remaining must not exceed 50");
        }
        if (wicketsLost < 0 || wicketsLost > 9) {
            throw new IllegalArgumentException("wickets lost must be between 0 and 9");
        }
        return 100.0 * raw(oversRemaining, wicketsLost) / FULL_INNINGS;
    }

    /** Resource consumed by an innings cut short, as a percentage. */
    public double resourceUsed(double oversAvailableAtStart, double oversRemaining, int wicketsLost) {
        double atStart = resourcePercentage(oversAvailableAtStart, 0);
        double left = resourcePercentage(oversRemaining, wicketsLost);
        return atStart - left;
    }

    /**
     * Target for the side batting second.
     *
     * <p>When the chasing side has fewer resources the target is scaled down; when it
     * has more, the shortfall is topped up using {@link #G50}. The target is the score
     * needed to win, so it is one more than the par score.
     */
    public int revisedTarget(int firstInningsScore, double team1Resource, double team2Resource) {
        if (firstInningsScore < 0) {
            throw new IllegalArgumentException("first innings score must not be negative");
        }
        if (team1Resource <= 0 || team2Resource <= 0) {
            throw new IllegalArgumentException("resources must be positive");
        }
        double par;
        if (team2Resource <= team1Resource) {
            par = firstInningsScore * (team2Resource / team1Resource);
        } else {
            par = firstInningsScore + G50 * (team2Resource - team1Resource) / 100.0;
        }
        return (int) Math.floor(par) + 1;
    }

    /**
     * Par score for the chasing side at this point of the innings: the score they need
     * to be level. Ahead of par wins on a washout, level ties.
     */
    public int parScore(int target, double team1Resource, double resourceRemaining) {
        if (target < 1) {
            throw new IllegalArgumentException("target must be at least 1");
        }
        double used = team1Resource - resourceRemaining;
        double par = (target - 1) * (used / team1Resource);
        return (int) Math.floor(par);
    }

    /** True when the chasing side is ahead of the DLS par score. */
    public boolean isAheadOfPar(int currentScore, int parScore) {
        return currentScore > parScore;
    }

    /** Overs remaining given the format limit and legal balls already bowled. */
    public double oversRemaining(int oversLimit, int legalBallsBowled) {
        if (oversLimit <= 0) {
            throw new IllegalArgumentException("overs limit must be positive");
        }
        if (legalBallsBowled < 0) {
            throw new IllegalArgumentException("balls bowled must not be negative");
        }
        double remaining = oversLimit - (legalBallsBowled / 6.0);
        return Math.max(0.0, remaining);
    }
}
