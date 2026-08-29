#!/usr/bin/env python3
"""Measure how often a JUnit test class passes.

Runs the class in a fresh JVM each time, so static state does not leak between
runs, and reports a per-method pass rate. Building the classpath through Maven
once and then invoking java directly keeps each run under a second, which makes
100-run measurements practical.

Usage:
    python tools/rerun.py --module cricket-live --class com.cricket.concurrent.X -n 50
    python tools/rerun.py --all -n 100
"""

import argparse
import collections
import json
import os
import re
import subprocess
import sys
import time

ROOT = os.path.dirname(os.path.abspath(os.path.join(__file__, "..")))
JAVA_HOME = r"C:\Program Files\Eclipse Adoptium\jdk-11.0.32.9-hotspot"
MVN = r"C:\Users\LENOVO\tools\apache-maven-3.9.16\bin\mvn.cmd"
JAVA = os.path.join(JAVA_HOME, "bin", "java.exe")

MODULES = ["cricket-core", "cricket-live", "cricket-stats", "cricket-api"]

FAIL_RE = re.compile(r"^\d+\)\s+(\w+)\(([\w.$]+)\)", re.M)
OK_RE = re.compile(r"^OK \((\d+) test", re.M)
RAN_RE = re.compile(r"^Tests run: (\d+),\s+Failures: (\d+)", re.M)


def build_classpath(module):
    """Ask Maven once for the module's dependency classpath."""
    out_file = os.path.join(ROOT, module, "target", "cp.txt")
    subprocess.run(
        [MVN, "-o", "-q", "-pl", module, "dependency:build-classpath",
         "-Dmdep.outputFile=" + out_file, "-Dmdep.includeScope=test"],
        cwd=ROOT, check=True, capture_output=True,
        env={**os.environ, "JAVA_HOME": JAVA_HOME},
    )
    with open(out_file) as handle:
        deps = handle.read().strip()
    classes = os.path.join(ROOT, module, "target", "classes")
    test_classes = os.path.join(ROOT, module, "target", "test-classes")
    return os.pathsep.join([test_classes, classes, deps])


def run_once(classpath, test_class):
    """Run the class in a fresh JVM. Returns (passed, failed_methods)."""
    proc = subprocess.run(
        [JAVA, "-cp", classpath, "org.junit.runner.JUnitCore", test_class],
        cwd=ROOT, capture_output=True, text=True, timeout=300,
    )
    output = proc.stdout + proc.stderr
    failed = [m.group(1) for m in FAIL_RE.finditer(output)]
    return proc.returncode == 0, failed, output


def measure(module, test_class, runs, classpath=None):
    if classpath is None:
        classpath = build_classpath(module)
    failures = collections.Counter()
    method_names = set()
    passes = 0
    started = time.time()
    for i in range(runs):
        ok, failed, output = run_once(classpath, test_class)
        if ok:
            passes += 1
        for name in failed:
            failures[name] += 1
            method_names.add(name)
        sys.stdout.write("\r  %s  %d/%d runs, %d class-level passes" %
                         (test_class.split(".")[-1], i + 1, runs, passes))
        sys.stdout.flush()
    elapsed = time.time() - started
    print("\r  %-45s %3d/%-3d passed  (%.1fs)" %
          (test_class.split(".")[-1], passes, runs, elapsed))
    return {
        "class": test_class,
        "module": module,
        "runs": runs,
        "class_passes": passes,
        "failure_counts": dict(failures),
    }


def discover(module):
    """Every test class compiled into the module."""
    base = os.path.join(ROOT, module, "target", "test-classes")
    found = []
    for dirpath, _, filenames in os.walk(base):
        for name in filenames:
            if name.endswith("Test.class") and "$" not in name:
                rel = os.path.relpath(os.path.join(dirpath, name), base)
                found.append(rel[:-6].replace(os.sep, "."))
    return sorted(found)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--module")
    parser.add_argument("--class", dest="test_class")
    parser.add_argument("--all", action="store_true")
    parser.add_argument("-n", "--runs", type=int, default=20)
    parser.add_argument("--out", default="artifacts/rerun-results.json")
    args = parser.parse_args()

    results = []
    if args.all:
        for module in MODULES:
            classpath = build_classpath(module)
            print("== %s ==" % module)
            for test_class in discover(module):
                results.append(measure(module, test_class, args.runs, classpath))
    else:
        if not args.module or not args.test_class:
            parser.error("--module and --class are required unless --all is given")
        results.append(measure(args.module, args.test_class, args.runs))

    out_path = os.path.join(ROOT, args.out)
    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    with open(out_path, "w") as handle:
        json.dump(results, handle, indent=2)

    print("\n%-50s %-8s %s" % ("CLASS", "PASSES", "FLAKY METHODS"))
    for result in results:
        flaky = [n for n, c in result["failure_counts"].items()
                 if 0 < c < result["runs"]]
        always = [n for n, c in result["failure_counts"].items()
                  if c == result["runs"]]
        note = ""
        if flaky:
            note = "FLAKY: " + ", ".join(
                "%s %d/%d" % (n, result["failure_counts"][n], result["runs"])
                for n in sorted(flaky))
        elif always:
            note = "ALWAYS FAILS: " + ", ".join(sorted(always))
        print("%-50s %d/%-6d %s" % (result["class"].split(".")[-1],
                                    result["class_passes"], result["runs"], note))
    print("\nwrote %s" % out_path)


if __name__ == "__main__":
    main()
