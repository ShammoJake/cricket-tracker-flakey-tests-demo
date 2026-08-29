package com.cricket.od;

import com.cricket.core.model.Match;
import com.cricket.core.model.MatchFormat;
import com.cricket.core.registry.SquadCatalog;
import com.cricket.stats.ScorecardExporter;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The archive an operator sees when they open the export folder for a new day's play.
 */
public class ArchiveFreshnessTest {

    private static Match match(String id) {
        return new Match(id, SquadCatalog.require("IND"), SquadCatalog.require("AUS"),
                MatchFormat.T20, "Wankhede");
    }

    /**
     * Nothing has been archived yet at the start of the day, so the folder the
     * broadcast team collects from is empty.
     */
    @Test
    public void theExportDirectoryStartsClean() {
        ScorecardExporter exporter = new ScorecardExporter();
        assertTrue("archive should be empty before the day's play", exporter.isEmpty());
    }

    /** Writes to a scratch folder of its own so the shared archive is left as it was. */
    @Test
    public void anExportedMatchIsListedInTheArchive() throws IOException {
        ScorecardExporter exporter = new ScorecardExporter("target/od-scratch");
        exporter.cleanup();
        exporter.export(match("FRESH-1"));

        List<String> names = exporter.listExports();
        assertTrue(names.contains("FRESH-1.json"));
    }

    @Test
    public void theRenderedDocumentNamesTheMatch() {
        ScorecardExporter exporter = new ScorecardExporter();
        String document = exporter.render(match("FRESH-2"));
        assertTrue(document.contains("\"matchId\""));
        assertTrue(document.contains("FRESH-2"));
    }

    @Test
    public void theExporterCreatesItsDirectoryOnDemand() {
        ScorecardExporter exporter = new ScorecardExporter("target/od-scratch");
        assertTrue(exporter.ensureDirectory());
        assertTrue(new File("target/od-scratch").isDirectory());
    }

    @Test
    public void anUnexportedMatchIsNotReportedAsPresent() {
        ScorecardExporter exporter = new ScorecardExporter();
        assertFalse(exporter.hasExport("NEVER-EXPORTED"));
    }

    @Test
    public void aBlankDirectoryIsRejected() {
        try {
            new ScorecardExporter("   ");
            org.junit.Assert.fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertEquals("directory must not be blank", expected.getMessage());
        }
    }
}
