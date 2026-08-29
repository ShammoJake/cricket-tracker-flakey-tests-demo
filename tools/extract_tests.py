"""Dump every test method as a labelled record for the model-based artifacts.

FlakyLens classifies a test method body into one of six categories, and RankF_L scores
a (candidate, OD test) pair of method bodies. Both take the body as source text, so this
walks the test sources and writes one record per @Test method: the signature, the body,
and the ground-truth label from artifacts/flaky-labels.json.

Comments are stripped by default. RankF_L strips them before tokenising, and leaving
them in would hand the classifier the answer -- several bodies here are commented with
exactly what makes them flaky.

    python tools/extract_tests.py --out artifacts/tests.json
    python tools/extract_tests.py --ratio 0.034 --out artifacts/tests-flakebench.json
    python tools/extract_tests.py --keep-comments --out artifacts/tests-annotated.json

The --ratio flag down-samples the non-flaky tests' flaky counterparts to a target flaky
proportion. FlakeBench sits at 3.4% flaky (280 of 8574); this suite is deliberately
denser than that, so an evaluation set drawn at FlakeBench's ratio is a fairer test of a
classifier than the raw suite is. Down-sampling drops flaky tests at random rather than
inventing non-flaky ones, so every record is still a real test from this codebase.
"""

import argparse
import json
import os
import random
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)

MODULES = ("cricket-core", "cricket-live", "cricket-stats", "cricket-api")

PACKAGE_RE = re.compile(r"^\s*package\s+([\w.]+)\s*;", re.M)
CLASS_RE = re.compile(r"^(?:public\s+)?(?:final\s+|abstract\s+)?class\s+(\w+)", re.M)
# The signature line of a test method, taken from the @Test annotation onwards.
TEST_RE = re.compile(
    r"@Test(?:\s*\([^)]*\))?\s*(?:@\w+(?:\([^)]*\))?\s*)*"
    r"(public\s+(?:final\s+)?void\s+(\w+)\s*\([^)]*\)(?:\s*throws\s+[\w.,\s]+)?)\s*\{",
    re.M)


def strip_comments(source):
    """Remove // and /* */ comments without touching string or char literals."""
    out = []
    i = 0
    n = len(source)
    while i < n:
        ch = source[i]
        if ch == '"' or ch == "'":
            quote = ch
            out.append(ch)
            i += 1
            while i < n:
                out.append(source[i])
                if source[i] == "\\":
                    if i + 1 < n:
                        out.append(source[i + 1])
                        i += 2
                        continue
                elif source[i] == quote:
                    i += 1
                    break
                i += 1
            continue
        if source.startswith("//", i):
            while i < n and source[i] != "\n":
                i += 1
            continue
        if source.startswith("/*", i):
            end = source.find("*/", i + 2)
            i = n if end == -1 else end + 2
            continue
        out.append(ch)
        i += 1
    return "".join(out)


def tidy(body):
    """Drop blank lines left behind by comment stripping and re-indent to the body."""
    lines = [ln.rstrip() for ln in body.splitlines()]
    lines = [ln for ln in lines if ln.strip()]
    if not lines:
        return ""
    indent = min((len(ln) - len(ln.lstrip()) for ln in lines if ln.strip()), default=0)
    return "\n".join(ln[indent:] if len(ln) >= indent else ln for ln in lines)


def method_body(source, open_brace_index):
    """The text between the method's braces, found by matching them."""
    depth = 0
    i = open_brace_index
    n = len(source)
    while i < n:
        ch = source[i]
        if ch == '"' or ch == "'":
            quote = ch
            i += 1
            while i < n:
                if source[i] == "\\":
                    i += 2
                    continue
                if source[i] == quote:
                    break
                i += 1
        elif ch == "{":
            depth += 1
        elif ch == "}":
            depth -= 1
            if depth == 0:
                return source[open_brace_index + 1:i]
        i += 1
    return None


