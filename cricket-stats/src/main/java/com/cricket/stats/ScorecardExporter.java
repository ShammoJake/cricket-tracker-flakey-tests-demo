package com.cricket.stats;

import com.cricket.core.json.JsonValue;
import com.cricket.core.json.JsonWriter;
import com.cricket.core.model.Innings;
import com.cricket.core.model.Match;
import com.cricket.core.scorecard.BattingLine;
import com.cricket.core.scorecard.BowlingLine;
import com.cricket.core.scorecard.ScoreCard;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Writes completed scorecards to disk as JSON, one file per match.
 *
 * <p>The export directory is shared process-wide: several matches export into the
 * same folder, and {@link #cleanup()} removes what is there.
 */
public final class ScorecardExporter {

    public static final String DEFAULT_DIRECTORY = "target/exports";

    private static final Charset UTF8 = Charset.forName("UTF-8");

    private final File directory;

    public ScorecardExporter() {
        this(DEFAULT_DIRECTORY);
    }

    public ScorecardExporter(String directory) {
        if (directory == null || directory.trim().isEmpty()) {
            throw new IllegalArgumentException("directory must not be blank");
        }
        this.directory = new File(directory);
    }

    public File getDirectory() {
        return directory;
    }

    /** Creates the export directory when it does not already exist. */
    public boolean ensureDirectory() {
        return directory.exists() || directory.mkdirs();
    }

    /** Renders a match as a JSON document. */
    public String render(Match match) {
        if (match == null) {
            throw new IllegalArgumentException("match must not be null");
        }
        JsonValue doc = JsonValue.object()
                .put("matchId", match.getId())
                .put("format", match.getFormat().name())
                .put("venue", match.getVenue())
                .put("state", match.getState().name());

        JsonValue inningsArray = JsonValue.array();
        for (Innings innings : match.getInnings()) {
            inningsArray.add(renderInnings(innings));
        }
        doc.put("innings", inningsArray);
        return JsonWriter.write(doc);
    }

    private JsonValue renderInnings(Innings innings) {
        ScoreCard card = innings.getScoreCard();
        JsonValue node = JsonValue.object()
                .put("number", innings.getNumber())
                .put("battingTeam", innings.getBattingTeam().getName())
                .put("runs", card.getTotalRuns())
                .put("wickets", card.getWickets())
                .put("overs", card.oversFaced())
                .put("extras", card.totalExtras());

        JsonValue batting = JsonValue.array();
        for (BattingLine line : sortedBatting(card)) {
            batting.add(JsonValue.object()
                    .put("playerId", line.getPlayerId())
                    .put("runs", line.getRuns())
                    .put("balls", line.getBallsFaced())
                    .put("fours", line.getFours())
                    .put("sixes", line.getSixes())
                    .put("out", line.isOut()));
        }
        node.put("batting", batting);

        JsonValue bowling = JsonValue.array();
        for (BowlingLine line : sortedBowling(card)) {
            bowling.add(JsonValue.object()
                    .put("playerId", line.getPlayerId())
                    .put("balls", line.getLegalBalls())
                    .put("runs", line.getRunsConceded())
                    .put("wickets", line.getWickets())
                    .put("maidens", line.getMaidens()));
        }
        node.put("bowling", bowling);
        return node;
    }

    /** Sorted by player id so the exported document is byte-stable. */
    private List<BattingLine> sortedBatting(ScoreCard card) {
        List<BattingLine> lines = new ArrayList<BattingLine>(card.battingLines());
        Collections.sort(lines, new java.util.Comparator<BattingLine>() {
            @Override
            public int compare(BattingLine a, BattingLine b) {
                return a.getPlayerId().compareTo(b.getPlayerId());
            }
        });
        return lines;
    }

    private List<BowlingLine> sortedBowling(ScoreCard card) {
        List<BowlingLine> lines = new ArrayList<BowlingLine>(card.bowlingLines());
        Collections.sort(lines, new java.util.Comparator<BowlingLine>() {
            @Override
            public int compare(BowlingLine a, BowlingLine b) {
                return a.getPlayerId().compareTo(b.getPlayerId());
            }
        });
        return lines;
    }

    /** Writes the match scorecard and returns the file written. */
    public File export(Match match) throws IOException {
        if (!ensureDirectory()) {
            throw new IOException("could not create export directory: " + directory);
        }
        File target = new File(directory, match.getId() + ".json");
        Writer writer = new OutputStreamWriter(new FileOutputStream(target), UTF8);
        try {
            writer.write(render(match));
        } finally {
            writer.close();
        }
        return target;
    }

    /** Files currently in the export directory. */
    public List<String> listExports() {
        String[] names = directory.list();
        if (names == null) {
            return new ArrayList<String>();
        }
        List<String> result = new ArrayList<String>(Arrays.asList(names));
        Collections.sort(result);
        return result;
    }

    public int exportCount() {
        return listExports().size();
    }

    public boolean isEmpty() {
        return exportCount() == 0;
    }

    public boolean hasExport(String matchId) {
        return new File(directory, matchId + ".json").isFile();
    }

    /** Removes every exported file, leaving the directory in place. */
    public int cleanup() {
        File[] files = directory.listFiles();
        if (files == null) {
            return 0;
        }
        int removed = 0;
        for (File file : files) {
            if (file.isFile() && file.delete()) {
                removed++;
            }
        }
        return removed;
    }
}
