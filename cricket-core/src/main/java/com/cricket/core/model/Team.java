package com.cricket.core.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** A squad of players. The batting order is the order of the squad list. */
public final class Team {

    private final String id;
    private final String name;
    private final List<Player> squad;
    private final Map<String, Player> byId;

    public Team(String id, String name, List<Player> squad) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("team id must not be blank");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("team name must not be blank");
        }
        if (squad == null || squad.isEmpty()) {
            throw new IllegalArgumentException("team squad must not be empty");
        }
        this.id = id;
        this.name = name;
        this.squad = Collections.unmodifiableList(new ArrayList<Player>(squad));
        Map<String, Player> index = new LinkedHashMap<String, Player>();
        for (Player p : this.squad) {
            if (index.put(p.getId(), p) != null) {
                throw new IllegalArgumentException("duplicate player in squad: " + p.getId());
            }
        }
        this.byId = Collections.unmodifiableMap(index);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public List<Player> getSquad() {
        return squad;
    }

    public int size() {
        return squad.size();
    }

    public Player playerById(String playerId) {
        return byId.get(playerId);
    }

    public boolean contains(String playerId) {
        return byId.containsKey(playerId);
    }

    /** Batting position is 1-based; returns null when out of range. */
    public Player batterAtPosition(int position) {
        if (position < 1 || position > squad.size()) {
            return null;
        }
        return squad.get(position - 1);
    }

    public List<Player> bowlers() {
        List<Player> result = new ArrayList<Player>();
        for (Player p : squad) {
            if (p.getRole().canBowl()) {
                result.add(p);
            }
        }
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Team)) {
            return false;
        }
        return id.equals(((Team) o).id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return name + " (" + squad.size() + ")";
    }
}
