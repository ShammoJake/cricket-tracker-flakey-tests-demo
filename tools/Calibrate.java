import com.cricket.core.engine.ScoringEngine;
import com.cricket.core.model.Ball;
import com.cricket.core.model.Innings;
import com.cricket.core.model.Player;
import com.cricket.core.model.PlayerRole;
import com.cricket.core.model.Team;
import com.cricket.live.IngestPipeline;
import com.cricket.live.ScorecardUpdater;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Reports how long an ingest of N deliveries actually takes to drain, cold and warm.
 * Used to pick wait values for the async tests that are genuinely marginal rather
 * than always-passing or always-failing.
 */
public final class Calibrate {

    private static Team squad(String id, String name, String prefix) {
        List<Player> players = new ArrayList<Player>();
        for (int i = 1; i <= 11; i++) {
            players.add(new Player(prefix + i, name + " Player " + i,
                    i > 7 ? PlayerRole.BOWLER : PlayerRole.BATTER));
        }
        return new Team(id, name, players);
    }

    private static Innings freshInnings() {
        Innings innings = new Innings("inn1", 1, squad("IND", "India", "IND"),
                squad("AUS", "Australia", "AUS"));
        innings.setOversLimit(20);
        innings.openWith("IND1", "IND2");
        return innings;
    }

    private static List<Ball> singles(int count) {
        List<Ball> balls = new ArrayList<Ball>();
        for (int i = 0; i < count; i++) {
            balls.add(Ball.builder().over(i / 6).ballInOver(i % 6 + 1)
                    .bowler(i / 6 % 2 == 0 ? "AUS8" : "AUS9")
                    .striker("IND1").nonStriker("IND2").runsOffBat(1).build());
        }
        return balls;
    }

    public static void main(String[] args) {
        int deliveries = args.length > 0 ? Integer.parseInt(args[0]) : 120;
        int rounds = args.length > 1 ? Integer.parseInt(args[1]) : 25;

        long[] micros = new long[rounds];
        for (int round = 0; round < rounds; round++) {
            IngestPipeline pipeline = new IngestPipeline();
            ScorecardUpdater updater = new ScorecardUpdater();
            pipeline.subscribe(updater);
            // Production runs the full listener set; calibrate against that.
            pipeline.subscribe(new com.cricket.live.PartnershipTracker());
            pipeline.subscribe(new com.cricket.live.MilestoneDetector());
            com.cricket.live.LiveFeedBroadcaster feed = new com.cricket.live.LiveFeedBroadcaster();
            feed.subscribe(new com.cricket.live.LiveFeedBroadcaster.RecordingSubscriber("s1"));
            pipeline.subscribe(feed);
            Innings innings = freshInnings();
            List<Ball> balls = singles(deliveries);

            long start = System.nanoTime();
            pipeline.submitAll("M", innings, balls);
            pipeline.awaitIdle(30000L);
            micros[round] = (System.nanoTime() - start) / 1000L;

            pipeline.shutdown(5000L);
        }

        System.out.println("deliveries=" + deliveries + " rounds=" + rounds);
        System.out.println("cold (run 1)  : " + micros[0] / 1000.0 + " ms");
        long[] warm = Arrays.copyOfRange(micros, rounds / 2, rounds);
        Arrays.sort(warm);
        System.out.println("warm median   : " + warm[warm.length / 2] / 1000.0 + " ms");
        System.out.println("warm min      : " + warm[0] / 1000.0 + " ms");
        System.out.println("warm max      : " + warm[warm.length - 1] / 1000.0 + " ms");
        StringBuilder sb = new StringBuilder("all (ms)      : ");
        for (long m : micros) {
            sb.append(String.format("%.1f ", m / 1000.0));
        }
        System.out.println(sb.toString().trim());
    }
}
