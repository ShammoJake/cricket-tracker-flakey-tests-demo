package com.cricket.core.model;

import java.util.Objects;

/** An individual cricketer. Immutable. */
public final class Player {

    private final String id;
    private final String fullName;
    private final String shortName;
    private final PlayerRole role;

    public Player(String id, String fullName, String shortName, PlayerRole role) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("player id must not be blank");
        }
        if (fullName == null || fullName.trim().isEmpty()) {
            throw new IllegalArgumentException("player fullName must not be blank");
        }
        if (role == null) {
            throw new IllegalArgumentException("player role must not be null");
        }
        this.id = id;
        this.fullName = fullName;
        this.shortName = (shortName == null || shortName.trim().isEmpty())
                ? deriveShortName(fullName)
                : shortName;
        this.role = role;
    }

    public Player(String id, String fullName, PlayerRole role) {
        this(id, fullName, null, role);
    }

    /** "Joe Root" becomes "J Root"; a single-word name is left alone. */
    static String deriveShortName(String fullName) {
        String trimmed = fullName.trim();
        int lastSpace = trimmed.lastIndexOf(' ');
        if (lastSpace <= 0) {
            return trimmed;
        }
        return trimmed.charAt(0) + " " + trimmed.substring(lastSpace + 1);
    }

    public String getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public String getShortName() {
        return shortName;
    }

    public PlayerRole getRole() {
        return role;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Player)) {
            return false;
        }
        return id.equals(((Player) o).id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return shortName + " (" + id + ")";
    }
}
