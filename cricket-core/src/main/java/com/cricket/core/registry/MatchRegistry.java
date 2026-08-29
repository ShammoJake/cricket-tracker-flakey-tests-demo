package com.cricket.core.registry;

import com.cricket.core.model.Match;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Process-wide directory of live matches.
 *
 * <p>A singleton because the ingest path, the query path and the exporter all need to
 * reach the same {@link Match} instances without threading a reference through every
 * call. {@link #reset()} clears it, which is what the admin shutdown endpoint calls.
 */
public final class MatchRegistry {

    private static final MatchRegistry INSTANCE = new MatchRegistry();

    private final Map<String, Match> matches = new HashMap<String, Match>();
    private long registrations;

    private MatchRegistry() {
    }

    public static MatchRegistry getInstance() {
        return INSTANCE;
    }

    public void register(Match match) {
        if (match == null) {
            throw new IllegalArgumentException("match must not be null");
        }
        matches.put(match.getId(), match);
        registrations++;
    }

    public Match find(String matchId) {
        return matches.get(matchId);
    }

    /** Throws when the match is not registered. */
    public Match require(String matchId) {
        Match match = matches.get(matchId);
        if (match == null) {
            throw new IllegalArgumentException("no such match: " + matchId);
        }
        return match;
    }

    public boolean contains(String matchId) {
        return matches.containsKey(matchId);
    }

    public Match remove(String matchId) {
        return matches.remove(matchId);
    }

    public int size() {
        return matches.size();
    }

    public boolean isEmpty() {
        return matches.isEmpty();
    }

    /** Total registrations since the last reset, including replaced entries. */
    public long getRegistrations() {
        return registrations;
    }

    public List<Match> all() {
        return new ArrayList<Match>(matches.values());
    }

    public List<String> matchIds() {
        return new ArrayList<String>(matches.keySet());
    }

    /** Matches involving the given team. */
    public List<Match> involvingTeam(String teamId) {
        List<Match> result = new ArrayList<Match>();
        for (Match m : matches.values()) {
            if (m.involves(teamId)) {
                result.add(m);
            }
        }
        return result;
    }

    /** Matches currently accepting deliveries. */
    public List<Match> live() {
        List<Match> result = new ArrayList<Match>();
        for (Match m : matches.values()) {
            if (m.getState().acceptsDeliveries()) {
                result.add(m);
            }
        }
        return result;
    }

    /** Empties the registry and zeroes the registration counter. */
    public void reset() {
        matches.clear();
        registrations = 0;
    }
}
