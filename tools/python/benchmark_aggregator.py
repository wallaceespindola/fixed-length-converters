#!/usr/bin/env python3
"""
Aggregates JMH benchmark JSON results and prints statistics per benchmark.

Usage:
    python benchmark_aggregator.py [path/to/jmh-result.json]
    python benchmark_aggregator.py  # defaults to target/jmh-result.json
"""

import json
import statistics
import sys
from pathlib import Path


def aggregate(json_path: str = "target/jmh-result.json") -> dict:
    path = Path(json_path)
    if not path.exists():
        print(f"Error: File not found: {json_path}", file=sys.stderr)
        return {}

    data = json.loads(path.read_text())
    by_benchmark: dict[str, list[float]] = {}

    for entry in data:
        name = entry.get("benchmark", "unknown")
        score = entry.get("primaryMetric", {}).get("score", 0.0)
        by_benchmark.setdefault(name, []).append(float(score))

    return by_benchmark


def print_table(by_benchmark: dict) -> None:
    if not by_benchmark:
        print("No benchmark data available.")
        return

    col_widths = [60, 12, 12, 12, 6]
    headers = ["Benchmark", "Mean", "StdDev", "Min", "Runs"]
    separator = "-" * sum(col_widths + [3 * len(col_widths)])

    print(f"\n{'Banking Fixed-Length Platform — JMH Benchmark Results':^{sum(col_widths) + 3 * len(col_widths)}}")
    print(separator)
    print(" | ".join(h.ljust(w) for h, w in zip(headers, col_widths)))
    print(separator)

    for name, scores in sorted(by_benchmark.items()):
        short_name = name.split(".")[-1] if "." in name else name
        mean = statistics.mean(scores)
        stdev = statistics.stdev(scores) if len(scores) > 1 else 0.0
        min_val = min(scores)
        print(
            f"{short_name:<60} | {mean:>12.2f} | {stdev:>12.2f} | {min_val:>12.2f} | {len(scores):>6}"
        )

    print(separator)


def main() -> None:
    json_path = sys.argv[1] if len(sys.argv) > 1 else "target/jmh-result.json"
    by_benchmark = aggregate(json_path)
    print_table(by_benchmark)


if __name__ == "__main__":
    main()
