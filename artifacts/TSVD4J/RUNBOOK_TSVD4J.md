# Running TSVD4J on cricket-tracker

A worked runbook for the `TSVD4J` artifact (ICSE'23, thread-safety violation detection) against the
`cricket-tracker` demo project. Companion to `RUNBOOK_FLAKYLENS.md`.

**Status: verified end-to-end on this machine.** Every command below was executed, and the results
in [Results](#results) are the real output of a full run of the reduced target on 2026-08-29.

---


## Environment

Verified working with exactly this. TSVD4J compiles at `source/target 1.8` and its Maven plugin is
built against maven-core 3.3.9, but both run fine on the toolchain below.

| | |
|---|---|
| JDK | Temurin 11.0.32+9 (`C:\Program Files\Eclipse Adoptium\jdk-11.0.32.9-hotspot`) |
| Maven | 3.9.16 (`C:\Users\LENOVO\tools\apache-maven-3.9.16`) |
| OS | Windows 11, Git Bash for the commands below |

These are the same two paths hardcoded at the top of `cricket-tracker/tools/rerun.py`, so if that
script works, TSVD4J will too.

`mvn clean install` in `TSVD4J/` takes ~75s and **requires network access** on first run — nothing
it needs was in the local repo, and `-o` fails immediately on `maven-clean-plugin:2.5`. It installs
three artifacts at version `0.1-SNAPSHOT`:

```
~/.m2/repository/edu/utexas/ece/{tsvd4j-parent,tsvd4j-core,tsvd4j-maven-plugin}/
```

`tsvd4j-core` is the Java agent (loaded via `-javaagent`); `tsvd4j-maven-plugin` is the Mojo that
reconfigures Surefire to load it.

---

## The three gotchas

### 1. `mvn tsvd4j:tsvd4j` does not resolve — use fully-qualified coordinates

Both `TSVD4J/README.md` and `CLAUDE.md` give the short prefix form. It fails:

```
[ERROR] No plugin found for prefix 'tsvd4j' in the current project and in the plugin groups
[ERROR] [org.apache.maven.plugins, org.codehaus.mojo] available from the repositories ...
```

Maven resolves a goal prefix by scanning `pluginGroups` in `settings.xml` (there is no
`~/.m2/settings.xml` on this machine) plus the groupIds of plugins **declared in the project POM**.
`edu.utexas.ece` is in neither. The `tsvd4j` profile at `cricket-tracker/pom.xml:99` is easy to
misread as wiring the plugin in — it does not. It only narrows Surefire's `<includes>`:

```xml
<profile>
  <id>tsvd4j</id>
  <build><plugins>
    <plugin>
      <artifactId>maven-surefire-plugin</artifactId>
      <configuration><includes> ... </includes></configuration>
    </plugin>
  </plugins></build>
</profile>
```

Two ways out. **This runbook uses the first**, because it mutates nothing:

```bash
# (a) fully-qualified coordinates — same pattern FlakeSync already uses
mvn -pl <module> -Ptsvd4j edu.utexas.ece:tsvd4j-maven-plugin:0.1-SNAPSHOT:tsvd4j

# (b) or inject the plugin declaration into all five POMs, then the short form works
cd TSVD4J/scripts && bash pom-modify/modify-project.sh ../../cricket-tracker
```

Option (b) rewrites `cricket-tracker/pom.xml` and all four module POMs. It was **not** used here.
Gotcha 2 applies either way.

### 2. Output silently fails to persist — pre-create `<module>/.tsvd4j`

`TSVD4JMojo.applyConfig()` does:

```java
new File(tsvd4jDir).mkdir();     // ".tsvd4j", relative to the Maven process CWD
```

The Maven process runs at the repo root, but the **agent writes from the forked Surefire JVM, whose
CWD is the module directory**. So the Mojo creates `cricket-tracker/.tsvd4j/` while the agent tries
to write `cricket-tracker/cricket-live/.tsvd4j/Conflicting-Pairs.txt`. Result: a flood of

```
java.nio.file.NoSuchFileException: ...\cricket-live\.tsvd4j\Conflicting-Pairs.txt
        at edu.utexas.ece.tsvd4j.agent.Agent.writeTo(Agent.java:64)
```

findings printed to console only, and an empty `.tsvd4j/` at the root. **Nothing is saved.** The run
still reports `BUILD SUCCESS`, so this is easy to miss.

```bash
mkdir -p <module>/.tsvd4j        # before every run
```

`tsvd4j:clean` has the identical CWD bug — it reports SUCCESS without deleting the module directory.
Clean up with `rm -rf <module>/.tsvd4j` instead.

### 3. `-Dmaven.test.failure.ignore=true` is required

Same reason as the ordinary cricket-tracker build: the suite is meant to fail. Without it Maven
aborts the module on the first failing class, and since findings are only flushed at JVM shutdown you
can lose the whole run's output.

---

## What actually gets run

The `tsvd4j` profile's `<includes>` resolve to **6 classes / 29 tests** across three modules.
`cricket-core` matches nothing — do not bother running it.

| Module | Class | Tests |
|---|---|---|
| `cricket-live` | `adversarial.ConcurrentSubscriberTest` | 4 |
| `cricket-live` | `concurrent.ConcurrentBallIngestTest` | 4 |
| `cricket-live` | `concurrent.SharedStateRaceTest` | 3 |
| `cricket-live` | `live.LiveScoreIngestTest` | 6 |
| `cricket-stats` | `uc.UnorderedStandingsTest` | 8 |
| `cricket-api` | `api.MatchApiIngestTest` | 4 |

Run modules **sequentially**. These are concurrency tests; running modules in parallel puts the JVMs
into CPU contention and distorts both the timing and the interleavings TSVD4J explores.

A driver script is given below.


---

## Driver script

Sequential across all three modules, with the `mkdir` workaround applied per module.

```bash
#!/bin/bash
set -u
ROOT="D:/New folder/Demo/cricket-tracker"
PLUGIN="edu.utexas.ece:tsvd4j-maven-plugin:0.1-SNAPSHOT:tsvd4j"

cd "$ROOT" || exit 1
for m in cricket-live cricket-stats cricket-api; do
    echo "===== $m started $(date +%H:%M:%S) ====="
    rm -rf "$m/.tsvd4j"
    mkdir -p "$m/.tsvd4j"        # agent writes from the forked JVM, whose CWD is the module dir
    mvn -pl "$m" -Ptsvd4j "$PLUGIN" -Dmaven.test.failure.ignore=true > "tsvd4j-$m.log" 2>&1
    echo "===== $m done $(date +%H:%M:%S) exit=$? ====="
done
```

Run it detached — it takes ~7 hours:

```bash
nohup bash run-tsvd4j.sh > run-tsvd4j.out 2>&1 &
```

Progress is visible live in `<module>/.tsvd4j/listener.log` (one `Test started` / `Test finished`
line per method). Surefire buffers its own per-class summary until the class ends, so `listener.log`
is the only real-time signal — and note that "Test finished" means *the method returned*, not that it
passed.

The Maven and forked JVM processes are detached children: killing the driver shell does **not** stop
an in-flight module. Check with `Get-Process java` and confirm progress by sampling CPU, rather than
assuming a killed script means a stopped run.

---

## Reading the output format

```
com/cricket/core/scorecard/ScoreCard|get|145 : com/cricket/core/scorecard/ScoreCard|put|148
└─ class ────────────────────────┘ └op┘ └ln┘
```

Each line is one conflicting pair: `class|operation|line : class|operation|line`. The operation
segment is present for API/collection calls (`get`, `put`, `add`, `iterator`) and omitted for plain
field accesses, which appear as `class|line`. A pair with identical halves (e.g.
`ScoreCard|81:ScoreCard|81`) is the same statement racing itself across threads — the signature of an
unsynchronised read-modify-write such as `this.totalRuns += runs`.

Files written per module in `<module>/.tsvd4j/`:

| File | Contents |
|---|---|
| `Conflicting-Pairs.txt` | aggregate, duplicated across JVM shutdowns — `sort -u` it. Absent when zero findings. |
| `<FQCN>.<method>` | pairs attributed to one test. Unreliable — see traps above. |
| `listener.log` | `Test started` / `Test finished` per method. The live progress signal. |
