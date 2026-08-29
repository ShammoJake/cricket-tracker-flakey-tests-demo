package com.cricket.core.model;

/** Primary discipline a player is picked for. */
public enum PlayerRole {
    BATTER,
    BOWLER,
    ALL_ROUNDER,
    WICKET_KEEPER;

    public boolean canBowl() {
        return this == BOWLER || this == ALL_ROUNDER;
    }

    public boolean isSpecialistBatter() {
        return this == BATTER || this == WICKET_KEEPER;
    }
}
