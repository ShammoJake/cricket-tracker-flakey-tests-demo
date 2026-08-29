import com.cricket.core.model.Match;
import com.cricket.core.model.MatchFormat;
import com.cricket.core.registry.SquadCatalog;
import com.cricket.stats.MatchTimeline;
import com.cricket.stats.ScorecardExporter;

/**
 * What the timing-sensitive work actually costs, and how coarse the clock is.
 *
 * <p>The budgets in the timing tests have to sit inside the band this reports: above it
 * they never fire, below it they always do. Neither is a flaky test.
 */
public final class TimingProbe {

    private static final int SAMPLES = 40;

    public static void main(String[] args) throws Exception {
        Match match = new Match("PROBE", SquadCatalog.require("IND"),
                SquadCatalog.require("AUS"), MatchFormat.T20, "Probe Ground");

        ScorecardExporter exporter = new ScorecardExporter("target/probe-exports");
        exporter.ensureDirectory();

        // Warm up, the way the tests do.
        for (int i = 0; i < 5; i++) {
            exporter.render(match);
            exporter.export(match);
        }

        report("render", timeRender(exporter, match));
        report("export", timeExport(exporter, match));
        System.out.println("clock granularity : " + granularityMillis() + " ms");
        for (int work : new int[]{4000, 20000, 60000, 120000, 240000}) {
            System.out.println("markers kept of 6, burn=" + work + " : " + markersKept(work));
        }
        exporter.cleanup();
    }

    private static long[] timeRender(ScorecardExporter exporter, Match match) {
        long[] samples = new long[SAMPLES];
        for (int i = 0; i < SAMPLES; i++) {
            long started = System.currentTimeMillis();
            exporter.render(match);
            samples[i] = System.currentTimeMillis() - started;
        }
        return samples;
    }

    private static long[] timeExport(ScorecardExporter exporter, Match match) throws Exception {
        long[] samples = new long[SAMPLES];
        for (int i = 0; i < SAMPLES; i++) {
            long started = System.currentTimeMillis();
            exporter.export(match);
            samples[i] = System.currentTimeMillis() - started;
        }
        return samples;
    }

    /** Smallest non-zero step the millisecond clock takes. */
    private static long granularityMillis() {
        long start = System.currentTimeMillis();
        long now = start;
        while (now == start) {
            now = System.currentTimeMillis();
        }
        return now - start;
    }

    /** How many of six markers survive when this much work separates them. */
    private static String markersKept(int work) {
        StringBuilder counts = new StringBuilder();
        for (int run = 0; run < 10; run++) {
            MatchTimeline timeline = new MatchTimeline();
            for (int i = 0; i < 6; i++) {
                timeline.note("marker-" + i);
                burn(work);
            }
            counts.append(timeline.size()).append(' ');
        }
        return counts.toString().trim();
    }

    private static void burn(int iterations) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < iterations; i++) {
            sb.append(i % 10);
        }
        if (sb.length() < 0) {
            throw new IllegalStateException("unreachable");
        }
    }

    private static void report(String label, long[] samples) {
        long min = Long.MAX_VALUE;
        long max = 0;
        long total = 0;
        for (long sample : samples) {
            min = Math.min(min, sample);
            max = Math.max(max, sample);
            total += sample;
        }
        System.out.printf("%-18s min=%d ms  max=%d ms  mean=%.2f ms%n",
                label, min, max, total / (double) samples.length);
    }
}
