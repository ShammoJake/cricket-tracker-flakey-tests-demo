package com.cricket.od;

import com.cricket.core.model.Player;
import com.cricket.core.registry.PlayerDirectory;
import com.cricket.core.registry.SquadCatalog;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Loading the squads named for a fixture into the process-wide player directory.
 *
 * <p>The directory is filled once when the teams are announced and read from there on
 * for the rest of the match, so the import is not undone at the end of the class.
 */
public class PlayerImportTest {

    @Test
    public void importingASquadAddsEveryPlayer() {
        PlayerDirectory directory = PlayerDirectory.getInstance();
        directory.importSquad(SquadCatalog.require("IND"));
        assertTrue(directory.size() >= 11);
        assertTrue(directory.knows("IND3"));
    }

    @Test
    public void bothNamedSquadsAreImported() {
        PlayerDirectory directory = PlayerDirectory.getInstance();
        directory.importSquad(SquadCatalog.require("IND"));
        directory.importSquad(SquadCatalog.require("AUS"));
        assertTrue(directory.knows("IND1"));
        assertTrue(directory.knows("AUS1"));
    }

    @Test
    public void anImportedPlayerKeepsTheirRole() {
        PlayerDirectory directory = PlayerDirectory.getInstance();
        directory.importSquad(SquadCatalog.require("IND"));
        Player bumrah = directory.byId("IND9");
        assertNotNull(bumrah);
        assertEquals("Jasprit Bumrah", bumrah.getFullName());
    }

    @Test
    public void theImportCounterTracksTheSquadsLoaded() {
        PlayerDirectory directory = PlayerDirectory.getInstance();
        int before = directory.getImportCount();
        directory.importSquad(SquadCatalog.require("ENG"));
        assertEquals(before + 1, directory.getImportCount());
    }

    @Test
    public void importingNullIsRejected() {
        try {
            PlayerDirectory.getInstance().importSquad(null);
            org.junit.Assert.fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertEquals("team must not be null", expected.getMessage());
        }
    }
}
