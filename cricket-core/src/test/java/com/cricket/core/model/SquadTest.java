package com.cricket.core.model;

import com.cricket.core.Fixtures;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Covers {@link Player} and {@link Team}. */
public class SquadTest {

    @Test
    public void aShortNameIsDerivedFromTheFullName() {
        assertEquals("J Root", new Player("ENG1", "Joe Root", PlayerRole.BATTER).getShortName());
    }

    @Test
    public void aThreePartNameKeepsTheSurname() {
        assertEquals("K Pietersen",
                new Player("ENG2", "Kevin Peter Pietersen", PlayerRole.BATTER).getShortName());
    }

    @Test
    public void aSingleWordNameIsLeftAlone() {
        assertEquals("Ashwin", new Player("IND12", "Ashwin", PlayerRole.BOWLER).getShortName());
    }

    @Test
    public void anExplicitShortNameWins() {
        assertEquals("KP", new Player("ENG2", "Kevin Pietersen", "KP", PlayerRole.BATTER).getShortName());
    }

    @Test
    public void aBlankShortNameFallsBackToTheDerivedOne() {
        assertEquals("K Pietersen",
                new Player("ENG2", "Kevin Pietersen", "  ", PlayerRole.BATTER).getShortName());
    }

    @Test(expected = IllegalArgumentException.class)
    public void aBlankPlayerIdIsRejected() {
        new Player(" ", "Joe Root", PlayerRole.BATTER);
    }

    @Test(expected = IllegalArgumentException.class)
    public void aBlankFullNameIsRejected() {
        new Player("ENG1", "  ", PlayerRole.BATTER);
    }

    @Test(expected = IllegalArgumentException.class)
    public void aNullRoleIsRejected() {
        new Player("ENG1", "Joe Root", null);
    }

    @Test
    public void playersAreEqualById() {
        assertEquals(new Player("ENG1", "Joe Root", PlayerRole.BATTER),
                new Player("ENG1", "Someone Else", PlayerRole.BOWLER));
    }

    @Test
    public void playersWithDifferentIdsAreNotEqual() {
        assertNotEquals(new Player("ENG1", "Joe Root", PlayerRole.BATTER),
                new Player("ENG2", "Joe Root", PlayerRole.BATTER));
    }

    @Test
    public void aPlayerIsNotEqualToAnUnrelatedObject() {
        assertNotEquals(new Player("ENG1", "Joe Root", PlayerRole.BATTER), "ENG1");
    }

    @Test
    public void bowlersCanBowl() {
        assertTrue(PlayerRole.BOWLER.canBowl());
        assertTrue(PlayerRole.ALL_ROUNDER.canBowl());
    }

    @Test
    public void specialistBattersDoNotBowl() {
        assertFalse(PlayerRole.BATTER.canBowl());
        assertFalse(PlayerRole.WICKET_KEEPER.canBowl());
    }

    @Test
    public void keepersCountAsSpecialistBatters() {
        assertTrue(PlayerRole.WICKET_KEEPER.isSpecialistBatter());
        assertTrue(PlayerRole.BATTER.isSpecialistBatter());
        assertFalse(PlayerRole.BOWLER.isSpecialistBatter());
    }

    @Test
    public void aSquadIsElevenStrong() {
        assertEquals(11, Fixtures.india().size());
    }

    @Test
    public void aPlayerIsFoundById() {
        assertEquals("Virat Kohli", Fixtures.india().playerById("IND3").getFullName());
    }

    @Test
    public void anUnknownPlayerIsNotFound() {
        assertNull(Fixtures.india().playerById("NOPE"));
    }

    @Test
    public void membershipIsTestable() {
        assertTrue(Fixtures.india().contains("IND3"));
        assertFalse(Fixtures.india().contains("AUS3"));
    }

    @Test
    public void battingPositionIsOneBased() {
        assertEquals("IND1", Fixtures.india().batterAtPosition(1).getId());
        assertEquals("IND11", Fixtures.india().batterAtPosition(11).getId());
    }

    @Test
    public void positionZeroIsOutOfRange() {
        assertNull(Fixtures.india().batterAtPosition(0));
    }

    @Test
    public void positionTwelveIsOutOfRange() {
        assertNull(Fixtures.india().batterAtPosition(12));
    }

    @Test
    public void bowlersAreListed() {
        assertEquals(6, Fixtures.india().bowlers().size());
    }

    @Test
    public void everyListedBowlerCanBowl() {
        for (Player p : Fixtures.australia().bowlers()) {
            assertTrue(p.getRole().canBowl());
        }
    }

    @Test(expected = UnsupportedOperationException.class)
    public void theSquadIsReadOnly() {
        Fixtures.india().getSquad().add(new Player("X", "Someone", PlayerRole.BATTER));
    }

    @Test(expected = IllegalArgumentException.class)
    public void anEmptySquadIsRejected() {
        new Team("IND", "India", Collections.<Player>emptyList());
    }

    @Test(expected = IllegalArgumentException.class)
    public void aNullSquadIsRejected() {
        new Team("IND", "India", null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void aBlankTeamIdIsRejected() {
        new Team(" ", "India", Fixtures.india().getSquad());
    }

    @Test(expected = IllegalArgumentException.class)
    public void aBlankTeamNameIsRejected() {
        new Team("IND", "", Fixtures.india().getSquad());
    }

    @Test(expected = IllegalArgumentException.class)
    public void aDuplicatePlayerInTheSquadIsRejected() {
        List<Player> squad = new ArrayList<Player>();
        squad.add(new Player("IND1", "Rohit Sharma", PlayerRole.BATTER));
        squad.add(new Player("IND1", "Rohit Sharma", PlayerRole.BATTER));
        new Team("IND", "India", squad);
    }

    @Test
    public void teamsAreEqualById() {
        assertEquals(Fixtures.india(), Fixtures.india());
    }

    @Test
    public void differentTeamsAreNotEqual() {
        assertNotEquals(Fixtures.india(), Fixtures.australia());
    }

    @Test
    public void teamsShareAHashCodeById() {
        assertEquals(Fixtures.india().hashCode(), Fixtures.india().hashCode());
    }

    @Test
    public void theSquadOrderIsTheBattingOrder() {
        List<Player> squad = Fixtures.india().getSquad();
        assertEquals("IND1", squad.get(0).getId());
        assertEquals("IND2", squad.get(1).getId());
    }

    @Test
    public void toStringCarriesTheNameAndSize() {
        assertTrue(Fixtures.india().toString().contains("India"));
        assertTrue(Fixtures.india().toString().contains("11"));
    }

    @Test
    public void aPlayerToStringCarriesTheShortNameAndId() {
        String text = new Player("IND3", "Virat Kohli", PlayerRole.BATTER).toString();
        assertTrue(text.contains("V Kohli"));
        assertTrue(text.contains("IND3"));
    }
}
