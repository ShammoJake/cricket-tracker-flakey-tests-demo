"""Join FlakyLens' per-test predictions back to cricket-tracker's ground truth.

FlakyLens writes Finetuned_Result_with_tokens.csv with columns
project_group,test_code,Prediction,Ground_Truth,Token_List -- note there is no test
name, so rows have to be matched to artifacts/tests.json by position. That is exact
(to_flakylens_csv.py writes the fold file in tests.json order and the model preserves
row order), and this script verifies it by checking the method name parsed out of
test_code against tests.json at every index. 13 method names are duplicated across
classes in this suite, so a name-based join would be ambiguous -- position plus
verification is the reliable route.

    python tools/analyze_flakylens.py \
        --pred ../FlakyLens/src/FlakyLens_Categorization_PerProject-result/Finetuned_Result_with_tokens.csv
"""

import argparse
import collections
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)

CLASS_NAMES = {
    0: "async wait",
    1: "concurrency",
    2: "time",
    3: "unordered collections",
    4: "order dependency",
    5: "non-flaky",
}

# cricket-tracker category -> the integer FlakyLens predicts.
CATEGORY_TO_CODE = {
    "async": 0, "concurrency": 1, "time": 2,
    "uc": 3, "od": 4, "non-flaky": 5,
}

METHOD_RE = re.compile(r"void\s+(\w+)\s*\(")


def load_predictions(path):
    import csv
    csv.field_size_limit(min(sys.maxsize, 2**31 - 1))
    with open(path, newline="", encoding="utf-8") as handle:
        return list(csv.DictReader(handle))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--pred", required=True, help="Finetuned_Result_with_tokens.csv")
    ap.add_argument("--tests", default=os.path.join(ROOT, "artifacts", "tests.json"))
    ap.add_argument("--out", help="optional CSV of every joined row")
    args = ap.parse_args()

    tests = json.load(open(args.tests, encoding="utf-8"))["tests"]
    rows = load_predictions(args.pred)

    if len(rows) != len(tests):
        sys.exit("row count mismatch: %d predictions vs %d tests -- the prediction file "
                 "is probably from a different fold or a stale run" % (len(rows), len(tests)))

    # Positional join, verified.
    joined, mismatches = [], 0
    for record, row in zip(tests, rows):
        found = METHOD_RE.search(row["test_code"] or "")
        parsed = found.group(1) if found else None
        if parsed != record["method"]:
            mismatches += 1
        truth = CATEGORY_TO_CODE[record["category"]]
        assert int(row["Ground_Truth"]) == truth, (
            "ground truth disagrees at %s: file says %s, labels say %s"
            % (record["fqn"], row["Ground_Truth"], truth))
        joined.append({
            "fqn": record["fqn"],
            "clazz": record["class"].rsplit(".", 1)[-1],
            "method": record["method"],
            "package": ".".join(record["class"].split(".")[:3]),
            "truth": truth,
            "pred": int(float(row["Prediction"])),
            "adversarial": bool(record.get("adversarial")),
            "flaky": bool(record.get("flaky")),
        })

    print("joined %d rows; method-name mismatches: %d" % (len(joined), mismatches))
    if mismatches:
        print("  WARNING: positional join is not trustworthy, stopping")
        return
    print()

    correct = sum(1 for r in joined if r["truth"] == r["pred"])
    print("overall accuracy: %d/%d = %.3f" % (correct, len(joined), correct / len(joined)))
    print()

    print("=== confusion matrix (rows = truth, cols = prediction) ===")
    header = "%-24s" % "truth \\ pred" + "".join("%8d" % c for c in range(6)) + "%9s" % "total"
    print(header)
    for t in range(6):
        counts = [sum(1 for r in joined if r["truth"] == t and r["pred"] == p) for p in range(6)]
        total = sum(counts)
        if total:
            print("%-24s" % ("%d %s" % (t, CLASS_NAMES[t])) +
                  "".join("%8d" % c for c in counts) + "%9d" % total)
    print()

    nf = [r for r in joined if r["truth"] == 5]
    print("=== what the model called the %d non-flaky tests ===" % len(nf))
    spread = collections.Counter(r["pred"] for r in nf)
    for code, count in spread.most_common():
        print("  %-24s %4d  (%5.1f%%)" % (CLASS_NAMES[code], count, 100.0 * count / len(nf)))
    print()

    print("=== the adversarial package ===")
    print("The point of these two groups: does the classifier follow surface tokens")
    print("rather than behaviour?")
    print()

    hidden = [r for r in joined if r["adversarial"] and r["flaky"]]
    decoys = [r for r in joined if r["adversarial"] and not r["flaky"]]

    print("-- 8 genuinely flaky, no flakiness vocabulary (truth = 0 async wait)")
    print("   designed failure: predicted 5 non-flaky")
    for r in sorted(hidden, key=lambda x: x["fqn"]):
        mark = "OK " if r["pred"] == r["truth"] else "-> "
        print("   %s%-58s pred=%d %s" % (mark, r["clazz"] + "#" + r["method"],
                                         r["pred"], CLASS_NAMES[r["pred"]]))
    got = sum(1 for r in hidden if r["pred"] == r["truth"])
    print("   correct: %d/%d;  called non-flaky: %d/%d"
          % (got, len(hidden), sum(1 for r in hidden if r["pred"] == 5), len(hidden)))
    print()

    print("-- 4 deterministic decoys stuffed with 4-8 flakiness tokens (truth = 5 non-flaky)")
    print("   designed failure: predicted 0 async wait or 1 concurrency")
    for r in sorted(decoys, key=lambda x: x["fqn"]):
        mark = "OK " if r["pred"] == r["truth"] else "-> "
        print("   %s%-58s pred=%d %s" % (mark, r["clazz"] + "#" + r["method"],
                                         r["pred"], CLASS_NAMES[r["pred"]]))
    print()

    # The decoys only mean something against the base rate of ordinary non-flaky tests:
    # if everything non-flaky is called flaky, "the decoys were called flaky" says nothing.
    ordinary = [r for r in nf if not r["adversarial"]]
    ordinary_tokenish = sum(1 for r in ordinary if r["pred"] in (0, 1))
    decoy_tokenish = sum(1 for r in decoys if r["pred"] in (0, 1))
    print("   baseline -- ordinary non-flaky tests predicted async/concurrency: %d/%d = %.1f%%"
          % (ordinary_tokenish, len(ordinary), 100.0 * ordinary_tokenish / max(len(ordinary), 1)))
    print("   decoys    -- token-stuffed non-flaky predicted async/concurrency: %d/%d = %.1f%%"
          % (decoy_tokenish, len(decoys), 100.0 * decoy_tokenish / max(len(decoys), 1)))
    print("   (a gap here is token-keying; no gap means the decoys are no worse than any")
    print("    other non-flaky test on this codebase)")
    print()

    print("=== per-package accuracy ===")
    by_pkg = collections.defaultdict(lambda: [0, 0])
    for r in joined:
        by_pkg[r["package"]][1] += 1
        if r["truth"] == r["pred"]:
            by_pkg[r["package"]][0] += 1
    for pkg in sorted(by_pkg):
        ok, total = by_pkg[pkg]
        print("  %-32s %4d/%-4d %6.1f%%" % (pkg, ok, total, 100.0 * ok / total))

    if args.out:
        import csv as _csv
        with open(args.out, "w", newline="", encoding="utf-8") as handle:
            writer = _csv.DictWriter(handle, fieldnames=list(joined[0].keys()))
            writer.writeheader()
            writer.writerows(joined)
        print("\nwrote %s" % args.out)


if __name__ == "__main__":
    main()
