package com.cricket.core.model;

import java.util.Objects;

/** A dismissal on a delivery. */
public final class WicketEvent {

    private final Dismissal dismissal;
    private final String batterId;
    private final String fielderId;

    public WicketEvent(Dismissal dismissal, String batterId, String fielderId) {
        if (dismissal == null) {
            throw new IllegalArgumentException("dismissal must not be null");
        }
        if (batterId == null || batterId.trim().isEmpty()) {
            throw new IllegalArgumentException("dismissed batter must not be blank");
        }
        if (dismissal.requiresFielder() && (fielderId == null || fielderId.trim().isEmpty())) {
            throw new IllegalArgumentException(dismissal + " requires a fielder");
        }
        this.dismissal = dismissal;
        this.batterId = batterId;
        this.fielderId = fielderId;
    }

    public static WicketEvent of(Dismissal dismissal, String batterId) {
        return new WicketEvent(dismissal, batterId, null);
    }

    public Dismissal getDismissal() {
        return dismissal;
    }

    public String getBatterId() {
        return batterId;
    }

    /** May be null for dismissals that do not involve a fielder. */
    public String getFielderId() {
        return fielderId;
    }

    public boolean isCreditedToBowler() {
        return dismissal.isCreditedToBowler();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof WicketEvent)) {
            return false;
        }
        WicketEvent other = (WicketEvent) o;
        return dismissal == other.dismissal
                && batterId.equals(other.batterId)
                && Objects.equals(fielderId, other.fielderId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dismissal, batterId, fielderId);
    }

    @Override
    public String toString() {
        return batterId + " " + dismissal + (fielderId != null ? " (" + fielderId + ")" : "");
    }
}
