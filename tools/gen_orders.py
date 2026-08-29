"""Run a module's test suite in many class orders and record what passed.

The order-dependent tests are written to pass in the alphabetical order Surefire is
pinned to. Shuffling the classes is what exposes them: a victim starts failing once a
polluter has run ahead of it, and a brittle starts failing once its state-setter no
longer has.

The per-order pass/fail matrix this writes is the input RankF_O's heuristics score --
Plus One, #Methods, Distance and the two combinations -- so the JSON keeps the full
class order alongside the outcome of every test.

    python tools/gen_orders.py --module cricket-api -k 30 --out artifacts/orders.json
    python tools/gen_orders.py --module cricket-api --order-file order.txt
"""

import argparse
import json
import os
import random
import re
import shutil
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)

JAVA_HOME = r"C:\Program Files\Eclipse Adoptium\jdk-11.0.32.9-hotspot"
JAVA = os.path.join(JAVA_HOME, "bin", "java.exe")
MVN = r"C:\Users\LENOVO\tools\apache-maven-3.9.16\bin\mvn.cmd"

# "1) someTest(com.cricket.od.SomeTest)" in the JUnitCore failure report.
FAIL_RE = re.compile(r"^\d+\)\s+([\w$]+)\(([\w.$]+)\)", re.M)
# "OK (123 tests)" / "Tests run: 123,  Failures: 4"
OK_RE = re.compile(r"^OK \((\d+) test", re.M)
RUN_RE = re.compile(r"^Tests run: (\d+),\s+Failures: (\d+)", re.M)


def build_classpath(module):
    """Resolve the module's test classpath once and cache it."""
    cache = os.path.join(ROOT, module, "target", "cp-test.txt")
    if not os.path.isfile(cache):
        subprocess.run(
            [MVN, "-B", "-o", "-q", "-pl", module, "dependency:build-classpath",
             "-Dmdep.includeScope=test", "-Dmdep.outputFile=" + cache],
            cwd=ROOT, check=True,
            env=dict(os.environ, JAVA_HOME=JAVA_HOME),
        )
    with open(cache) as fh:
        deps = fh.read().strip()
    classes = os.path.join(ROOT, module, "target", "classes")
    tests = os.path.join(ROOT, module, "target", "test-classes")
    return os.pathsep.join([tests, classes, deps])


def discover(module):
    """Every compiled *Test class in the module, in alphabetical (= Surefire) order."""
    root = os.path.join(ROOT, module, "target", "test-classes")
    found = []
    for dirpath, _dirs, files in os.walk(root):
        for name in files:
            if not name.endswith("Test.class") or "$" in name:
                continue
            rel = os.path.relpath(os.path.join(dirpath, name), root)
            found.append(rel[:-len(".class")].replace(os.sep, "."))
    return sorted(found)


# Directories the suite writes into. One order's leftovers must not decide the next
# order's outcome, so these are cleared between runs: what we are measuring is the
# effect of the class order, not of whatever the previous order happened to leave.
SCRATCH_DIRS = ("target/exports", "target/od-scratch")


def reset_workspace(workdir):
    for rel in SCRATCH_DIRS:
        shutil.rmtree(os.path.join(workdir, rel.replace("/", os.sep)), ignore_errors=True)


def run_order(classpath, classes, workdir):
    """Run the classes in exactly this order in one JVM; report the failures.

    Runs from the module directory because that is where Surefire runs: tests that
    touch relative paths must resolve them the same way they do under Maven.
    """
    proc = subprocess.run(
        [JAVA, "-cp", classpath, "org.junit.runner.JUnitCore"] + classes,
        cwd=workdir, capture_output=True, text=True, timeout=900,
    )
    out = proc.stdout + proc.stderr

    failures = sorted({"%s#%s" % (cls, method)
                       for method, cls in FAIL_RE.findall(out)})

    ok = OK_RE.search(out)
    if ok:
        total, failed = int(ok.group(1)), 0
    else:
        run = RUN_RE.search(out)
        total = int(run.group(1)) if run else 0
        failed = int(run.group(2)) if run else 0

    return {"total": total, "failed": failed, "failures": failures}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--module", required=True)
    ap.add_argument("-k", "--orders", type=int, default=20,
                    help="how many shuffled orders to run after the original one")
    ap.add_argument("--seed", type=int, default=1)
    ap.add_argument("--order-file",
                    help="run one explicit order, one class per line, instead of shuffling")
    ap.add_argument("--out")
    args = ap.parse_args()

    workdir = os.path.join(ROOT, args.module)
    classpath = build_classpath(args.module)
    original = discover(args.module)
    if not original:
        sys.exit("no compiled test classes in %s; run mvn test-compile first" % args.module)

    if args.order_file:
        with open(args.order_file) as fh:
            orders = [[ln.strip() for ln in fh if ln.strip()]]
        labels = ["explicit"]
    else:
        rng = random.Random(args.seed)
        orders = [list(original)]
        labels = ["original"]
        for i in range(args.orders):
            shuffled = list(original)
            rng.shuffle(shuffled)
            orders.append(shuffled)
            labels.append("shuffle-%d" % (i + 1))

    baseline = set()
    records = []
    for label, classes in zip(labels, orders):
        reset_workspace(workdir)
        result = run_order(classpath, classes, workdir)
        if label == "original":
            baseline = set(result["failures"])
        # Anything failing in the original order is already broken, not order-dependent.
        new_failures = sorted(set(result["failures"]) - baseline)
        records.append({
            "label": label,
            "classOrder": classes,
            "total": result["total"],
            "failed": result["failed"],
            "failures": result["failures"],
            "orderDependentFailures": new_failures,
        })
        print("%-12s %3d/%-3d failed  %s" % (
            label, result["failed"], result["total"],
            ", ".join(f.split(".")[-1] for f in new_failures) or "-"))

    # A test that fails in some order but not the original is order dependent; one that
    # fails in the original but passes elsewhere is a brittle whose setter got moved.
    od = {}
    for rec in records:
        for fqn in rec["orderDependentFailures"]:
            od.setdefault(fqn, []).append(rec["label"])

    # Leave the workspace as we found it, so the next ordinary build starts clean.
    reset_workspace(workdir)

    print("\norder-dependent tests found: %d" % len(od))
    for fqn in sorted(od):
        print("  %-70s %d/%d orders" % (fqn, len(od[fqn]), len(records) - 1))

    if args.out:
        path = args.out if os.path.isabs(args.out) else os.path.join(ROOT, args.out)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w") as fh:
            json.dump({
                "module": args.module,
                "seed": args.seed,
                "originalOrder": original,
                "runs": records,
                "orderDependent": {k: v for k, v in sorted(od.items())},
            }, fh, indent=2)
        print("\nwrote %s" % path)


if __name__ == "__main__":
    main()
