package com.cricket.od;

import com.cricket.core.scorecard.ScoreCard;
import com.cricket.stats.ScoreboardFormatter;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The feed prepared for a regional rights holder: local number formatting and the
 * compact line the in-ground scoreboard takes.
 *
 * <p>Both are process-wide settings the operator applies at start-up, so they are
 * established once for the class.
 */
public class RegionalBroadcastTest {

    @BeforeClass
    public static void configureRegionalFeed() {
        Locale.setDefault(Locale.GERMANY);
        System.setProperty(ScoreboardFormatter.STYLE_PROPERTY, ScoreboardFormatter.STYLE_COMPACT);
    }

    private static ScoreCard cardWith(int runs, int wickets, int legalBalls) {
        ScoreCard card = new ScoreCard("regional-innings");
        card.addRuns(runs);
        for (int i = 0; i < wickets; i++) {
            card.addWicket();
        }
        for (int i = 0; i < legalBalls; i++) {
            card.addLegalBall();
        }
        return card;
    }

    @Test
    public void theRegionalFeedUsesTheLocalDecimalSeparator() {
        assertEquals("8,11", new ScoreboardFormatter().rate(8.111));
    }

    @Test
    public void theInGroundBoardTakesTheCompactLine() {
        assertTrue(ScoreboardFormatter.isCompact());
        assertEquals("142/3", new ScoreboardFormatter().scoreLine(cardWith(142, 3, 106)));
    }

    @Test
    public void theCompactBattingLineOmitsTheBallsFaced() {
        com.cricket.core.scorecard.BattingLine line =
                new com.cricket.core.scorecard.BattingLine("IND3");
        line.addRuns(82);
        assertFalse(new ScoreboardFormatter().battingLine(line).contains("("));
    }

    @Test
    public void theConfiguredStyleIsReportedBack() {
        assertEquals(ScoreboardFormatter.STYLE_COMPACT, ScoreboardFormatter.style());
    }
}
