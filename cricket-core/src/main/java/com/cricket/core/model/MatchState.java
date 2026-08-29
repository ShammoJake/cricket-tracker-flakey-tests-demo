package com.cricket.core.model;

/** Lifecycle of a match. */
public enum MatchState {
    SCHEDULED,
    TOSS_DONE,
    IN_PROGRESS,
    INNINGS_BREAK,
    RAIN_DELAY,
    COMPLETED,
    ABANDONED;

    /** True when deliveries may be recorded. */
    public boolean acceptsDeliveries() {
        return this == IN_PROGRESS;
    }

    /** True when no further play is possible. */
    public boolean isTerminal() {
        return this == COMPLETED || this == ABANDONED;
    }

    /** Legal state transitions. */
    public boolean canTransitionTo(MatchState next) {
        if (next == null || next == this) {
            return false;
        }
        if (isTerminal()) {
            return false;
        }
        switch (this) {
            case SCHEDULED:
                return next == TOSS_DONE || next == ABANDONED;
            case TOSS_DONE:
                return next == IN_PROGRESS || next == ABANDONED;
            case IN_PROGRESS:
                return next == INNINGS_BREAK || next == RAIN_DELAY
                        || next == COMPLETED || next == ABANDONED;
            case INNINGS_BREAK:
            case RAIN_DELAY:
                return next == IN_PROGRESS || next == COMPLETED || next == ABANDONED;
            default:
                return false;
        }
    }
}
