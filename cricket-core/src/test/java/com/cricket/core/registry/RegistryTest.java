package com.cricket.core.registry;

import com.cricket.core.Fixtures;
import com.cricket.core.engine.ScoringRules;
import com.cricket.core.model.Match;
import com.cricket.core.model.MatchFormat;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Covers {@link MatchRegistry}, {@link PlayerDirectory} and {@link ScoringRules}. */
public class RegistryTest {

    private MatchRegistry registry;
    private PlayerDirectory directory;

    @Before
    public void setUp() {
        registry = MatchRegistry.getInstance();
        registry.reset();
        directory = PlayerDirectory.getInstance();
        directory.clear();
        ScoringRules.reloadDefaults();
    }

    @After
    public void tearDown() {
        registry.reset();
        directory.clear();
        ScoringRules.reloadDefaults();
    }

    @Test
    public void theRegistryIsASingleton() {
        assertTrue(MatchRegistry.getInstance() == MatchRegistry.getInstance());
    }

    @Test
    public void aRegisteredMatchIsFound() {
        Match match = Fixtures.t20Match();
        registry.register(match);
        assertEquals(match, registry.find(match.getId()));
    }

    @Test
    public void anUnregisteredMatchIsNotFound() {
        assertNull(registry.find("nope"));
    }

    @Test
    public void requireReturnsARegisteredMatch() {
        Match match = Fixtures.t20Match();
        registry.register(match);
        assertNotNull(registry.require(match.getId()));
    }

    @Test(expected = IllegalArgumentException.class)
    public void requireRaisesForAnUnknownMatch() {
        registry.require("nope");
    }

    @Test(expected = IllegalArgumentException.class)
    public void registeringNullIsRejected() {
        registry.register(null);
    }

    @Test
    public void registeringTracksTheCount() {
        registry.register(Fixtures.t20Match());
        assertEquals(1, registry.size());
        assertEquals(1, registry.getRegistrations());
    }

    @Test
    public void reRegisteringTheSameIdReplacesIt() {
        registry.register(Fixtures.t20Match());
        registry.register(Fixtures.t20Match());
        assertEquals(1, registry.size());
        assertEquals(2, registry.getRegistrations());
    }

    @Test
    public void containsTracksMembership() {
        registry.register(Fixtures.t20Match());
        assertTrue(registry.contains("IND-AUS-T20"));
        assertFalse(registry.contains("nope"));
    }

    @Test
    public void aMatchCanBeRemoved() {
        registry.register(Fixtures.t20Match());
        assertNotNull(registry.remove("IND-AUS-T20"));
        assertTrue(registry.isEmpty());
    }

    @Test
    public void removingAnUnknownMatchYieldsNull() {
        assertNull(registry.remove("nope"));
    }

    @Test
    public void allListsEveryMatch() {
        registry.register(Fixtures.t20Match());
        registry.register(Fixtures.odiMatch());
        assertEquals(2, registry.all().size());
        assertEquals(2, registry.matchIds().size());
    }

    @Test
    public void matchesAreFilteredByTeam() {
        registry.register(Fixtures.t20Match());
        assertEquals(1, registry.involvingTeam("IND").size());
        assertEquals(0, registry.involvingTeam("ENG").size());
    }

    @Test
    public void onlyInProgressMatchesAreLive() {
        Match match = Fixtures.t20Match();
        registry.register(match);
        assertEquals(0, registry.live().size());
        match.recordToss("IND", true);
        match.transitionTo(com.cricket.core.model.MatchState.IN_PROGRESS);
        assertEquals(1, registry.live().size());
    }

    @Test
    public void resetEmptiesTheRegistry() {
        registry.register(Fixtures.t20Match());
        registry.reset();
        assertTrue(registry.isEmpty());
        assertEquals(0, registry.getRegistrations());
    }

    @Test
    public void theDirectoryIsASingleton() {
        assertTrue(PlayerDirectory.getInstance() == PlayerDirectory.getInstance());
    }

    @Test
    public void aFreshDirectoryIsEmpty() {
        assertTrue(directory.isEmpty());
        assertEquals(0, directory.size());
    }

    @Test
    public void importingASquadPopulatesTheDirectory() {
        directory.importSquad(Fixtures.india());
        assertEquals(11, directory.size());
        assertEquals(1, directory.getImportCount());
    }

