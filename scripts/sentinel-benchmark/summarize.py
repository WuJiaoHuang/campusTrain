"""Summarize all JTL CSV samples, using Stage10 nearest-rank P90/P95/P99."""

import argparse
import csv
import json
import math
from collections import Counter
from pathlib import Path


def nearest_rank(values, percentile):
    return values[max(0, math.ceil(len(values) * percentile) - 1)]


def summarize(path):
    with path.open(encoding="utf-8-sig", errors="replace", newline="") as stream:
        samples = list(csv.DictReader(stream))
    if not samples:
        raise ValueError(f"No JTL samples: {path}")
    result = summarize_samples(samples)
    success = [sample for sample in samples if sample.get("success", "").lower() == "true"]
    result["successOnly"] = summarize_samples(success) if success else None
    labels = sorted({sample.get("label", "") for sample in samples})
    result["byLabel"] = {
        label: summarize_samples([sample for sample in samples if sample.get("label", "") == label])
        for label in labels
    }
    return result


def summarize_samples(samples):
    elapsed = sorted(int(sample["elapsed"]) for sample in samples)
    start = min(int(sample["timeStamp"]) for sample in samples)
    end = max(int(sample["timeStamp"]) + int(sample["elapsed"]) for sample in samples)
    window_seconds = max((end - start) / 1000, 0.001)
    codes = Counter(sample.get("responseCode", "") for sample in samples)
    failed = sum(sample.get("success", "").lower() != "true" for sample in samples)
    errors = [
        (sample.get("responseCode", "") + " " + sample.get("responseMessage", "")).lower()
        for sample in samples
    ]
    connection_refused = sum("connection refused" in error for error in errors)
    connection_reset = sum("connection reset" in error for error in errors)
    timeout = sum("timeout" in error or "timed out" in error for error in errors)
    return {
        "samples": len(samples),
        "durationSeconds": round(window_seconds, 3),
        "actualQps": round(len(samples) / window_seconds, 3),
        "averageMs": round(sum(elapsed) / len(elapsed), 3),
        "p90Ms": nearest_rank(elapsed, 0.90),
        "p95Ms": nearest_rank(elapsed, 0.95),
        "p99Ms": nearest_rank(elapsed, 0.99),
        "maxMs": elapsed[-1],
        "errorPercent": round(100 * failed / len(samples), 3),
        "http200": codes["200"],
        "http400": codes["400"],
        "http401": codes["401"],
        "http404": codes["404"],
        "http405": codes["405"],
        "http429": codes["429"],
        "http500": codes["500"],
        "http503": codes["503"],
        "timeout": timeout,
        "connectionRefused": connection_refused,
        "connectionReset": connection_reset,
        "otherConnectionError": sum(
            any(marker in error for marker in ("connectexception", "unknownhost", "noroutetohost"))
            for error in errors
        ) - connection_refused,
        "responseCodes": dict(codes),
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("jtl", type=Path)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    result = summarize(args.jtl)
    report = json.dumps(result, ensure_ascii=False, indent=2)
    if args.output:
        args.output.write_text(report + "\n", encoding="utf-8")
    print(report)


if __name__ == "__main__":
    main()
