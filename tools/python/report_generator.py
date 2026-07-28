#!/usr/bin/env python3
"""
Generates Markdown and HTML benchmark reports from JMH JSON results.

Usage:
    python report_generator.py [path/to/jmh-result.json] [output.md]
    python report_generator.py                              # defaults

Writes the raw JMH numbers to docs/jmh-report.md. The curated analysis in
docs/benchmark-results.md (library health, adoption, bank suitability) is
maintained by hand and is deliberately NOT overwritten by this script.
"""

import json
import sys
import statistics
from datetime import datetime
from pathlib import Path


def load_jmh(json_path: str) -> list:
    path = Path(json_path)
    if not path.exists():
        print(f"Warning: {json_path} not found — using empty dataset", file=sys.stderr)
        return []
    return json.loads(path.read_text())


def group_by_library(data: list) -> dict:
    groups: dict[str, list] = {}
    for entry in data:
        name = entry.get("benchmark", "")
        # Extract library name from benchmark class name
        # Longest names first so "SpringBatchFixedFormat4J" is not swallowed by "SpringBatch"
        for lib in ("SpringBatchFixedFormat4J", "SpringBatchFixedLength", "SpringBatch",
                    "CamelBeanIO", "FixedFormat4J", "FixedLength",
                    "BeanIO", "Bindy", "Velocity"):
            if lib.lower() in name.lower():
                groups.setdefault(lib, []).append(entry)
                break
        else:
            groups.setdefault("Other", []).append(entry)
    return groups


def generate_markdown(data: list, output_path: str = "docs/benchmark-results.md") -> None:
    now = datetime.now().isoformat(timespec="seconds")
    groups = group_by_library(data)

    lines = [
        "# Benchmark Results",
        "",
        f"> Generated: {now}  ",
        f"> Total benchmarks: {len(data)}",
        "",
        "## Summary by Library",
        "",
        "| Library | Benchmarks | Mean Score | Unit |",
        "|---------|-----------|-----------|------|",
    ]

    for lib, entries in sorted(groups.items()):
        scores = [e.get("primaryMetric", {}).get("score", 0.0) for e in entries]
        unit = entries[0].get("primaryMetric", {}).get("scoreUnit", "ops/s") if entries else "ops/s"
        mean = statistics.mean(scores) if scores else 0
        lines.append(f"| {lib} | {len(entries)} | {mean:.2f} | {unit} |")

    lines += [
        "",
        "## Detailed Results",
        "",
        "| Benchmark | Mode | Score | Error | Unit |",
        "|-----------|------|-------|-------|------|",
    ]

    for entry in data:
        name = entry.get("benchmark", "").split(".")[-1]
        mode = entry.get("mode", "")
        metric = entry.get("primaryMetric", {})
        score = metric.get("score", 0.0)
        error = metric.get("scoreError", 0.0)
        unit = metric.get("scoreUnit", "")
        lines.append(f"| {name} | {mode} | {score:.2f} | ±{error:.2f} | {unit} |")

    lines += [
        "",
        "---",
        "",
        "Library health, adoption, supply-chain weight, the bank-suitability matrix and the curated",
        "recommendations live in [`benchmark-results.md`](benchmark-results.md) — maintained by hand,",
        "not generated from JMH output.",
    ]

    Path(output_path).parent.mkdir(parents=True, exist_ok=True)
    Path(output_path).write_text("\n".join(lines) + "\n")
    print(f"Markdown report written to: {output_path}")


def main() -> None:
    json_path = sys.argv[1] if len(sys.argv) > 1 else "target/jmh-result.json"
    output_path = sys.argv[2] if len(sys.argv) > 2 else "docs/jmh-report.md"
    data = load_jmh(json_path)
    generate_markdown(data, output_path)


if __name__ == "__main__":
    main()
