package com.cricket.od;

import com.cricket.core.model.Match;
import com.cricket.core.model.MatchFormat;
import com.cricket.core.model.MatchState;
import com.cricket.core.registry.MatchRegistry;
import com.cricket.core.registry.SquadCatalog;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The queries the scoreboard app makes against the directory of matches.
 */
public class LiveMatchQueryTest {

    private static Match match(String id) {
        return new Match(id, SquadCatalog.require("IND"), SquadCatalog.require("AUS"),
                MatchFormat.T20, "Eden Gardens");
    }

    /** Takes a scheduled fixture through the toss and out to the middle. */
    private static Match underway(String id) {
        Match match = match(id);
        match.transitionTo(MatchState.TOSS_DONE);
        match.transitionTo(MatchState.IN_PROGRESS);
        return match;
    }

    /**
     * Before any fixture has been set up the directory has nothing in it.
     */
    @Test
    public void theDirectoryStartsEmpty() {
        MatchRegistry registry = MatchRegistry.getInstance();
        assertTrue("directory held " + registry.size() + " matches: " + registry.matchIds(),
                registry.isEmpty());
    }

    /**
     * Only fixtures that have actually got under way are served on the live endpoint.
     */
    @Test
    public void onlyMatchesInProgressAreLive() {
        MatchRegistry registry = MatchRegistry.getInstance();
        registry.register(underway("LIVE-1"));
        registry.register(match("LIVE-2"));

        List<Match> live = registry.live();
        assertEquals(1, live.size());
        assertEquals("LIVE-1", live.get(0).getId());

        registry.remove("LIVE-1");
        registry.remove("LIVE-2");
    }

    @Test
    public void aRegisteredMatchCanBeFoundById() {
        MatchRegistry registry = MatchRegistry.getInstance();
        registry.register(match("FIND-1"));
        assertEquals("FIND-1", registry.require("FIND-1").getId());
        registry.remove("FIND-1");
    }

    @Test
    public void anUnknownMatchIdIsNotFound() {
        assertNull(MatchRegistry.getInstance().find("NO-SUCH-MATCH"));
    }

    @Test
    public void requiringAnUnknownMatchThrows() {
        try {
            MatchRegistry.getInstance().require("NO-SUCH-MATCH");
            org.junit.Assert.fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("no such match"));
        }
    }

    @Test
    public void aMatchIsFoundByEitherSide() {
        MatchRegistry registry = MatchRegistry.getInstance();
        registry.register(match("TEAM-1"));
        assertFalse(registry.involvingTeam("IND").isEmpty());
        assertFalse(registry.involvingTeam("AUS").isEmpty());
        registry.remove("TEAM-1");
    }

    @Test
    public void registeringNullIsRejected() {
        try {
            MatchRegistry.getInstance().register(null);
            org.junit.Assert.fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertEquals("match must not be null", expected.getMessage());
        }
    }
}
