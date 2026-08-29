package com.cricket.core.model;

/**
 * Kinds of extra. The two axes that matter for scoring are whether the delivery
 * counts as a legal ball in the over, and whether the penalty run is charged to
 * the bowler's analysis.
 */
public enum ExtraType {
    /** Wide: illegal delivery, penalty charged to the bowler. */
    WIDE(false, true),
    /** No-ball: illegal delivery, penalty charged to the bowler. */
    NO_BALL(false, true),
    /** Bye: legal delivery, runs are not charged to the bowler. */
    BYE(true, false),
    /** Leg-bye: legal delivery, runs are not charged to the bowler. */
    LEG_BYE(true, false),
    /** Penalty runs (e.g. five for a fielding infringement). */
    PENALTY(true, false);

    private final boolean legalDelivery;
    private final boolean chargedToBowler;

    ExtraType(boolean legalDelivery, boolean chargedToBowler) {
        this.legalDelivery = legalDelivery;
        this.chargedToBowler = chargedToBowler;
    }

    /** True when the delivery still counts towards the six balls of the over. */
    public boolean isLegalDelivery() {
        return legalDelivery;
    }

    /** True when the runs count against the bowler's figures. */
    public boolean isChargedToBowler() {
        return chargedToBowler;
    }

    /** Wides and no-balls have to be re-bowled. */
    public boolean requiresRebowl() {
        return !legalDelivery;
    }
}
