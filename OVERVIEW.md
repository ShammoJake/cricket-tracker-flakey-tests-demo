# cricket-tracker — application and test overview

A ball-by-ball cricket scoring service, built as a subject program for four flaky-test
research artifacts (TSVD4J, FlakeSync, FlakyLens, RankF). The application is a working
one rather than a harness: the laws of the game are implemented properly, and the
defects the artifacts are meant to find are ordinary bugs placed where such bugs
naturally occur.

**54 main classes, 6,689 lines.** Java 11, JUnit 4, Maven. No external runtime
dependencies — the JSON codec and the HTTP layer are hand-rolled so that everything the
artifacts instrument lives in this repository.

---

## The application

### `cricket-core` — domain and scoring (32 classes, 3,715 lines)

The rules engine. Deliveries are validated and applied to an innings, which maintains a
scorecard, overs, partnerships and fall of wickets.


### `cricket-live` — ingest and fan-out (9 classes, 940 lines)

Deliveries arrive on a worker pool and are published to listeners.

`WorkerPool` extends `ThreadPoolExecutor` so the scheduling hooks are in our own code
rather than the JDK's. `IngestPipeline` submits each delivery as a named inner class —
deliberately not a lambda, since lambdas compile to `invokedynamic`, which ASM-based
instrumentation handles less reliably. `EventBus` fans out to four listeners:
`ScorecardUpdater`, `LiveFeedBroadcaster`, `MilestoneDetector` and `PartnershipTracker`.

`awaitIdle(timeout)` is the correct way to synchronise with the pipeline, and the control
tests use it. The flaky tests sleep instead.

### `cricket-stats` — aggregation and reporting (7 classes, 1,048 lines)

`LeaderboardService` and `SeriesBoard` rank players; `RecordsBook` holds notable
performances; `ScorecardExporter` writes JSON to disk; `MatchTimeline` logs time-stamped
match events; `ScoreboardFormatter` renders the broadcast line.

### `cricket-api` — HTTP surface (6 classes, 986 lines)

`CricketApi` is a transport-independent router — it takes an `ApiRequest` and returns an
`ApiResponse`, so it can be driven directly in tests without binding a socket.
`CricketHttpServer` wraps it in the JDK's `com.sun.net.httpserver` and binds port 0.
`RateLimiter` is a fixed-window limiter with an injectable `Clock` (`ManualClock` in
tests, so rate-limit tests are deterministic). `RequestMetrics` records per-route latency
and status counts.

---

## API

Every response is JSON. Requests carry a client id used for rate limiting, and every
call is recorded in `RequestMetrics` under a route label with ids collapsed
(`POST/matches/{id}/balls`), so metrics do not fragment per match.

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/health` | status, registered matches, pipeline submitted/completed counts |
| `POST` | `/matches` | register a fixture — body `{matchId, format, homeTeam, awayTeam, venue}` |
| `GET` | `/matches` | list registered fixtures |
| `GET` | `/matches/{id}` | fixture detail: teams, format, venue, state, innings |
| `POST` | `/matches/{id}/balls` | record a delivery — body `{over, ballInOver, bowler, striker, nonStriker, runsOffBat, extra, wicket}` |
| `GET` | `/matches/{id}/scorecard` | runs, wickets, overs, extras, run rate, summary |
| `GET` | `/matches/{id}/commentary` | ball-by-ball lines; `?limit=` to bound the response |
| `GET` | `/stats/leaderboard` | leading run scorers |
| `GET` | `/stats/wickets` | leading wicket takers |
| `GET` | `/stats/metrics` | request totals, successes, errors, tracked routes |
| `POST` | `/admin/start` | take a fixture through the toss and out to the middle |
| `POST` | `/admin/shutdown` | clear the registry; returns how many were cleared |

Status codes in use: `200`, `400` (bad request), `404` (no such route or match), `405`
(method not allowed), `429` (rate limit exceeded).

`POST /matches/{id}/balls` is the hot path — the endpoint a scoring client hits once per
delivery — and it is where the concurrency lives: the request thread hands the delivery
to the pool and returns, while the listeners update shared state behind it.

---

## Tests

**797 tests across 41 classes.** The suite is *meant* to fail: around six tests fail on a
typical run, and which six varies.

### By module

| Module | Tests |
|---|---|
| `cricket-core` | 505 |
| `cricket-api` | 154 |
| `cricket-live` | 73 |
| `cricket-stats` | 65 |
| **Total** | **797** |

### By flakiness category


| Category | Tests | Share | Where |
|---|---|---|---|
| Non-flaky | 752 | 94.35% | throughout |
| Async wait | 11 | 1.38% | `com.cricket.live`, `com.cricket.api` |
| Async wait — adversarial | 8 | 1.00% | `com.cricket.adversarial` |
| Order-dependent | 12 | 1.51% | `com.cricket.od` |
| Concurrency | 7 | 0.88% | `com.cricket.concurrent` |
| Unordered collections | 4 | 0.50% | `com.cricket.uc` |
| Time | 3 | 0.38% | `com.cricket.time` |
| **Flaky total** | **45** | **5.65%** | |


The 12 order-dependent tests break down as **9 victims and 3 brittles**, a deliberately
lopsided ratio mirroring RankF's own dataset (135 victims to 20 brittles).


### Controls

13 tests are controls and **must never fail**. Most do exactly the work of a flaky
counterpart but synchronise properly — `awaitIdle()` instead of a sleep. Two are TSVD4J
controls where a finding would be a false positive: `RequestMetrics`, which is correctly
built on `AtomicLong` and `ConcurrentHashMap`, and `Match.dirty`, where two threads write
the same constant — a false-positive shape the TSVD4J paper names explicitly.

A further 4 are the adversarial decoys: deterministic tests carrying 4–8 concurrency
tokens each, passing 40 runs out of 40.

If a control fails, something has regressed.

---

## Where the defects are

Each is an ordinary bug of a kind real code has, placed so a particular artifact should
find it.

| Artifact | Defect | Location |
|---|---|---|
| TSVD4J | non-atomic read-modify-write on plain `int` fields, and bare `HashMap`/`HashSet` written from several threads | `ScoreCard`, `BattingLine`, `MatchRegistry`, `LiveFeedBroadcaster` |
| FlakeSync | fixed sleep standing in for synchronisation with the ingest pipeline | `com.cricket.live`, `com.cricket.api` |
| FlakyLens | six categories present; the adversarial package inverts the token/label correlation | throughout |
| RankF | five channels of leaked global state | static registries, static scoring config, the export directory, `Locale`, a system property |

The five order-dependence channels are `MatchRegistry` (static `HashMap` singleton),
`ScoringRules` (static mutable playing conditions), `PlayerDirectory` (lazily populated
static cache), the shared export directory on disk, and JVM-global presentation state
(`Locale.getDefault()` plus the `cricket.scoreboard.style` property).

`artifacts/flaky-labels.json` is the ground truth: every flaky test with its category,
measured failure rate, and what each artifact should report — including
`expectedRepairable: false` on the two timing assertions FlakeSync states it cannot fix.