def test_sources():
    for module in MODULES:
        root = os.path.join(ROOT, module, "src", "test", "java")
        for dirpath, _dirs, files in os.walk(root):
            for name in sorted(files):
                if name.endswith(".java"):
                    yield module, os.path.join(dirpath, name)


def extract(keep_comments):
    records = []
    for module, path in test_sources():
        with open(path, encoding="utf-8") as fh:
            raw = fh.read()

        package = PACKAGE_RE.search(raw)
        klass = CLASS_RE.search(raw)
        if not package or not klass:
            continue

        source = raw if keep_comments else strip_comments(raw)
        fqcn = "%s.%s" % (package.group(1), klass.group(1))

        for match in TEST_RE.finditer(source):
            body = method_body(source, match.end() - 1)
            if body is None:
                continue
            records.append({
                "fqn": "%s#%s" % (fqcn, match.group(2)),
                "module": module,
                "class": fqcn,
                "method": match.group(2),
                "signature": " ".join(match.group(1).split()),
                "body": tidy(body),
                "file": os.path.relpath(path, ROOT).replace(os.sep, "/"),
            })
    return records


def load_labels():
    path = os.path.join(ROOT, "artifacts", "flaky-labels.json")
    with open(path, encoding="utf-8") as fh:
        labels = json.load(fh)
    by_fqn = {}
    for test in labels.get("tests", []):
        by_fqn[test["fqn"]] = {
            "category": test["category"],
            "odRole": test.get("odRole"),
            "adversarial": test.get("adversarial", False),
        }
    for control in labels.get("controls", []):
        if "#" in control["fqn"]:
            by_fqn[control["fqn"]] = {
                "category": "non-flaky",
                "odRole": None,
                "adversarial": control.get("adversarial", False),
                "control": True,
            }
    return by_fqn


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default="artifacts/tests.json")
    ap.add_argument("--keep-comments", action="store_true",
                    help="leave comments in; several give the label away")
    ap.add_argument("--ratio", type=float,
                    help="down-sample flaky tests to this proportion, e.g. 0.034")
    ap.add_argument("--seed", type=int, default=1)
    args = ap.parse_args()

    records = extract(args.keep_comments)
    labels = load_labels()

    for record in records:
        label = labels.get(record["fqn"])
        record["category"] = label["category"] if label else "non-flaky"
        record["odRole"] = label["odRole"] if label else None
        record["adversarial"] = label["adversarial"] if label else False
        record["flaky"] = record["category"] != "non-flaky"

    flaky = [r for r in records if r["flaky"]]
    stable = [r for r in records if not r["flaky"]]

    if args.ratio is not None:
        if not 0 < args.ratio < 1:
            sys.exit("--ratio must be between 0 and 1")
        # n / (n + len(stable)) = ratio  ->  n = ratio * stable / (1 - ratio)
        target = int(round(args.ratio * len(stable) / (1 - args.ratio)))
        if target < len(flaky):
            random.Random(args.seed).shuffle(flaky)
            flaky = flaky[:target]
            records = sorted(flaky + stable, key=lambda r: r["fqn"])
        else:
            print("suite is already at or below the requested ratio; keeping all %d"
                  % len(flaky))

    counts = {}
    for record in records:
        counts[record["category"]] = counts.get(record["category"], 0) + 1

    total = len(records)
    n_flaky = sum(1 for r in records if r["flaky"])
    print("tests: %d  flaky: %d (%.2f%%)" % (total, n_flaky, 100.0 * n_flaky / total))
    for category in sorted(counts):
        print("  %-14s %d" % (category, counts[category]))

    path = args.out if os.path.isabs(args.out) else os.path.join(ROOT, args.out)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as fh:
        json.dump({
            "project": "cricket-tracker",
            "commentsStripped": not args.keep_comments,
            "ratio": args.ratio,
            "counts": counts,
            "total": total,
            "flaky": n_flaky,
            "tests": records,
        }, fh, indent=2)
    print("\nwrote %s" % path)


if __name__ == "__main__":
    main()
