package com.cricket.od;

import com.cricket.core.model.Match;
import com.cricket.core.model.MatchFormat;
import com.cricket.core.model.MatchState;
import com.cricket.core.registry.MatchRegistry;
import com.cricket.core.registry.SquadCatalog;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Setting up the fixtures for a bilateral series.
 *
 * <p>The tour is registered once, the way an operator schedules it before the first
 * ball, and stays registered for as long as the series is being played.
 */
public class SeriesSetupTest {

    private static final int MATCHES_IN_SERIES = 5;

    private static Match fixture(int number) {
        return new Match("SERIES-" + number, SquadCatalog.require("IND"),
                SquadCatalog.require("ENG"), MatchFormat.ODI, "Venue " + number);
    }

    @BeforeClass
    public static void scheduleTheTour() {
        MatchRegistry registry = MatchRegistry.getInstance();
        for (int i = 1; i <= MATCHES_IN_SERIES; i++) {
            registry.register(fixture(i));
        }
        Match opener = registry.require("SERIES-1");
        opener.transitionTo(MatchState.TOSS_DONE);
        opener.transitionTo(MatchState.IN_PROGRESS);
    }

    @Test
    public void theWholeSeriesIsRegistered() {
        MatchRegistry registry = MatchRegistry.getInstance();
        for (int i = 1; i <= MATCHES_IN_SERIES; i++) {
            assertTrue("SERIES-" + i + " was not scheduled", registry.contains("SERIES-" + i));
        }
    }

    @Test
    public void theOpeningFixtureIsUnderWay() {
        MatchRegistry registry = MatchRegistry.getInstance();
        assertTrue(registry.require("SERIES-1").getState().acceptsDeliveries());
    }

    @Test
    public void theOpenerIsServedOnTheLiveEndpoint() {
        MatchRegistry registry = MatchRegistry.getInstance();
        assertTrue(registry.live().contains(registry.require("SERIES-1")));
    }

    @Test
    public void everyFixtureInvolvesBothTouringSides() {
        Match match = MatchRegistry.getInstance().require("SERIES-2");
        assertTrue(match.involves("IND"));
        assertTrue(match.involves("ENG"));
    }

    @Test
    public void theRegistrationCounterAdvancesWithEachFixture() {
        MatchRegistry registry = MatchRegistry.getInstance();
        long before = registry.getRegistrations();
        registry.register(fixture(9));
        assertEquals(before + 1, registry.getRegistrations());
    }

    @Test
    public void aReplacedFixtureKeepsTheSameId() {
        MatchRegistry registry = MatchRegistry.getInstance();
        int size = registry.size();
        registry.register(fixture(3));
        assertEquals(size, registry.size());
    }
}
