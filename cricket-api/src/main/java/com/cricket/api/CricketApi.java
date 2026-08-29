package com.cricket.api;

import com.cricket.core.json.JsonException;
import com.cricket.core.json.JsonValue;
import com.cricket.core.model.Ball;
import com.cricket.core.model.Dismissal;
import com.cricket.core.model.ExtraType;
import com.cricket.core.model.Innings;
import com.cricket.core.model.Match;
import com.cricket.core.model.MatchFormat;
import com.cricket.core.model.MatchState;
import com.cricket.core.model.WicketEvent;
import com.cricket.core.registry.MatchRegistry;
import com.cricket.core.registry.PlayerDirectory;
import com.cricket.core.registry.SquadCatalog;
import com.cricket.core.scorecard.ScoreCard;
import com.cricket.live.IngestPipeline;
import com.cricket.live.LiveFeedBroadcaster;
import com.cricket.live.ScorecardUpdater;
import com.cricket.stats.LeaderboardService;
import com.cricket.stats.PlayerTally;

import java.util.List;

/**
 * Routes API requests to the domain.
 *
 * <p>Transport-independent: {@link CricketHttpServer} adapts the JDK http server onto
 * this, and tests can call {@link #handle} directly.
 */
public final class CricketApi {

    private final MatchRegistry registry;
    private final IngestPipeline pipeline;
    private final ScorecardUpdater updater;
    private final LiveFeedBroadcaster feed;
    private final LeaderboardService leaderboard;
    private final RequestMetrics metrics;
    private final RateLimiter rateLimiter;

    public CricketApi(IngestPipeline pipeline) {
        this(pipeline, new ScorecardUpdater(), new LiveFeedBroadcaster(),
                new LeaderboardService(), new RateLimiter(1000, 1000L));
    }

    public CricketApi(IngestPipeline pipeline, ScorecardUpdater updater,
                      LiveFeedBroadcaster feed, LeaderboardService leaderboard,
                      RateLimiter rateLimiter) {
        if (pipeline == null) {
            throw new IllegalArgumentException("pipeline must not be null");
        }
        this.registry = MatchRegistry.getInstance();
        this.pipeline = pipeline;
        this.updater = updater;
        this.feed = feed;
        this.leaderboard = leaderboard;
        this.metrics = new RequestMetrics();
        this.rateLimiter = rateLimiter;
        pipeline.subscribe(updater);
        pipeline.subscribe(feed);
    }

    public RequestMetrics getMetrics() {
        return metrics;
    }

    public LeaderboardService getLeaderboard() {
        return leaderboard;
    }

    public LiveFeedBroadcaster getFeed() {
        return feed;
    }

    public ScorecardUpdater getUpdater() {
        return updater;
    }

