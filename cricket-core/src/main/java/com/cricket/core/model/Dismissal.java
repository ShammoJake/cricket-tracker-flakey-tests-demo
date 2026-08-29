package com.cricket.core.model;

/**
 * How a batter was dismissed. Two properties drive the scoring rules: whether the
 * wicket is credited to the bowler, and whether it can occur off an illegal delivery.
 */
public enum Dismissal {
    BOWLED(true, false),
    CAUGHT(true, false),
    LBW(true, false),
    STUMPED(true, true),
    HIT_WICKET(true, false),
    RUN_OUT(false, true),
    OBSTRUCTING_THE_FIELD(false, true),
    RETIRED_OUT(false, true),
    TIMED_OUT(false, true);

    private final boolean creditedToBowler;
    private final boolean allowedOffIllegalDelivery;

    Dismissal(boolean creditedToBowler, boolean allowedOffIllegalDelivery) {
        this.creditedToBowler = creditedToBowler;
        this.allowedOffIllegalDelivery = allowedOffIllegalDelivery;
    }

    /** True when the wicket appears in the bowler's figures. */
    public boolean isCreditedToBowler() {
        return creditedToBowler;
    }

    /**
     * True when this dismissal can occur off a wide or no-ball. A batter can be
     * stumped or run out off a wide, but cannot be bowled off one.
     */
    public boolean isAllowedOffIllegalDelivery() {
        return allowedOffIllegalDelivery;
    }

    /** True when a fielder must be named for the dismissal to be well-formed. */
    public boolean requiresFielder() {
        return this == CAUGHT || this == RUN_OUT || this == STUMPED;
    }
}
