package com.cricket.core.model;

import java.util.Objects;

/**
 * Extras conceded on a delivery.
 *
 * <p>{@code runs} is the additional runs beyond the automatic penalty. Four wides
 * down the leg side is {@code new Extra(WIDE, 4)}: one penalty run plus four run.
 */
public final class Extra {

    private final ExtraType type;
    private final int runs;

    public Extra(ExtraType type, int runs) {
        if (type == null) {
            throw new IllegalArgumentException("extra type must not be null");
        }
        if (runs < 0) {
            throw new IllegalArgumentException("extra runs must not be negative");
        }
        this.type = type;
        this.runs = runs;
    }

    public static Extra of(ExtraType type) {
        return new Extra(type, 0);
    }

    public ExtraType getType() {
        return type;
    }

    /** Additional runs beyond the automatic penalty. */
    public int getRuns() {
        return runs;
    }

    public boolean isLegalDelivery() {
        return type.isLegalDelivery();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Extra)) {
            return false;
        }
        Extra other = (Extra) o;
        return runs == other.runs && type == other.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, runs);
    }

    @Override
    public String toString() {
        return type + (runs > 0 ? "+" + runs : "");
    }
}
