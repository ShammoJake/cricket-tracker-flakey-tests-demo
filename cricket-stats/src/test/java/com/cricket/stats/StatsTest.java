package com.cricket.stats;

import com.cricket.core.Fixtures;
import com.cricket.core.engine.ScoringEngine;
import com.cricket.core.engine.ScoringRules;
import com.cricket.core.json.JsonParser;
import com.cricket.core.json.JsonValue;
import com.cricket.core.model.Ball;
import com.cricket.core.model.Innings;
import com.cricket.core.model.Match;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Covers {@link LeaderboardService}, {@link RecordsBook} and {@link ScorecardExporter}. */
public class StatsTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private LeaderboardService leaderboard;
    private RecordsBook records;

    @Before
    public void setUp() {
        ScoringRules.reloadDefaults();
        leaderboard = new LeaderboardService();
        records = new RecordsBook();
    }

    @After
    public void tearDown() {
        ScoringRules.reloadDefaults();
    }

    /** An innings where IND1 scores heavily and AUS8 takes the wickets. */
    private Innings playedInnings() {
        ScoringEngine engine = new ScoringEngine();
        Innings innings = Fixtures.openedInnings();
        for (int i = 0; i < 12; i++) {
            engine.apply(innings, Ball.builder().over(i / 6).ballInOver(i % 6 + 1)
                    .bowler("AUS8").striker("IND1").nonStriker("IND2").runsOffBat(4).build());
        }
        return innings;
    }

    @Test
    public void aTallyIsCreatedOnFirstReference() {
        assertNotNull(leaderboard.tallyFor("IND1"));
        assertTrue(leaderboard.tracks("IND1"));
    }

    @Test
    public void anUntrackedPlayerIsNotPresent() {
        assertFalse(leaderboard.tracks("IND1"));
        assertEquals(0, leaderboard.size());
    }

    @Test
    public void absorbingAnInningsPopulatesTheTallies() {
        leaderboard.absorb(playedInnings());
        assertEquals(48, leaderboard.tallyFor("IND1").getRuns());
    }

    @Test
    public void absorbingCountsBoundaries() {
        leaderboard.absorb(playedInnings());
        assertEquals(12, leaderboard.tallyFor("IND1").getFours());
    }

    @Test
    public void absorbingRecordsTheBowlersFigures() {
        leaderboard.absorb(playedInnings());
        assertEquals(48, leaderboard.tallyFor("AUS8").getRunsConceded());
        assertEquals(12, leaderboard.tallyFor("AUS8").getLegalBallsBowled());
    }

    @Test(expected = IllegalArgumentException.class)
    public void absorbingNullIsRejected() {
        leaderboard.absorb(null);
    }

    @Test
    public void theLeadingRunScorerIsRanked() {
        leaderboard.tallyFor("IND1").addBatting(80, 50, 8, 2);
        leaderboard.tallyFor("IND2").addBatting(40, 30, 4, 0);
        assertEquals("IND1", leaderboard.leadingRunScorer().getPlayerId());
    }

    @Test
    public void theLeadingWicketTakerIsRanked() {
        leaderboard.tallyFor("AUS8").addBowling(4, 30, 24);
        leaderboard.tallyFor("AUS9").addBowling(2, 25, 24);
        assertEquals("AUS8", leaderboard.leadingWicketTaker().getPlayerId());
    }

    @Test
    public void anEmptyLeaderboardHasNoLeader() {
        assertNull(leaderboard.leadingRunScorer());
        assertNull(leaderboard.leadingWicketTaker());
    }

    @Test
    public void theTopScorersAreOrderedByRuns() {
        leaderboard.tallyFor("A").addBatting(30, 20, 2, 1);
        leaderboard.tallyFor("B").addBatting(90, 50, 9, 2);
        leaderboard.tallyFor("C").addBatting(60, 40, 5, 1);
        List<PlayerTally> top = leaderboard.topRunScorers(3);
        assertEquals("B", top.get(0).getPlayerId());
        assertEquals("C", top.get(1).getPlayerId());
        assertEquals("A", top.get(2).getPlayerId());
    }

    @Test
    public void theLimitCapsTheLeaderboard() {
        leaderboard.tallyFor("A").addBatting(30, 20, 2, 1);
        leaderboard.tallyFor("B").addBatting(90, 50, 9, 2);
        assertEquals(1, leaderboard.topRunScorers(1).size());
    }

    @Test
    public void aLimitBeyondTheFieldReturnsEveryone() {
        leaderboard.tallyFor("A").addBatting(30, 20, 2, 1);
        assertEquals(1, leaderboard.topRunScorers(50).size());
    }

    @Test
    public void aZeroLimitReturnsNothing() {
        leaderboard.tallyFor("A").addBatting(30, 20, 2, 1);
        assertTrue(leaderboard.topRunScorers(0).isEmpty());
    }

    @Test(expected = IllegalArgumentException.class)
    public void aNegativeLimitIsRejected() {
        leaderboard.topRunScorers(-1);
    }

    @Test
    public void onlyBowlersAppearInTheEconomyTable() {
        leaderboard.tallyFor("A").addBatting(30, 20, 2, 1);
        leaderboard.tallyFor("AUS8").addBowling(2, 24, 24);
        assertEquals(1, leaderboard.mostEconomical(10).size());
        assertEquals("AUS8", leaderboard.mostEconomical(10).get(0).getPlayerId());
    }

    @Test
    public void theEconomyTableRanksTheCheapestFirst() {
        leaderboard.tallyFor("AUS8").addBowling(2, 24, 24);
        leaderboard.tallyFor("AUS9").addBowling(1, 48, 24);
        assertEquals("AUS8", leaderboard.mostEconomical(2).get(0).getPlayerId());
    }

    @Test
    public void aggregateRunsSumEveryPlayer() {
        leaderboard.tallyFor("A").addBatting(30, 20, 2, 1);
        leaderboard.tallyFor("B").addBatting(70, 40, 6, 2);
        assertEquals(100, leaderboard.aggregateRuns());
    }

    @Test
    public void playersAboveAThresholdAreListed() {
        leaderboard.tallyFor("A").addBatting(30, 20, 2, 1);
        leaderboard.tallyFor("B").addBatting(70, 40, 6, 2);
        assertEquals(1, leaderboard.playersAbove(50).size());
    }

    @Test
    public void resetClearsTheLeaderboard() {
        leaderboard.tallyFor("A").addBatting(30, 20, 2, 1);
        leaderboard.reset();
        assertEquals(0, leaderboard.size());
    }

    @Test
    public void aTallyComputesItsStrikeRate() {
        PlayerTally tally = new PlayerTally("A");
        tally.addBatting(50, 25, 6, 1);
        assertEquals(200.0, tally.strikeRate(), 1e-9);
    }

    @Test
    public void anUnfacedTallyHasNoStrikeRate() {
        assertEquals(0.0, new PlayerTally("A").strikeRate(), 1e-9);
    }

    @Test
    public void aNeverOutTallyAveragesItsRuns() {
        PlayerTally tally = new PlayerTally("A");
        tally.addBatting(50, 25, 6, 1);
        assertEquals(50.0, tally.battingAverage(), 1e-9);
    }

    @Test
    public void dismissalsDivideTheAverage() {
        PlayerTally tally = new PlayerTally("A");
        tally.addBatting(100, 60, 10, 2);
        tally.addDismissal();
        tally.addDismissal();
        assertEquals(50.0, tally.battingAverage(), 1e-9);
    }

    @Test
    public void aTallyComputesItsEconomy() {
        PlayerTally tally = new PlayerTally("A");
        tally.addBowling(2, 24, 24);
        assertEquals(6.0, tally.economy(), 1e-9);
    }

    @Test
    public void aWicketlessTallyHasNoBowlingAverage() {
        PlayerTally tally = new PlayerTally("A");
        tally.addBowling(0, 24, 24);
        assertEquals(-1.0, tally.bowlingAverage(), 1e-9);
    }

    @Test
    public void boundaryRunsAreSummed() {
        PlayerTally tally = new PlayerTally("A");
        tally.addBatting(50, 25, 5, 3);
        assertEquals(38, tally.boundaryRuns());
    }

    @Test
    public void catchesAreCounted() {
        PlayerTally tally = new PlayerTally("A");
        tally.addCatch();
        tally.addCatch();
        assertEquals(2, tally.getCatches());
    }

    @Test(expected = IllegalArgumentException.class)
    public void aBlankTallyPlayerIdIsRejected() {
        new PlayerTally(" ");
    }

    @Test
    public void aRecordIsStored() {
        assertTrue(records.recordCentury("IND1", "M1", 112));
        assertEquals(1, records.size());
    }

    @Test
    public void theSameRecordIsNotStoredTwice() {
        records.recordCentury("IND1", "M1", 112);
        assertFalse(records.recordCentury("IND1", "M1", 112));
        assertEquals(1, records.size());
    }

    @Test
    public void differentValuesAreDistinctRecords() {
        records.recordCentury("IND1", "M1", 112);
        records.recordCentury("IND1", "M1", 130);
        assertEquals(2, records.size());
    }

    @Test
    public void recordsAreFilteredByKind() {
        records.recordCentury("IND1", "M1", 112);
        records.recordFiveFor("AUS8", "M1", 5);
        assertEquals(1, records.countOfKind(RecordsBook.CENTURY));
        assertEquals(1, records.countOfKind(RecordsBook.FIVE_FOR));
    }

    @Test
    public void recordsOfAKindAreRankedByValue() {
        records.recordCentury("A", "M1", 105);
        records.recordCentury("B", "M1", 150);
        records.recordCentury("C", "M1", 120);
        List<RecordsBook.Record> centuries = records.byKind(RecordsBook.CENTURY);
        assertEquals(150, centuries.get(0).getValue());
        assertEquals(120, centuries.get(1).getValue());
        assertEquals(105, centuries.get(2).getValue());
    }

    @Test
    public void theHighestRecordIsReported() {
        records.recordCentury("A", "M1", 105);
        records.recordCentury("B", "M1", 150);
        assertEquals("B", records.highest(RecordsBook.CENTURY).getPlayerId());
    }

    @Test
    public void thereIsNoHighestWhenEmpty() {
        assertNull(records.highest(RecordsBook.CENTURY));
        assertTrue(records.isEmpty());
    }

    @Test
    public void recordsAreFilteredByPlayer() {
        records.recordCentury("A", "M1", 105);
        records.recordFiveFor("A", "M2", 5);
        records.recordCentury("B", "M1", 150);
        assertEquals(2, records.forPlayer("A").size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void aBlankRecordPlayerIsRejected() {
        new RecordsBook.Record(" ", "M1", RecordsBook.CENTURY, 100);
    }

    @Test(expected = IllegalArgumentException.class)
    public void aBlankRecordKindIsRejected() {
        new RecordsBook.Record("A", "M1", " ", 100);
    }

    @Test(expected = IllegalArgumentException.class)
    public void aNullRecordIsRejected() {
        records.record(null);
    }

    @Test
    public void clearEmptiesTheBook() {
        records.recordCentury("A", "M1", 105);
        records.clear();
        assertTrue(records.isEmpty());
    }

    @Test
    public void aMatchRendersAsJson() {
        ScorecardExporter exporter = new ScorecardExporter(folder.getRoot().getAbsolutePath());
        Match match = Fixtures.t20Match();
        JsonValue doc = JsonParser.parse(exporter.render(match));
        assertEquals("IND-AUS-T20", doc.get("matchId").asString());
        assertEquals("T20", doc.get("format").asString());
    }

    @Test
    public void theRenderedDocumentListsTheInnings() {
        ScorecardExporter exporter = new ScorecardExporter(folder.getRoot().getAbsolutePath());
        Match match = Fixtures.t20Match();
        match.startInnings(match.getTeamA(), match.getTeamB());
        JsonValue doc = JsonParser.parse(exporter.render(match));
        assertEquals(1, doc.get("innings").size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void renderingNullIsRejected() {
        new ScorecardExporter(folder.getRoot().getAbsolutePath()).render(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void aBlankExportDirectoryIsRejected() {
        new ScorecardExporter(" ");
    }

    @Test
    public void exportingWritesAFile() throws IOException {
        ScorecardExporter exporter = new ScorecardExporter(folder.getRoot().getAbsolutePath());
        File written = exporter.export(Fixtures.t20Match());
        assertTrue(written.isFile());
        assertTrue(exporter.hasExport("IND-AUS-T20"));
    }

    @Test
    public void anExportedFileIsListed() throws IOException {
        ScorecardExporter exporter = new ScorecardExporter(folder.getRoot().getAbsolutePath());
        exporter.export(Fixtures.t20Match());
        assertEquals(1, exporter.exportCount());
        assertEquals("IND-AUS-T20.json", exporter.listExports().get(0));
    }

    @Test
    public void aFreshDirectoryIsEmpty() {
        ScorecardExporter exporter = new ScorecardExporter(folder.getRoot().getAbsolutePath());
        assertTrue(exporter.isEmpty());
    }

    @Test
    public void cleanupRemovesTheExports() throws IOException {
        ScorecardExporter exporter = new ScorecardExporter(folder.getRoot().getAbsolutePath());
        exporter.export(Fixtures.t20Match());
        exporter.export(Fixtures.odiMatch());
        assertEquals(2, exporter.cleanup());
        assertTrue(exporter.isEmpty());
    }

    @Test
    public void theExportedFileParsesBackAsJson() throws IOException {
        ScorecardExporter exporter = new ScorecardExporter(folder.getRoot().getAbsolutePath());
        Match match = Fixtures.t20Match();
        exporter.export(match);
        JsonValue doc = JsonParser.parse(exporter.render(match));
        assertTrue(doc.has("venue"));
    }

    @Test
    public void ensureDirectoryCreatesTheFolder() {
        File nested = new File(folder.getRoot(), "a/b/c");
        ScorecardExporter exporter = new ScorecardExporter(nested.getAbsolutePath());
        assertTrue(exporter.ensureDirectory());
        assertTrue(nested.isDirectory());
    }
}
