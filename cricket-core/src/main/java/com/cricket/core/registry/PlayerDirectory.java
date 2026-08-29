package com.cricket.core.registry;

import com.cricket.core.model.Player;
import com.cricket.core.model.Team;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lookup of players by id and by short name, populated as squads are imported.
 *
 * <p>Process-wide and populated lazily: squads are pushed in by {@code importSquad}
 * when a match is set up, and the lookup paths read whatever has been imported so far.
 * A short name that has not been imported simply is not found.
 */
public final class PlayerDirectory {

    private static final PlayerDirectory INSTANCE = new PlayerDirectory();

    private final Map<String, Player> byId = new HashMap<String, Player>();
    private final Map<String, String> shortNameToId = new HashMap<String, String>();
    private int importCount;

    private PlayerDirectory() {
    }

    public static PlayerDirectory getInstance() {
        return INSTANCE;
    }

    /** Adds every member of the squad to the directory. */
    public void importSquad(Team team) {
        if (team == null) {
            throw new IllegalArgumentException("team must not be null");
        }
        for (Player p : team.getSquad()) {
            byId.put(p.getId(), p);
            shortNameToId.put(normalise(p.getShortName()), p.getId());
        }
        importCount++;
    }

    public void add(Player player) {
        if (player == null) {
            throw new IllegalArgumentException("player must not be null");
        }
        byId.put(player.getId(), player);
        shortNameToId.put(normalise(player.getShortName()), player.getId());
    }

    static String normalise(String name) {
        return name == null ? "" : name.trim().toLowerCase();
    }

    public Player byId(String playerId) {
        return byId.get(playerId);
    }

    /** Resolves a short name such as "V Kohli"; null when not imported. */
    public Player byShortName(String shortName) {
        String id = shortNameToId.get(normalise(shortName));
        return id == null ? null : byId.get(id);
    }

    /** Throws when the short name is not in the directory. */
    public Player requireByShortName(String shortName) {
        Player player = byShortName(shortName);
        if (player == null) {
            throw new IllegalStateException("player not in directory: " + shortName);
        }
        return player;
    }

    public boolean knows(String playerId) {
        return byId.containsKey(playerId);
    }

    public int size() {
        return byId.size();
    }

    public boolean isEmpty() {
        return byId.isEmpty();
    }

    /** Number of squads imported since the last clear. */
    public int getImportCount() {
        return importCount;
    }

    public List<Player> all() {
        return new ArrayList<Player>(byId.values());
    }

    /** Players whose full name contains the fragment, case-insensitively. */
    public List<Player> search(String fragment) {
        List<Player> result = new ArrayList<Player>();
        String needle = normalise(fragment);
        if (needle.isEmpty()) {
            return result;
        }
        for (Player p : byId.values()) {
            if (normalise(p.getFullName()).contains(needle)) {
                result.add(p);
            }
        }
        return result;
    }

    public void clear() {
        byId.clear();
        shortNameToId.clear();
        importCount = 0;
    }
}
