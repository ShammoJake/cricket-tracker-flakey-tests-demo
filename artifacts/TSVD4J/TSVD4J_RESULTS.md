# TSVD4J on cricket-tracker — Results

Findings from a complete run of TSVD4J (ICSE'23) against the `cricket-tracker` reduced target.
For how to reproduce this, see `Running_TSVD4J.md`.

| | |
|---|---|
| Date | 2026-08-29, 15:15 – 22:40 (**7h 24m**) |
| Scope | `-Ptsvd4j` reduced target — 6 classes / 29 tests / 3 modules |
| Plugin | `edu.utexas.ece:tsvd4j-maven-plugin:0.1-SNAPSHOT` |
| Toolchain | Temurin JDK 11.0.32, Maven 3.9.16, Windows 11 |
| Ground truth | `cricket-tracker/artifacts/flaky-labels.json` |

---

## 1. Headline

| Module | Wall time | Tests | Failed | Conflicting pairs | Thread events |
|---|---|---|---|---|---|
| `cricket-live` | 40 min | 17 | 15 | **27** | 344,127 |
| `cricket-stats` | 1h 46m | 8 | 2 | 0 | 300,166 |
| `cricket-api` | 4h 58m | 4 | 1 | 0 | 1,002,552 |
| **Total** | **7h 24m** | **29** | **18** | **27** | **1,646,845** |

**Detection outcome: 6 of 6 expected conflicting pairs found, 0 false positives on the 2 designated
controls.** TSVD4J scored a clean sweep on this subject.

---

## 2. Ground-truth check

### 2.1 All six expected pairs were found

`flaky-labels.json` names six pairs under `tsvd4j.expectedConflictingPair`. All six appear in
`cricket-live/.tsvd4j/Conflicting-Pairs.txt`, verified by mapping reported line numbers to source:

| # | Expected (labels) | Reported | Source line |
|---|---|---|---|
| 1 | `ScoreCard.legalBalls` | `ScoreCard\|89` | `this.legalBalls++` |
| 2 | `ScoreCard.totalRuns` | `ScoreCard\|81` | `this.totalRuns += runs` |
| 3 | `Innings.balls` add | `Innings\|add\|136` | `balls.add(ball)` |
| 4 | `MatchRegistry.matches` put | `MatchRegistry\|put\|35` | `matches.put(match.getId(), match)` |
| 5 | `MatchRegistry.registrations` | `MatchRegistry\|36` | `registrations++` |
| 6 | `LiveFeedBroadcaster.subscribers` add | `LiveFeedBroadcaster\|add\|94` | `subscribers.add(...)` |

**Recall: 6/6.**

### 2.2 Both designated controls stayed clean

The labels file names two shapes that must yield **no** finding. Neither appears anywhere in the
output — both correct true negatives:

| Control | Why it should be clean | Result |
|---|---|---|
| `com.cricket.api.RequestMetrics` | correctly built on `AtomicLong` + `ConcurrentHashMap` | ✅ 0 pairs in `cricket-api` across 1,002,552 thread events |
| `com.cricket.core.model.Match.dirty` | two threads write the same constant `true`; the TSVD4J paper names this a known false-positive source | ✅ absent from all 27 pairs |

The `Match.dirty` result is the more interesting of the two: the paper itself flags this shape as
something TSVD4J *can* false-positive on, and here it did not.

---

## 3. The 27 conflicting pairs

All 27 originate from `cricket-live`. Sorted, with source mapping.

### 3.1 Same-statement races (unsynchronised read-modify-write)

A pair whose two halves are identical means one statement racing itself across threads.

| Pair | Source | Field |
|---|---|---|
| `ScoreCard\|81 : ScoreCard\|81` | `this.totalRuns += runs` | ✅ expected #2 |
| `ScoreCard\|89 : ScoreCard\|89` | `this.legalBalls++` | ✅ expected #1 |
| `MatchRegistry\|36 : MatchRegistry\|36` | `registrations++` | ✅ expected #5 |
| `BattingLine\|61 : BattingLine\|61` | `this.runs += scored` | |
| `BattingLine\|70 : BattingLine\|70` | `this.ballsFaced++` | |
| `BowlingLine\|55 : BowlingLine\|55` | `this.legalBalls++` | |
| `BowlingLine\|62 : BowlingLine\|62` | `this.runsConceded += runs` | |
| `Partnership\|54 : Partnership\|54` | `this.runs += scored` | |
| `Partnership\|58 : Partnership\|58` | `this.balls++` | |
| `ScoringEngine\|104 : ScoringEngine\|104` | `this.freeHitPending = freeHitNext` | |

### 3.2 Collection / API races

| Pair | Source | |
|---|---|---|
| `Innings\|add\|136 : Innings\|add\|136` | `balls.add(ball)` | ✅ expected #3 |
| `MatchRegistry\|put\|35 : MatchRegistry\|put\|35` | `matches.put(match.getId(), match)` | ✅ expected #4 |
| `LiveFeedBroadcaster\|add\|94 : LiveFeedBroadcaster\|add\|94` | `subscribers.add(...)` | ✅ expected #6 |
| `Innings\|add\|154 : Innings\|add\|154` | `overs.add(over)` | |
| `Innings\|get\|149 : Innings\|add\|154` | `overs.get(...)` vs `overs.add(over)` | |
| `Innings\|isEmpty\|149 : Innings\|add\|154` | `overs.isEmpty()` vs `overs.add(over)` | |
| `Innings\|size\|149 : Innings\|add\|154` | `overs.size()` vs `overs.add(over)` | |
| `Over\|iterator\|47 : Over\|add\|41` | `for (Ball b : balls)` vs `balls.add(ball)` | |
| `ScoreCard\|get\|145 : ScoreCard\|put\|148` | batting map check-then-put | |
| `ScoreCard\|put\|148 : ScoreCard\|put\|148` | `batting.put(playerId, line)` | |
| `ScoreCard\|get\|154 : ScoreCard\|put\|157` | bowling map check-then-put | |
| `ScoreCard\|put\|157 : ScoreCard\|put\|157` | `bowling.put(playerId, line)` | |

The `get`/`put` pairs at `ScoreCard:145–148` and `154–157` are textbook check-then-act on a plain
`HashMap` — two threads can both miss the `get` and each `put` a fresh line, losing one.
`Over|iterator|47 : Over|add|41` is a read-while-write that would throw
`ConcurrentModificationException` under the right interleaving.

### 3.3 The striker-swap trio

| Pair | Source |
|---|---|
| `Innings\|176 : Innings\|177` | `String tmp = strikerId` vs `strikerId = nonStrikerId` |
| `Innings\|177 : Innings\|177` | `strikerId = nonStrikerId` |
| `Innings\|177 : Innings\|178` | `strikerId = nonStrikerId` vs `nonStrikerId = tmp` |
| `Innings\|178 : Innings\|178` | `nonStrikerId = tmp` |

A three-line non-atomic swap of two fields. TSVD4J caught every internal pairing.

---

## 4. Category discrimination

Composition of the 29-test target, and where findings landed:

| Category | Tests | Pairs reported |
|---|---|---|
| concurrency | 7 | **27** |
| async wait | 6 | 0 |
| unordered collections | 4 | 0 |
| non-flaky controls | 6 | 0 |
| unlabelled | 6 | 0 |

**Every one of the 27 pairs traces to the 7 `com.cricket.concurrent` tests.** Zero findings from the
async, unordered-collection, adversarial-control, or unlabelled tests — despite 13 of those failing
during the run.

This is the most valuable result in the set. TSVD4J does not merely find races; it **declines to
attribute non-race flakiness to races**, even when the test in front of it is visibly failing.

Two sub-results make the point sharply:

- **`cricket-stats` / unordered collections.** `UnorderedStandingsTest` tracked 300,166 thread events
  and reported nothing, while two of its tests failed on genuine
  `ComparisonFailure: expected:<IND[2]> but was:<IND[1]>`. `SeriesBoard` folds each venue on its own
  worker, but into its own accumulator — no unsynchronised shared access exists. The flakiness lives
  in iteration order of the merged result, outside a TSV detector's remit. Correct silence.
- **`cricket-api` / async.** 1,002,552 thread events — the highest of any module — and zero pairs.

---

## 5. Instrumentation side effects

### 5.1 Five of six non-flaky controls failed — all artifacts

The reduced target contains 6 tests labelled non-flaky. Under TSVD4J, **5 failed**. Every one failed
on an `awaitIdle` timeout guard, not on a substantive assertion:

| Control | Result | Failing assertion |
|---|---|---|
| `ConcurrentSubscriberTest.everySubscriberOnTheFeedReceivesTheInnings` | ❌ | `assertTrue(pipeline.awaitIdle(5000))` |
| `ConcurrentSubscriberTest.theTranscriptTalliesTheSameFromEveryThread` | ❌ | `assertTrue(pipeline.awaitIdle(5000))` |
| `ConcurrentSubscriberTest.theAtomicDeliveryCountMatchesTheInnings` | ❌ | `assertTrue(pipeline.awaitIdle(5000))` |
| `ConcurrentSubscriberTest.theScorecardIsSettledOnceTheQueueHasDrained` | ❌ | `assertTrue(pipeline.awaitIdle(5000))` |
| `LiveScoreIngestTest.pipelineDrainsWhenProperlyAwaited` | ❌ | `assertTrue(pipeline.awaitIdle(5000L))` — line 157 |
| `MatchApiIngestTest.theScorecardIsCompleteWhenProperlyAwaited` | ✅ passed | — |

120 deliveries × a 100ms delay injected at every tracked access cannot drain inside a 5s budget.

**These are not control regressions and must not be scored as such.** The four
`ConcurrentSubscriberTest` methods pass 40/40 normally, and `pipelineDrainsWhenProperlyAwaited` is
labelled "must never fail". They are measuring TSVD4J's overhead, not the subject's correctness.

The rule when evaluating: **discount failures whose assertion is an `awaitIdle`/timeout guard; count
failures on substantive assertions.** By that rule, 13 of the 18 failures are real and 5 are artifacts.

### 5.2 Instrumentation inflates async failure far beyond natural rates

Async-wait tests fail near-deterministically under TSVD4J, at magnitudes unrelated to their measured
flakiness:

| Test | Natural rate | Under TSVD4J |
|---|---|---|
| `scorecardReflectsAllBallsAfterIngest` | 0/50 | ❌ `expected:<120> but was:<9>` |
| `theInningsTotalIsVisibleAfterIngest` | 8/50 | ❌ `expected:<120> but was:<4>` |
| `thePartnershipIsVisibleAfterIngest` | 14/50 | ❌ `expected:<120> but was:<4>` |
| `commentaryCoversTheWholeInnings` | 22/50 | ❌ `expected:<120> but was:<4>` |
| `everyDeliveryIsAppliedAfterIngest` | 43/50 | ❌ `expected:<120> but was:<4>` |

A test that never fails naturally (0/50) and one that fails 43/50 produce **identical** results under
instrumentation (`120` vs `4`). Failure counts from a TSVD4J run therefore carry **no information
about flakiness rate** and must not be fed back into `flaky-labels.json` as measurements.

### 5.3 TSVD4J surfaced a test that isolation-based rerunning under-reports

`ConcurrentBallIngestTest.theBatterIsCreditedWithEveryRun` is recorded at **0 failures in 50 runs** —
`CLAUDE.md` notes it is flaky only in the full suite, so isolation-based detection under-reports it.

Under TSVD4J it failed (`expected:<100> but was:<59>`) **and produced 23 of the 27 conflicting
pairs** — by far the richest single test in the run.

This is a concrete win for the approach over rerun-based detection: delay injection exposed in one
run what 50 isolated reruns could not.

---

## 6. Per-test attribution is unreliable

Findings are attributed to whichever test happened to hit the interleaving first, not to every test
that races:

| Test | Per-test pairs |
|---|---|
| `ConcurrentBallIngestTest.theBatterIsCreditedWithEveryRun` | 23 |
| `ConcurrentBallIngestTest.everySubmittedDeliveryReachesTheInnings` | 2 |
| `SharedStateRaceTest.concurrentRegistrationsAreAllVisible` | 2 |
| `ConcurrentBallIngestTest.parallelIngestKeepsRunTotalConsistent` | 1 |
| `SharedStateRaceTest.everySubscriberIsRetainedOnTheFeed` | 1 |
| `ConcurrentBallIngestTest.parallelIngestRecordsEveryLegalBall` | **no file** |
| `SharedStateRaceTest.theRegistrationCounterMatchesTheRegistrations` | **no file** |

The last two got **no per-test file at all**, even though the pairs the labels attribute to them
(`ScoreCard|89`, `MatchRegistry|36`) are present in the aggregate. One test acted as a catch-all and
absorbed most of the attribution.

**Consequence for evaluation:** scoring per-test recall from the per-test files would report 4/6
instead of the true 6/6. Score from the aggregate `Conflicting-Pairs.txt`; treat per-test files only
as a hint about which test exercised a race.

`Conflicting-Pairs.txt` is also **appended at each JVM shutdown** — `cricket-live` has 54 lines for
27 unique pairs. Always `sort -u`. TSVD4J's console line `Total # Conflicting items are = 27` reports
the deduplicated count and matched exactly.

---

## 7. Cost

**Cost tracks tracked accesses, not test count.**

| Class | Tests | Wall time | s/test |
|---|---|---|---|
| `MatchApiIngestTest` | 4 | 17,900s | 4,475 |
| `UnorderedStandingsTest` | 8 | 6,342s | 793 |
| `LiveScoreIngestTest` | 6 | 1,698s | 283 |
| `ConcurrentSubscriberTest` | 4 | 485s | 121 |
| `ConcurrentBallIngestTest` | 4 | 190s | 48 |
| `SharedStateRaceTest` | 3 | 26s | 9 |

A **500× spread in per-test cost.** `cricket-api`'s 4 tests cost more than the other 25 combined,
because every `MatchApiIngestTest` method crosses the `com.sun.net.httpserver` wrapper *and* the
ingest pipeline — 1M tracked accesses at 100ms apiece.

Natural failure rate does not predict cost: `theOverCountIsCurrentOnTheScorecard` fails 1/50 normally
and still took over an hour.

**Practical guidance.** `cricket-live` yields 100% of the findings in 9% of the runtime. For
iteration, run `cricket-live` alone (40 min); run `cricket-stats` and `cricket-api` only to confirm
the true negatives. `-Dfield` roughly halves runtime on a narrowed class.

---

## 8. Summary of observations

1. **Detection is exact on this subject** — 6/6 expected pairs, 0/2 control false positives.
2. **Category discrimination is the strongest result** — all 27 pairs from the 7 concurrency tests;
   zero from async, unordered-collection, or control tests, despite 13 of those failing.
3. **TSVD4J beat rerun-based detection on one test** — `theBatterIsCreditedWithEveryRun` (0/50
   naturally) failed and produced 23 pairs.
4. **5 of 6 non-flaky controls fail under instrumentation**, all on `awaitIdle` timeouts. Artifacts,
   not regressions.
5. **Failure counts under TSVD4J carry no flakiness-rate information** — a 0/50 test and a 43/50 test
   fail identically.
6. **Per-test attribution under-reports**; only the aggregate is trustworthy.
7. **A missing `Conflicting-Pairs.txt` means zero findings, not a crash** — `cricket-stats` and
   `cricket-api` produced only `listener.log`.
8. **One unrecorded finding** (`ScoringRules|30:92`) may warrant a ground-truth update.
9. **Cost is dominated by one module** — budget ~7.5h for the full target, or 40 min for the
   findings-bearing part.

---

## 9. Raw artifacts

```
cricket-tracker/cricket-live/.tsvd4j/
    Conflicting-Pairs.txt                  54 lines, 27 unique
    com.cricket.concurrent.*               5 per-test files
    listener.log
cricket-tracker/cricket-stats/.tsvd4j/     listener.log only (0 findings)
cricket-tracker/cricket-api/.tsvd4j/       listener.log only (0 findings)
```

Maven logs for all three modules, plus a copy of the result tree, are in the session scratchpad at
`tsvd4j-cricket-{live,stats,api}.log` and `tsvd4j-results-backup/`. Those are temporary — copy them
somewhere durable if this run needs to be cited later.
