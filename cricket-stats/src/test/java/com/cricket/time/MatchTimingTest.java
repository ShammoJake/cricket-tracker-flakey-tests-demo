package com.cricket.time;

import com.cricket.core.model.Match;
import com.cricket.core.model.MatchFormat;
import com.cricket.core.registry.SquadCatalog;
import com.cricket.stats.MatchTimeline;
import com.cricket.stats.ScorecardExporter;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * How long the reporting work takes, and the times it gets stamped with.
 */
public class MatchTimingTest {

    /** The archive is written between overs, so it has to fit in the gap. */
    private static final long EXPORT_BUDGET_MILLIS = 2L;

    /** The overlay is redrawn for every delivery of an over. */
    private static final long OVERLAY_BUDGET_MILLIS = 1L;
    private static final int DELIVERIES_IN_AN_OVER = 6;

    /** The wickets noted on the timeline during a session. */
    private static final String[] WICKETS = {"1st wicket", "2nd wicket", "3rd wicket"};

    private ScorecardExporter exporter;

    @Before
    public void setUp() throws IOException {
        exporter = new ScorecardExporter("target/timing-exports");
        exporter.ensureDirectory();
        warmUp();
        exporter.cleanup();
    }

    @After
    public void tearDown() {
        exporter.cleanup();
    }

    /**
     * Runs both the render and the write a few times first, so what the tests measure
     * is the work itself rather than class loading and the first touch of the disk.
     */
    private void warmUp() throws IOException {
        Match warmMatch = match("WARM");
        for (int i = 0; i < 5; i++) {
            exporter.render(warmMatch);
            exporter.export(warmMatch);
        }
    }

    private static Match match(String id) {
        return new Match(id, SquadCatalog.require("IND"), SquadCatalog.require("AUS"),
                MatchFormat.T20, "Chinnaswamy");
    }

    /**
     * Writing the archive has to fit between overs.
     *
     * <p>Asserts on the wall-clock cost of the work rather than on its result, so what
     * it reports depends on how busy the machine is at the time.
     */
    @Test
    public void theArchiveIsWrittenInsideTheBudget() throws IOException {
        Match match = match("TIMED-1");

        long started = System.currentTimeMillis();
        exporter.export(match);
        long elapsed = System.currentTimeMillis() - started;

        assertTrue("export took " + elapsed + "ms, budget is " + EXPORT_BUDGET_MILLIS,
                elapsed < EXPORT_BUDGET_MILLIS);
    }

    /** Redrawing the overlay has to keep up with an over of deliveries. */
    @Test
    public void theOverlayKeepsUpWithTheDeliveries() {
        Match match = match("TIMED-2");

        long started = System.currentTimeMillis();
        String document = null;
        for (int i = 0; i < DELIVERIES_IN_AN_OVER; i++) {
            document = exporter.render(match);
        }
        long elapsed = System.currentTimeMillis() - started;

        assertNotNull(document);
        assertTrue("overlay took " + elapsed + "ms, budget is " + OVERLAY_BUDGET_MILLIS,
                elapsed < OVERLAY_BUDGET_MILLIS);
    }

    /**
     * Every wicket that falls in the session is kept on the timeline.
     *
     * <p>Entries are held against the moment they were noted at, so two wickets noted
     * inside the same clock tick land on the same key and only one survives.
     */
    @Test
    public void everyWicketIsKeptOnTheTimeline() {
        MatchTimeline timeline = new MatchTimeline();
        for (String wicket : WICKETS) {
            timeline.note(wicket);
            bowlAnOver();
        }
        assertEquals("timeline held " + timeline.events(),
                WICKETS.length, timeline.size());
    }

    /** Enough work to stand for the overs bowled between wickets. */
    private void bowlAnOver() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 600000; i++) {
            sb.append(i % 10);
        }
        if (sb.length() == 0) {
            throw new IllegalStateException("unreachable");
        }
    }

    @Test
    public void theTimelineKeepsMarkersTheCallerStamps() {
        MatchTimeline timeline = new MatchTimeline();
        for (int i = 0; i < WICKETS.length; i++) {
            timeline.noteAt(timeline.getOpenedAt() + i, WICKETS[i]);
        }
        assertEquals(WICKETS.length, timeline.size());
        assertEquals("1st wicket", timeline.first());
    }

    @Test
    public void theSpanOfAnEmptyTimelineIsZero() {
        assertEquals(0L, new MatchTimeline().spanMillis());
    }

    @Test
    public void markersAreReadBackInTheOrderTheyWereStamped() {
        MatchTimeline timeline = new MatchTimeline();
        timeline.noteAt(1000L, "toss");
        timeline.noteAt(2000L, "first session");
        timeline.noteAt(3000L, "drinks");

        List<String> events = timeline.events();
        assertEquals("toss", events.get(0));
        assertEquals("drinks", events.get(2));
        assertEquals(2000L, timeline.spanMillis());
    }

    @Test
    public void aBlankMarkerIsRejected() {
        try {
            new MatchTimeline().note("  ");
            org.junit.Assert.fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertEquals("event must not be blank", expected.getMessage());
        }
    }
}
