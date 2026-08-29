package com.cricket.od;

import com.cricket.core.model.Match;
import com.cricket.core.model.MatchFormat;
import com.cricket.core.registry.SquadCatalog;
import com.cricket.stats.ScorecardExporter;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Archiving completed fixtures for the season's record.
 *
 * <p>Exports are deliberately kept after the run so the archive can be inspected when
 * a scorecard is queried.
 */
public class MatchArchiveTest {

    private static final String[] SEASON = {"ARCH-1", "ARCH-2", "ARCH-3"};

    private static Match match(String id) {
        return new Match(id, SquadCatalog.require("ENG"), SquadCatalog.require("SA"),
                MatchFormat.ODI, "Lord's");
    }

    @Test
    public void everyFixtureOfTheSeasonIsArchived() throws IOException {
        ScorecardExporter exporter = new ScorecardExporter();
        for (String id : SEASON) {
            exporter.export(match(id));
        }
        for (String id : SEASON) {
            assertTrue(id + " was not archived", exporter.hasExport(id));
        }
    }

    @Test
    public void anArchivedFileCarriesTheMatchId() throws IOException {
        ScorecardExporter exporter = new ScorecardExporter();
        File written = exporter.export(match("ARCH-4"));
        assertEquals("ARCH-4.json", written.getName());
        assertTrue(written.isFile());
    }

    @Test
    public void theArchivedDocumentRecordsTheVenue() {
        ScorecardExporter exporter = new ScorecardExporter();
        assertTrue(exporter.render(match("ARCH-5")).contains("Lord's"));
    }

    @Test
    public void theArchiveListingIsSorted() throws IOException {
        ScorecardExporter exporter = new ScorecardExporter();
        exporter.export(match("ARCH-9"));
        exporter.export(match("ARCH-6"));
        java.util.List<String> names = exporter.listExports();
        java.util.List<String> sorted = new java.util.ArrayList<String>(names);
        java.util.Collections.sort(sorted);
        assertEquals(sorted, names);
    }
}
