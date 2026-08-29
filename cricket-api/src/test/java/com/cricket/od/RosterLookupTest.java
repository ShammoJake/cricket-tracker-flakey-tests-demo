package com.cricket.od;

import com.cricket.core.model.Player;
import com.cricket.core.registry.PlayerDirectory;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Resolving the names a scorer types against the squads loaded for the fixture.
 *
 * <p>Reads the directory the squad import fills; the scorer's client only ever sees
 * short names, so this is the path every manual entry goes through.
 */
public class RosterLookupTest {

    /**
     * The scorer types "R Sharma" and the opener is resolved from the imported squad.
     */
    @Test
    public void theOpenerResolvesByShortName() {
        Player opener = PlayerDirectory.getInstance().requireByShortName("R Sharma");
        assertEquals("IND1", opener.getId());
    }

    /**
     * Both sides named for the fixture are available to the scorer.
     */
    @Test
    public void bothSquadsAreKnownToTheDirectory() {
        PlayerDirectory directory = PlayerDirectory.getInstance();
        assertNotNull("India squad not loaded", directory.byShortName("V Kohli"));
        assertNotNull("Australia squad not loaded", directory.byShortName("P Cummins"));
    }

    @Test
    public void aShortNameIsMatchedWithoutRegardToCase() {
        PlayerDirectory directory = PlayerDirectory.getInstance();
        assertEquals(directory.byShortName("V Kohli"), directory.byShortName("v kohli"));
    }

    @Test
    public void aNameThatWasNeverImportedIsNotFound() {
        assertNull(PlayerDirectory.getInstance().byShortName("W Grace"));
    }

    @Test
    public void requiringAnUnknownShortNameThrows() {
        try {
            PlayerDirectory.getInstance().requireByShortName("W Grace");
            org.junit.Assert.fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
            assertEquals("player not in directory: W Grace", expected.getMessage());
        }
    }

    @Test
    public void anEmptyFragmentMatchesNobody() {
        List<Player> found = PlayerDirectory.getInstance().search("");
        assertEquals(0, found.size());
    }

    @Test
    public void searchingByNameFragmentIsCaseInsensitive() {
        List<Player> found = PlayerDirectory.getInstance().search("kohli");
        assertFalse("no player matched the fragment", found.isEmpty());
    }
}
