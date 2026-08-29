package com.cricket.od;

import com.cricket.core.engine.ScoringRules;
import com.cricket.core.registry.MatchRegistry;
import com.cricket.core.registry.PlayerDirectory;
import com.cricket.stats.ScorecardExporter;
import com.cricket.stats.ScoreboardFormatter;
import org.junit.Test;

import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Returning the process to its shipped state once a tournament is over.
 *
 * <p>This is what the operator runs between competitions: the directory of fixtures is
 * emptied, the squads are unloaded, trial playing conditions are dropped and the
 * regional presentation settings are handed back to the defaults.
 */
public class TournamentTeardownTest {

    @Test
    public void theFixtureDirectoryIsEmptied() {
        MatchRegistry registry = MatchRegistry.getInstance();
        registry.reset();
        assertTrue(registry.isEmpty());
        assertEquals(0L, registry.getRegistrations());
    }

    @Test
    public void theSquadsAreUnloaded() {
        PlayerDirectory directory = PlayerDirectory.getInstance();
        directory.clear();
        assertTrue(directory.isEmpty());
        assertEquals(0, directory.getImportCount());
    }

    @Test
    public void theShippedPlayingConditionsAreRestored() {
        ScoringRules.reloadDefaults();
        assertTrue(ScoringRules.isDefault());
    }

    @Test
    public void thePresentationSettingsAreHandedBack() {
        Locale.setDefault(Locale.UK);
        System.clearProperty(ScoreboardFormatter.STYLE_PROPERTY);
        assertEquals(ScoreboardFormatter.STYLE_FULL, ScoreboardFormatter.style());
    }

    @Test
    public void theArchiveIsCleared() {
        ScorecardExporter exporter = new ScorecardExporter();
        exporter.cleanup();
        assertTrue(exporter.isEmpty());
    }
}
