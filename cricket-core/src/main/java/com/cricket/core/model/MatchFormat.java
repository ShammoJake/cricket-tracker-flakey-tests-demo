package com.cricket.core.model;

/**
 * Match format. Drives over limits and which scoring rules apply by default,
 * e.g. free hits are a limited-overs concept.
 */
public enum MatchFormat {
    T20(20, 4, true),
    ODI(50, 10, true),
    TEST(-1, -1, false);

    private final int oversPerInnings;
    private final int maxOversPerBowler;
    private final boolean freeHitByDefault;

    MatchFormat(int oversPerInnings, int maxOversPerBowler, boolean freeHitByDefault) {
        this.oversPerInnings = oversPerInnings;
        this.maxOversPerBowler = maxOversPerBowler;
        this.freeHitByDefault = freeHitByDefault;
    }

    /** Overs per innings, or -1 for unlimited (Tests). */
    public int getOversPerInnings() {
        return oversPerInnings;
    }

    /** Per-bowler over cap, or -1 when uncapped. */
    public int getMaxOversPerBowler() {
        return maxOversPerBowler;
    }

    public boolean isFreeHitByDefault() {
        return freeHitByDefault;
    }

    public boolean isLimitedOvers() {
        return oversPerInnings > 0;
    }
}