    @Test(expected = IllegalArgumentException.class)
    public void importingANullSquadIsRejected() {
        directory.importSquad(null);
    }

    @Test
    public void aPlayerIsResolvedById() {
        directory.importSquad(Fixtures.india());
        assertEquals("Virat Kohli", directory.byId("IND3").getFullName());
    }

    @Test
    public void aPlayerIsResolvedByShortName() {
        directory.importSquad(Fixtures.india());
        assertEquals("IND3", directory.byShortName("V Kohli").getId());
    }

    @Test
    public void shortNameLookupIgnoresCase() {
        directory.importSquad(Fixtures.india());
        assertNotNull(directory.byShortName("v kohli"));
    }

    @Test
    public void anUnimportedShortNameIsNotFound() {
        assertNull(directory.byShortName("V Kohli"));
    }

    @Test(expected = IllegalStateException.class)
    public void requireRaisesForAnUnimportedShortName() {
        directory.requireByShortName("V Kohli");
    }

    @Test
    public void aSinglePlayerCanBeAdded() {
        directory.add(new com.cricket.core.model.Player(
                "ENG1", "Joe Root", com.cricket.core.model.PlayerRole.BATTER));
        assertTrue(directory.knows("ENG1"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void addingANullPlayerIsRejected() {
        directory.add(null);
    }

    @Test
    public void searchMatchesOnAFragment() {
        directory.importSquad(Fixtures.india());
        assertEquals(1, directory.search("Kohli").size());
    }

    @Test
    public void searchMatchesSeveralPlayers() {
        directory.importSquad(Fixtures.india());
        assertEquals(2, directory.search("Mohammed").size());
    }

    @Test
    public void anEmptySearchYieldsNothing() {
        directory.importSquad(Fixtures.india());
        assertTrue(directory.search("").isEmpty());
        assertTrue(directory.search(null).isEmpty());
    }

    @Test
    public void clearEmptiesTheDirectory() {
        directory.importSquad(Fixtures.india());
        directory.clear();
        assertTrue(directory.isEmpty());
        assertEquals(0, directory.getImportCount());
    }

    @Test
    public void twoSquadsBothLand() {
        directory.importSquad(Fixtures.india());
        directory.importSquad(Fixtures.australia());
        assertEquals(22, directory.size());
        assertEquals(2, directory.getImportCount());
    }

    @Test
    public void theRulesStartAtTheirDefaults() {
        assertTrue(ScoringRules.isDefault());
        assertEquals(6, ScoringRules.ballsPerOver());
        assertEquals(1, ScoringRules.widePenalty());
        assertTrue(ScoringRules.freeHitAfterNoBall());
        assertFalse(ScoringRules.wideCountsAsBall());
    }

    @Test
    public void changingARuleLeavesTheDefaults() {
        ScoringRules.setFreeHitAfterNoBall(false);
        assertFalse(ScoringRules.isDefault());
    }

    @Test
    public void reloadingRestoresTheDefaults() {
        ScoringRules.setBallsPerOver(8);
        ScoringRules.setWidePenalty(2);
        ScoringRules.reloadDefaults();
        assertTrue(ScoringRules.isDefault());
    }

    @Test
    public void testFormatDefaultsDisableTheFreeHit() {
        ScoringRules.applyFormatDefaults(MatchFormat.TEST);
        assertFalse(ScoringRules.freeHitAfterNoBall());
    }

    @Test
    public void t20FormatDefaultsEnableTheFreeHit() {
        ScoringRules.applyFormatDefaults(MatchFormat.T20);
        assertTrue(ScoringRules.freeHitAfterNoBall());
    }

    @Test(expected = IllegalArgumentException.class)
    public void aNullFormatIsRejected() {
        ScoringRules.applyFormatDefaults(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void zeroBallsPerOverIsRejected() {
        ScoringRules.setBallsPerOver(0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void thirteenBallsPerOverIsRejected() {
        ScoringRules.setBallsPerOver(13);
    }

    @Test(expected = IllegalArgumentException.class)
    public void aNegativeWidePenaltyIsRejected() {
        ScoringRules.setWidePenalty(-1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void aNegativeNoBallPenaltyIsRejected() {
        ScoringRules.setNoBallPenalty(-1);
    }

    @Test
    public void theRulesDescribeThemselves() {
        assertTrue(ScoringRules.describe().contains("ballsPerOver=6"));
    }
}
