package com.cricket.core.engine;

import com.cricket.core.model.Ball;
import com.cricket.core.model.Dismissal;
import com.cricket.core.model.ExtraType;
import com.cricket.core.model.WicketEvent;

/** Renders a delivery as a line of ball-by-ball commentary. */
public final class CommentaryGenerator {

    /** "12.3 Cummins to Kohli, FOUR" */
    public String describe(Ball ball, String bowlerName, String strikerName) {
        if (ball == null) {
            throw new IllegalArgumentException("ball must not be null");
        }
        StringBuilder sb = new StringBuilder();
        sb.append(ball.address())
                .append(' ').append(safe(bowlerName))
                .append(" to ").append(safe(strikerName))
                .append(", ").append(outcome(ball));
        return sb.toString();
    }

    /** The outcome phrase alone, without the address or the players. */
    public String outcome(Ball ball) {
        if (ball == null) {
            throw new IllegalArgumentException("ball must not be null");
        }
        if (ball.isWicket()) {
            return wicketPhrase(ball.getWicket());
        }
        if (ball.hasExtra()) {
            String phrase = extraPhrase(ball);
            if (phrase != null) {
                return phrase;
            }
        }
        return runPhrase(ball.getRunsOffBat());
    }

    private String runPhrase(int runs) {
        switch (runs) {
            case 0:
                return "no run";
            case 1:
                return "1 run";
            case 4:
                return "FOUR";
            case 6:
                return "SIX";
            default:
                return runs + " runs";
        }
    }

    private String extraPhrase(Ball ball) {
        ExtraType type = ball.getExtra().getType();
        int runs = ball.getExtra().getRuns();
        switch (type) {
            case WIDE:
                return runs == 0 ? "wide" : "wide, " + runs + " more";
            case NO_BALL:
                return runs == 0 && ball.getRunsOffBat() == 0
                        ? "no ball"
                        : "no ball, " + (ball.getRunsOffBat() + runs) + " off it";
            case BYE:
                return runs == 1 ? "1 bye" : runs + " byes";
            case LEG_BYE:
                return runs == 1 ? "1 leg bye" : runs + " leg byes";
            case PENALTY:
                return runs + " penalty runs";
            default:
                return null;
        }
    }

    private String wicketPhrase(WicketEvent wicket) {
        Dismissal how = wicket.getDismissal();
        switch (how) {
            case BOWLED:
                return "OUT, bowled";
            case CAUGHT:
                return "OUT, caught by " + safe(wicket.getFielderId());
            case LBW:
                return "OUT, lbw";
            case STUMPED:
                return "OUT, stumped by " + safe(wicket.getFielderId());
            case RUN_OUT:
                return "OUT, run out by " + safe(wicket.getFielderId());
            case HIT_WICKET:
                return "OUT, hit wicket";
            case OBSTRUCTING_THE_FIELD:
                return "OUT, obstructing the field";
            case RETIRED_OUT:
                return "retired out";
            case TIMED_OUT:
                return "OUT, timed out";
            default:
                return "OUT";
        }
    }

    /** Milestone line for a batter reaching a round score. */
    public String milestone(String batterName, int runs, int balls) {
        if (runs < 50) {
            return null;
        }
        String label;
        if (runs >= 200) {
            label = "DOUBLE HUNDRED";
        } else if (runs >= 100) {
            label = "HUNDRED";
        } else {
            label = "FIFTY";
        }
        return safe(batterName) + " brings up his " + label + " off " + balls + " balls";
    }

    /** End-of-over summary line. */
    public String endOfOver(int overNumber, int runsInOver, int totalRuns, int wickets) {
        return "End of over " + (overNumber + 1) + " (" + runsInOver
                + (runsInOver == 1 ? " run" : " runs") + ") - "
                + totalRuns + "/" + wickets;
    }

    private static String safe(String name) {
        return name == null || name.trim().isEmpty() ? "unknown" : name;
    }
}
