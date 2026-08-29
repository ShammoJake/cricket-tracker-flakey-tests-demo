package com.cricket.core;

import com.cricket.core.model.Ball;
import com.cricket.core.model.Innings;
import com.cricket.core.model.Match;
import com.cricket.core.model.MatchFormat;
import com.cricket.core.model.Player;
import com.cricket.core.model.PlayerRole;
import com.cricket.core.model.Team;

import java.util.ArrayList;
import java.util.List;

/** Shared test data. */
public final class Fixtures {

    private Fixtures() {
    }

    public static Team india() {
        List<Player> squad = new ArrayList<Player>();
        squad.add(new Player("IND1", "Rohit Sharma", PlayerRole.BATTER));
        squad.add(new Player("IND2", "Shubman Gill", PlayerRole.BATTER));
        squad.add(new Player("IND3", "Virat Kohli", PlayerRole.BATTER));
        squad.add(new Player("IND4", "Shreyas Iyer", PlayerRole.BATTER));
        squad.add(new Player("IND5", "KL Rahul", PlayerRole.WICKET_KEEPER));
        squad.add(new Player("IND6", "Hardik Pandya", PlayerRole.ALL_ROUNDER));
        squad.add(new Player("IND7", "Ravindra Jadeja", PlayerRole.ALL_ROUNDER));
        squad.add(new Player("IND8", "Kuldeep Yadav", PlayerRole.BOWLER));
        squad.add(new Player("IND9", "Jasprit Bumrah", PlayerRole.BOWLER));
        squad.add(new Player("IND10", "Mohammed Siraj", PlayerRole.BOWLER));
        squad.add(new Player("IND11", "Mohammed Shami", PlayerRole.BOWLER));
        return new Team("IND", "India", squad);
    }

    public static Team australia() {
        List<Player> squad = new ArrayList<Player>();
        squad.add(new Player("AUS1", "David Warner", PlayerRole.BATTER));
        squad.add(new Player("AUS2", "Travis Head", PlayerRole.BATTER));
        squad.add(new Player("AUS3", "Marnus Labuschagne", PlayerRole.BATTER));
        squad.add(new Player("AUS4", "Steve Smith", PlayerRole.BATTER));
        squad.add(new Player("AUS5", "Alex Carey", PlayerRole.WICKET_KEEPER));
        squad.add(new Player("AUS6", "Glenn Maxwell", PlayerRole.ALL_ROUNDER));
        squad.add(new Player("AUS7", "Mitchell Marsh", PlayerRole.ALL_ROUNDER));
        squad.add(new Player("AUS8", "Pat Cummins", PlayerRole.BOWLER));
        squad.add(new Player("AUS9", "Mitchell Starc", PlayerRole.BOWLER));
        squad.add(new Player("AUS10", "Josh Hazlewood", PlayerRole.BOWLER));
        squad.add(new Player("AUS11", "Adam Zampa", PlayerRole.BOWLER));
        return new Team("AUS", "Australia", squad);
    }

    public static Match t20Match() {
        return new Match("IND-AUS-T20", india(), australia(), MatchFormat.T20, "Narendra Modi Stadium");
    }

    public static Match odiMatch() {
        return new Match("IND-AUS-ODI", india(), australia(), MatchFormat.ODI, "Wankhede Stadium");
    }

    /** An innings with India batting, openers already at the crease. */
    public static Innings openedInnings() {
        Innings innings = new Innings("inn1", 1, india(), australia());
        innings.setOversLimit(20);
        innings.openWith("IND1", "IND2");
        return innings;
    }

    /** A legal dot ball from AUS8 to whoever is on strike. */
    public static Ball.Builder delivery(Innings innings, int over, int ballInOver) {
        return Ball.builder()
                .over(over)
                .ballInOver(ballInOver)
                .bowler("AUS8")
                .striker(innings.getStrikerId())
                .nonStriker(innings.getNonStrikerId());
    }
}
