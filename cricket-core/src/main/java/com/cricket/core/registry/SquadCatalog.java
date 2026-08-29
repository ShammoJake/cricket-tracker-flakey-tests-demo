package com.cricket.core.registry;

import com.cricket.core.model.Player;
import com.cricket.core.model.PlayerRole;
import com.cricket.core.model.Team;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The squads the service knows about.
 *
 * <p>A real deployment would load these from the competition database; here they are
 * built in so a match can be set up from a team id alone.
 */
public final class SquadCatalog {

    private static final Map<String, Team> TEAMS = new LinkedHashMap<String, Team>();

    static {
        register(build("IND", "India", new String[][]{
                {"IND1", "Rohit Sharma", "BATTER"},
                {"IND2", "Shubman Gill", "BATTER"},
                {"IND3", "Virat Kohli", "BATTER"},
                {"IND4", "Shreyas Iyer", "BATTER"},
                {"IND5", "KL Rahul", "WICKET_KEEPER"},
                {"IND6", "Hardik Pandya", "ALL_ROUNDER"},
                {"IND7", "Ravindra Jadeja", "ALL_ROUNDER"},
                {"IND8", "Kuldeep Yadav", "BOWLER"},
                {"IND9", "Jasprit Bumrah", "BOWLER"},
                {"IND10", "Mohammed Siraj", "BOWLER"},
                {"IND11", "Mohammed Shami", "BOWLER"}}));

        register(build("AUS", "Australia", new String[][]{
                {"AUS1", "David Warner", "BATTER"},
                {"AUS2", "Travis Head", "BATTER"},
                {"AUS3", "Marnus Labuschagne", "BATTER"},
                {"AUS4", "Steve Smith", "BATTER"},
                {"AUS5", "Alex Carey", "WICKET_KEEPER"},
                {"AUS6", "Glenn Maxwell", "ALL_ROUNDER"},
                {"AUS7", "Mitchell Marsh", "ALL_ROUNDER"},
                {"AUS8", "Pat Cummins", "BOWLER"},
                {"AUS9", "Mitchell Starc", "BOWLER"},
                {"AUS10", "Josh Hazlewood", "BOWLER"},
                {"AUS11", "Adam Zampa", "BOWLER"}}));

        register(build("ENG", "England", new String[][]{
                {"ENG1", "Jonny Bairstow", "WICKET_KEEPER"},
                {"ENG2", "Ben Duckett", "BATTER"},
                {"ENG3", "Joe Root", "BATTER"},
                {"ENG4", "Harry Brook", "BATTER"},
                {"ENG5", "Ben Stokes", "ALL_ROUNDER"},
                {"ENG6", "Moeen Ali", "ALL_ROUNDER"},
                {"ENG7", "Chris Woakes", "ALL_ROUNDER"},
                {"ENG8", "Adil Rashid", "BOWLER"},
                {"ENG9", "Jofra Archer", "BOWLER"},
                {"ENG10", "Mark Wood", "BOWLER"},
                {"ENG11", "James Anderson", "BOWLER"}}));

        register(build("SA", "South Africa", new String[][]{
                {"SA1", "Quinton de Kock", "WICKET_KEEPER"},
                {"SA2", "Temba Bavuma", "BATTER"},
                {"SA3", "Rassie van der Dussen", "BATTER"},
                {"SA4", "Aiden Markram", "BATTER"},
                {"SA5", "Heinrich Klaasen", "BATTER"},
                {"SA6", "David Miller", "BATTER"},
                {"SA7", "Marco Jansen", "ALL_ROUNDER"},
                {"SA8", "Keshav Maharaj", "BOWLER"},
                {"SA9", "Kagiso Rabada", "BOWLER"},
                {"SA10", "Lungi Ngidi", "BOWLER"},
                {"SA11", "Tabraiz Shamsi", "BOWLER"}}));
    }

    private SquadCatalog() {
    }

    private static Team build(String id, String name, String[][] rows) {
        List<Player> squad = new ArrayList<Player>();
        for (String[] row : rows) {
            squad.add(new Player(row[0], row[1], PlayerRole.valueOf(row[2])));
        }
        return new Team(id, name, squad);
    }

    private static void register(Team team) {
        TEAMS.put(team.getId(), team);
    }

    /** The team with this id, or null when unknown. */
    public static Team byId(String teamId) {
        return TEAMS.get(teamId);
    }

    /** Throws when the team is not in the catalog. */
    public static Team require(String teamId) {
        Team team = TEAMS.get(teamId);
        if (team == null) {
            throw new IllegalArgumentException("unknown team: " + teamId);
        }
        return team;
    }

    public static boolean knows(String teamId) {
        return TEAMS.containsKey(teamId);
    }

    public static List<String> teamIds() {
        return Collections.unmodifiableList(new ArrayList<String>(TEAMS.keySet()));
    }

    public static int size() {
        return TEAMS.size();
    }
}
