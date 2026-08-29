package com.cricket.od;

import com.cricket.core.Fixtures;
import com.cricket.core.model.Innings;
import com.cricket.core.scorecard.ScoreCard;
import com.cricket.stats.ScoreboardFormatter;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The scoreboard line the broadcast graphics render from.
 */
public class CommentaryFormatTest {

    private final ScoreboardFormatter formatter = new ScoreboardFormatter();

    private static ScoreCard cardWith(int runs, int wickets, int legalBalls) {
        ScoreCard card = new ScoreCard("od-innings");
        card.addRuns(runs);
        for (int i = 0; i < wickets; i++) {
            card.addWicket();
        }
        for (int i = 0; i < legalBalls; i++) {
            card.addLegalBall();
        }
        return card;
    }

    /**
     * Scores go out to an international feed, so the decimal separator is the point
     * regardless of where the scoring machine happens to sit.
     */
    @Test
    public void scoresUseThePointDecimalSeparator() {
        assertEquals("8.11", formatter.rate(8.111));
        assertEquals("17.4", formatter.overs(17.4));
    }

    /**
     * The broadcast feed carries the full line: score, overs and run rate.
     */
    @Test
    public void theDefaultScorecardFormatIsFull() {
        ScoreCard card = cardWith(142, 3, 106);
        String line = formatter.scoreLine(card);

        assertTrue("expected the full line, got: " + line, line.contains(" ov"));
        assertTrue("expected the full line, got: " + line, line.contains("RR"));
    }

    @Test
    public void theScoreLineLeadsWithRunsAndWickets() {
        assertTrue(formatter.scoreLine(cardWith(142, 3, 106)).startsWith("142/3"));
    }

    @Test
    public void theHeaderNamesTheBattingSide() {
        Innings innings = Fixtures.openedInnings();
        assertTrue(formatter.inningsHeader(innings).startsWith("India"));
    }

    @Test
    public void aChaseWithNoBallsLeftHasNoRequiredRate() {
        assertEquals("-", formatter.requiredRate(12, 0));
    }

    @Test
    public void theRequiredRateIsRunsOverRemainingOvers() {
        assertEquals("6.00", formatter.requiredRate(12, 12));
    }

    @Test
    public void aNullCardIsRejected() {
        try {
            formatter.scoreLine(null);
            org.junit.Assert.fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertEquals("card must not be null", expected.getMessage());
        }
    }
}