    /** Handles a request, recording metrics for it. */
    public ApiResponse handle(ApiRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        long start = System.nanoTime();
        ApiResponse response;
        try {
            response = route(request);
        } catch (JsonException e) {
            response = ApiResponse.badRequest("malformed JSON: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            response = ApiResponse.badRequest(e.getMessage());
        } catch (IllegalStateException e) {
            response = ApiResponse.error(ApiResponse.SERVER_ERROR, e.getMessage());
        }
        long micros = (System.nanoTime() - start) / 1000L;
        metrics.record(routeName(request), response.getStatus(), micros);
        return response;
    }

    private ApiResponse route(ApiRequest request) {
        if (!rateLimiter.tryAcquire(request.getClientId())) {
            return ApiResponse.error(ApiResponse.TOO_MANY_REQUESTS, "rate limit exceeded");
        }

        String[] segments = request.segments();
        if (segments.length == 0) {
            return ApiResponse.notFound("no route for /");
        }

        if ("health".equals(segments[0])) {
            return health(request);
        }
        if ("matches".equals(segments[0])) {
            return matches(request, segments);
        }
        if ("stats".equals(segments[0])) {
            return stats(request, segments);
        }
        if ("admin".equals(segments[0])) {
            return admin(request, segments);
        }
        return ApiResponse.notFound("no route for " + request.getPath());
    }

    /** Stable route label for metrics, with ids collapsed. */
    static String routeName(ApiRequest request) {
        String[] segments = request.segments();
        StringBuilder sb = new StringBuilder(request.getMethod());
        for (int i = 0; i < segments.length; i++) {
            sb.append('/');
            // Collapse the id segment so /matches/{id}/balls is one route.
            if (i == 1 && segments.length > 1 && "matches".equals(segments[0])) {
                sb.append("{id}");
            } else {
                sb.append(segments[i]);
            }
        }
        return sb.toString();
    }

    private ApiResponse health(ApiRequest request) {
        if (!"GET".equals(request.getMethod())) {
            return ApiResponse.error(ApiResponse.METHOD_NOT_ALLOWED, "use GET");
        }
        return ApiResponse.ok(JsonValue.object()
                .put("status", "ok")
                .put("matches", registry.size())
                .put("submitted", pipeline.submitted())
                .put("completed", pipeline.completed()));
    }

    private ApiResponse matches(ApiRequest request, String[] segments) {
        if (segments.length == 1) {
            if ("POST".equals(request.getMethod())) {
                return createMatch(request);
            }
            if ("GET".equals(request.getMethod())) {
                return listMatches();
            }
            return ApiResponse.error(ApiResponse.METHOD_NOT_ALLOWED, "use GET or POST");
        }

        String matchId = segments[1];
        Match match = registry.find(matchId);
        if (match == null) {
            return ApiResponse.notFound("no such match: " + matchId);
        }

        if (segments.length == 2) {
            return "GET".equals(request.getMethod())
                    ? matchDetail(match)
                    : ApiResponse.error(ApiResponse.METHOD_NOT_ALLOWED, "use GET");
        }
        if ("balls".equals(segments[2])) {
            return "POST".equals(request.getMethod())
                    ? postBall(request, match)
                    : ApiResponse.error(ApiResponse.METHOD_NOT_ALLOWED, "use POST");
        }
        if ("scorecard".equals(segments[2])) {
            return scorecard(match);
        }
        if ("commentary".equals(segments[2])) {
            return commentary(request);
        }
        return ApiResponse.notFound("no route for " + request.getPath());
    }

    private ApiResponse createMatch(ApiRequest request) {
        if (!request.hasBody()) {
            return ApiResponse.badRequest("a body is required");
        }
        JsonValue body = com.cricket.core.json.JsonParser.parse(request.getBody());
        String id = body.optString("matchId", null);
        if (id == null) {
            return ApiResponse.badRequest("matchId is required");
        }
        if (registry.contains(id)) {
            return ApiResponse.badRequest("match already registered: " + id);
        }
        String formatName = body.optString("format", "T20");
        MatchFormat format;
        try {
            format = MatchFormat.valueOf(formatName);
        } catch (IllegalArgumentException e) {
            return ApiResponse.badRequest("unknown format: " + formatName);
        }

        String homeId = body.optString("homeTeam", "IND");
        String awayId = body.optString("awayTeam", "AUS");
        if (!SquadCatalog.knows(homeId)) {
            return ApiResponse.badRequest("unknown team: " + homeId);
        }
        if (!SquadCatalog.knows(awayId)) {
            return ApiResponse.badRequest("unknown team: " + awayId);
        }
        if (homeId.equals(awayId)) {
            return ApiResponse.badRequest("a team cannot play itself");
        }

        Match match = new Match(id, SquadCatalog.require(homeId), SquadCatalog.require(awayId),
                format, body.optString("venue", "unknown"));
        registry.register(match);
        PlayerDirectory.getInstance().importSquad(match.getTeamA());
        PlayerDirectory.getInstance().importSquad(match.getTeamB());
        return ApiResponse.created(JsonValue.object()
                .put("matchId", match.getId())
                .put("format", match.getFormat().name())
                .put("state", match.getState().name()));
    }

    private ApiResponse listMatches() {
        JsonValue array = JsonValue.array();
        for (String id : registry.matchIds()) {
            array.add(id);
        }
        return ApiResponse.ok(JsonValue.object()
                .put("count", registry.size())
                .put("matchIds", array));
    }

    private ApiResponse matchDetail(Match match) {
        return ApiResponse.ok(JsonValue.object()
                .put("matchId", match.getId())
                .put("format", match.getFormat().name())
                .put("venue", match.getVenue())
                .put("state", match.getState().name())
                .put("innings", match.getInnings().size())
                .put("aggregateRuns", match.aggregateRuns()));
    }

    private ApiResponse postBall(ApiRequest request, Match match) {
        if (!request.hasBody()) {
            return ApiResponse.badRequest("a body is required");
        }
        Innings innings = match.currentInnings();
        if (innings == null) {
            return ApiResponse.badRequest("no innings in progress");
        }
        JsonValue body = com.cricket.core.json.JsonParser.parse(request.getBody());
        Ball ball = parseBall(body, innings);
        long sequence = pipeline.submit(match.getId(), innings, ball);
        match.markDirty();
        return ApiResponse.created(JsonValue.object()
                .put("sequence", sequence)
                .put("address", ball.address()));
    }

    private Ball parseBall(JsonValue body, Innings innings) {
        Ball.Builder builder = Ball.builder()
                .over(body.optInt("over", 0))
                .ballInOver(body.optInt("ballInOver", 1))
                .bowler(body.optString("bowler", null))
                .striker(body.optString("striker", innings.getStrikerId()))
                .nonStriker(body.optString("nonStriker", innings.getNonStrikerId()))
                .runsOffBat(body.optInt("runsOffBat", 0))
                .timestampMillis(System.currentTimeMillis());

        String extraType = body.optString("extraType", null);
        if (extraType != null) {
            builder.extra(ExtraType.valueOf(extraType), body.optInt("extraRuns", 0));
        }
        String dismissal = body.optString("dismissal", null);
        if (dismissal != null) {
            builder.wicket(new WicketEvent(Dismissal.valueOf(dismissal),
                    body.optString("dismissedBatter", innings.getStrikerId()),
                    body.optString("fielder", null)));
        }
        return builder.build();
    }

    private ApiResponse scorecard(Match match) {
        Innings innings = match.currentInnings();
        if (innings == null) {
            return ApiResponse.badRequest("no innings in progress");
        }
        ScoreCard card = innings.getScoreCard();
        return ApiResponse.ok(JsonValue.object()
                .put("matchId", match.getId())
                .put("runs", card.getTotalRuns())
                .put("wickets", card.getWickets())
                .put("overs", card.oversFaced())
                .put("extras", card.totalExtras())
                .put("runRate", card.runRate())
                .put("summary", card.summary()));
    }

    private ApiResponse commentary(ApiRequest request) {
        int limit = request.intParam("limit", 10);
        if (limit < 0) {
            return ApiResponse.badRequest("limit must not be negative");
        }
        JsonValue lines = JsonValue.array();
        for (String line : feed.recent(limit)) {
            lines.add(line);
        }
        return ApiResponse.ok(JsonValue.object()
                .put("count", lines.size())
                .put("lines", lines));
    }

    private ApiResponse stats(ApiRequest request, String[] segments) {
        if (!"GET".equals(request.getMethod())) {
            return ApiResponse.error(ApiResponse.METHOD_NOT_ALLOWED, "use GET");
        }
        if (segments.length < 2) {
            return ApiResponse.notFound("no route for " + request.getPath());
        }
        int limit = request.intParam("limit", 5);
        if ("leaderboard".equals(segments[1])) {
            return leaderboardResponse(leaderboard.topRunScorers(limit), "runs");
        }
        if ("wickets".equals(segments[1])) {
            return leaderboardResponse(leaderboard.topWicketTakers(limit), "wickets");
        }
        if ("metrics".equals(segments[1])) {
            return ApiResponse.ok(JsonValue.object()
                    .put("totalRequests", metrics.totalRequests())
                    .put("successes", metrics.successCount())
                    .put("errors", metrics.errorCount())
                    .put("routes", metrics.trackedRoutes()));
        }
        return ApiResponse.notFound("no route for " + request.getPath());
    }

    private ApiResponse leaderboardResponse(List<PlayerTally> tallies, String metric) {
        JsonValue array = JsonValue.array();
        for (PlayerTally tally : tallies) {
            array.add(JsonValue.object()
                    .put("playerId", tally.getPlayerId())
                    .put("runs", tally.getRuns())
                    .put("wickets", tally.getWickets()));
        }
        return ApiResponse.ok(JsonValue.object()
                .put("metric", metric)
                .put("count", array.size())
                .put("entries", array));
    }

    private ApiResponse admin(ApiRequest request, String[] segments) {
        if (segments.length < 2) {
            return ApiResponse.notFound("no route for " + request.getPath());
        }
        if (!"POST".equals(request.getMethod())) {
            return ApiResponse.error(ApiResponse.METHOD_NOT_ALLOWED, "use POST");
        }
        if ("shutdown".equals(segments[1])) {
            int cleared = registry.size();
            registry.reset();
            return ApiResponse.ok(JsonValue.object().put("cleared", cleared));
        }
        if ("start".equals(segments[1])) {
            String matchId = request.param("matchId", null);
            if (matchId == null) {
                return ApiResponse.badRequest("matchId is required");
            }
            Match match = registry.find(matchId);
            if (match == null) {
                return ApiResponse.notFound("no such match: " + matchId);
            }
            if (match.getState() == MatchState.SCHEDULED) {
                match.recordToss(match.getTeamA().getId(), true);
            }
            match.transitionTo(MatchState.IN_PROGRESS);
            Innings innings = match.startInnings(match.getTeamA(), match.getTeamB());
            innings.openWith(match.getTeamA().getSquad().get(0).getId(),
                    match.getTeamA().getSquad().get(1).getId());
            return ApiResponse.ok(JsonValue.object()
                    .put("matchId", matchId)
                    .put("state", match.getState().name())
                    .put("innings", match.getInnings().size()));
        }
        return ApiResponse.notFound("no route for " + request.getPath());
    }
}
