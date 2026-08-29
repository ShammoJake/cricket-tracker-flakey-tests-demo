package com.cricket.stats;

import com.cricket.core.model.Innings;
import com.cricket.core.scorecard.BattingLine;
import com.cricket.core.scorecard.ScoreCard;

/**
 * Renders an innings for the scoreboard feed that broadcasters consume.
 *
 * <p>The level of detail is a deployment choice rather than a per-call one: the
 * ground's scoreboard takes the compact line while the broadcast graphics take the
 * full one, so the style is read from the {@code cricket.scoreboard.style} system
 * property that the operator sets when the process starts.
 */
public final class ScoreboardFormatter {

    /** System property naming the level of detail to render. */
    public static final String STYLE_PROPERTY = "cricket.scoreboard.style";

    public static final String STYLE_FULL = "full";
    public static final String STYLE_COMPACT = "compact";

    /** The style in force, defaulting to the full line. */
    public static String style() {
        String configured = System.getProperty(STYLE_PROPERTY);
        if (configured == null || configured.trim().isEmpty()) {
            return STYLE_FULL;
        }
        return configured.trim().toLowerCase();
    }

    public static boolean isCompact() {
        return STYLE_COMPACT.equals(style());
    }

    /** "142/3 (17.4 ov, RR 8.11)" in full, "142/3" when compact. */
    public String scoreLine(ScoreCard card) {
        if (card == null) {
            throw new IllegalArgumentException("card must not be null");
        }
        StringBuilder sb = new StringBuilder();
        sb.append(card.getTotalRuns()).append('/').append(card.getWickets());
        if (isCompact()) {
            return sb.toString();
        }
        sb.append(" (").append(overs(card.oversFaced())).append(" ov")
                .append(", RR ").append(rate(card.runRate())).append(')');
        return sb.toString();
    }

    /** "V Kohli 82 (54)" in full, "V Kohli 82" when compact. */
    public String battingLine(BattingLine line) {
        if (line == null) {
            throw new IllegalArgumentException("line must not be null");
        }
        StringBuilder sb = new StringBuilder();
        sb.append(line.getPlayerId()).append(' ').append(line.getRuns());
        if (!isCompact()) {
            sb.append(" (").append(line.getBallsFaced()).append(')');
        }
        return sb.toString();
    }

    public String inningsHeader(Innings innings) {
        if (innings == null) {
            throw new IllegalArgumentException("innings must not be null");
        }
        return innings.getBattingTeam().getName() + " " + scoreLine(innings.getScoreCard());
    }

    /** Overs to one decimal, as the scoreboard shows them. */
    public String overs(double value) {
        return String.format("%.1f", value);
    }

    /** Run rate to two decimals. */
    public String rate(double value) {
        return String.format("%.2f", value);
    }

    /** The required rate for a chase, to two decimals. */
    public String requiredRate(int runsNeeded, int ballsRemaining) {
        if (ballsRemaining <= 0) {
            return "-";
        }
        return rate(runsNeeded * 6.0 / ballsRemaining);
    }
}
