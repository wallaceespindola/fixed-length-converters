# Benchmark Results

> Library facts verified **2026-07-27** against Maven Central `maven-metadata.xml`, artifact `Last-Modified`
> headers, the GitHub REST API and deps.dev. Throughput measured with `mvn test -Pbenchmark` on
> Java 21 / Apple Silicon. Re-run before quoting — absolute numbers are machine-specific.

---

## Library Health

| Library | Coordinates (groupId:artifactId) | Pinned | Latest | Latest released | Last repo activity |
|---|---|---|---|---|---|
| **BeanIO** | `com.github.beanio:beanio` | 3.2.1 | 3.2.1 | 2025-02-07 | 2025-02-07 |
| **fixedformat4j** | `com.ancientprogramming.fixedformat4j:fixedformat4j` | 1.9.1 | 1.9.1 | 2026-06-17 | 2026-07-25 |
| **fixedlength** | `name.velikodniy.vitaliy:fixedlength` | 0.15 | 0.15 | 2026-02-26 | 2026-02-26 |
| **Apache Camel Bindy** | `org.apache.camel:camel-bindy` | 4.21.0 | 4.21.0 | 2026-06-27 | daily |
| **Apache Camel BeanIO** | `org.apache.camel:camel-beanio` | 4.21.0 | 4.21.0 | 2026-06-27 | daily |
| **Apache Velocity** | `org.apache.velocity:velocity-engine-core` | 2.4.1 | 2.4.1 | 2024-10-14 | 2026-06-14 |
| **Spring Batch** | `org.springframework.batch:spring-batch-core` | 5.2.2 | 6.0.4 | 2026-06-10 | 2026-07-23 |

Spring Batch 5.2.2 is what Spring Boot 3.4.5 resolves; 6.x requires Boot 4.x.
Camel is pinned to the current 4.21.0 (Dependabot-maintained).

---

## Adoption & Governance

| Library | GitHub repo | Stars | Governance | Dependents¹ | License |
|---|---|---:|---|---:|---|
| BeanIO | `beanio/beanio` | 68 | Community fork of the 2014 `org.beanio` line (2.1.0, last release 2014) | 10 | Apache-2.0 |
| fixedformat4j | `jeyben/fixedformat4j` | 52 | Single maintainer, actively releasing | 2 | Apache-2.0 |
| fixedlength | `g0ddest/fixedlength` | 23 | Single maintainer, 0.x versioning | 0 | Apache-2.0 |
| Camel Bindy / BeanIO | `apache/camel` | 6 273 | Apache Software Foundation, LTS releases | 4 | Apache-2.0 |
| Velocity | `apache/velocity-engine` | 413 | Apache Software Foundation | 1 560 | Apache-2.0 |
| Spring Batch | `spring-projects/spring-batch` | 2 947 | Broadcom/VMware, commercial support available | 40 | Apache-2.0 |

¹ deps.dev dependent packages for the **pinned version only** — a directional proxy, not a total.

> **Maven Central publishes no public download counts.** Any "downloads" figure quoted elsewhere is an
> estimate derived from mirrors or proxies; the numbers above are verifiable facts instead.

---

## Supply-Chain Weight

| Approach | Own jar | Transitive cost | Notes |
|---|---:|---|---|
| BeanIO | 430 KB | none | Self-contained |
| fixedformat4j | 125 KB | none | Smallest annotation-driven option |
| fixedlength | 33 KB | none | Tiny; 0.x API stability caveat |
| Camel Bindy | 171 KB | `camel-support`, `camel-api`, **icu4j 14 MB** | 45 Camel artifacts on the dependency tree |
| Camel BeanIO | 27 KB | Camel core + BeanIO 2.x | Two ecosystems in one path |
| Velocity | 503 KB | commons-lang3, slf4j | Templates are executable code |
| Spring Batch (3 variants) | 0 KB extra | already on the classpath | Batch runtime is a given |

**Security:** Velocity < 2.3 carried CVE-2020-13936 (template → RCE). The pinned 2.4.1 is not affected, but the
risk class remains — `.vm` templates must be version-controlled and never user-supplied.

---

## Library Comparison Matrix

| Approach | Grammar support | Layout auditability | Spring Batch fit | Support model | Operational risk |
|---|---|---|---|---|---|
| **BeanIO** | High (record groups, repeating segments) | Programmatic builder | Good | Community only | Medium (key-person) |
| **fixedformat4j** | Low (flat records) | Annotations | Excellent | Single maintainer | Low–Medium |
| **fixedlength** | Low | Annotations | Good | Single maintainer, 0.x | Medium |
| **Camel Bindy** | Medium | Annotations | Medium | ASF | Medium (dependency weight) |
| **Camel BeanIO** | High | XML mapping files | Medium | ASF | Medium |
| **Velocity** | N/A (write-only) | Templates | Low | ASF | Low (rendering only) |
| **Spring Batch native** | Medium | Code (`Range` list) | Native | Broadcom/VMware | Low |
| **Spring Batch + fixedformat4j** | Medium | Annotations | Native | Broadcom + maintainer | Low |
| **Spring Batch + fixedlength** | Medium | Annotations | Native | Broadcom + maintainer | Low |

---

## Bank Suitability — Verdicts

| Approach | Verdict for a bank |
|---|---|
| **Spring Batch + fixedformat4j** | **Best overall fit** — batch-native runtime, layout declared once in annotations, no extra runtime dependency |
| **Spring Batch + fixedlength** | Same shape, 33 KB dependency; accept 0.x versioning |
| **Spring Batch native** | Safe default when no new dependency is allowed; layout duplicated by hand |
| **BeanIO** | Good for complex CODA grammars; accept community-only support |
| **Camel BeanIO** | Auditable XML mappings, heavy dependency path |
| **Camel Bindy** | Only when Camel already runs in production |
| **fixedformat4j** (standalone) | Strong for simple fixed layouts outside Spring Batch |
| **fixedlength** (standalone) | Prototyping, not core payments |
| **Velocity** | Report/statement rendering only — never for parsing |

---

## Throughput Results (JMH, ops/s)

Throughput mode, 1 fork, 2×1 s warm-up, 3×2 s measurement. One operation = 5 accounts × 20 transactions.
Values are the **median of 3 full runs** (2026-07-27/28, Java 21, Apple Silicon).

| Approach | CODA write | CODA read | SWIFT write | SWIFT read |
|---|---:|---:|---:|---:|
| **fixedformat4j** | **8 261** | **14 198** | 11 418 | 14 433 |
| fixedlength | 6 374 | 7 810 | **11 616** | 13 819 |
| Velocity | 5 473 | 8 947 | 4 110 | 14 121 |
| BeanIO | 5 228 | 2 818 | 11 373 | **14 499** |
| Camel BeanIO | 4 942 | 2 889 | 11 416 | 14 267 |
| Spring Batch native | 4 642 | 9 188 | 11 615 | 13 009 |
| Spring Batch + fixedformat4j | 4 320 | 8 656 | 11 510 | 14 217 |
| Spring Batch + fixedlength | 4 218 | 8 956 | 11 497 | 14 117 |
| Camel Bindy | 3 186 | 2 179 | 11 384 | 14 291 |

Observations:

- **fixedformat4j leads CODA** — ~1.3× the write throughput and ~1.5× the read throughput of the next best
- **Annotation-derived layout costs nothing at runtime** — both hybrids sit within noise of native Spring Batch;
  reflection runs once at bean construction, never per record
- **SWIFT converges (~11k write / ~13–14k read)** — all approaches share `SwiftMtRecord`; only Velocity's
  template rendering (4 110 ops/s) separates from the pack
- Median of 3 runs. A run taken while the machine had heavy background load produced numbers up to 75 % lower
  on the allocation-heavy CODA paths — measure on a quiet machine, and treat gaps under ~20 % as noise

Regenerate with:

```bash
mvn test -Pbenchmark          # writes target/jmh-result.json
```

---

## Batch Pipeline Throughput (records/second)

End-to-end `POST /api/batch/generate`, MEDIUM profile (100 accounts / 1 000 transactions), H2 in-memory,
chunk size 100, median of 4 warm runs:

| Approach | CODA | SWIFT |
|---|---:|---:|
| BeanIO | ~72 000 | ~134 000 |
| Spring Batch + fixedlength | ~71 000 | ~167 000 |
| fixedformat4j | ~68 000 | ~146 000 |
| Spring Batch native | ~63 000 | ~71 000 |
| Camel BeanIO | ~63 000 | ~143 000 |
| Spring Batch + fixedformat4j | ~59 000 | ~167 000 |
| fixedlength | ~56 000 | ~143 000 |
| Camel Bindy | ~29 000 | ~83 000 |
| Velocity | ~18 000 | ~19 000 |

Every approach clears 1 000 records in under 60 ms end-to-end; most land between 14 ms and 18 ms, where
millisecond timer resolution dominates. Use the JMH table to compare formatters, this one to size a job.

---

## Notes

- All benchmarks run with chunk-size 100 on H2 in-memory database.
- Throughput measured end-to-end: domain entity read → strategy format → file write.
- Memory usage tracked via `Runtime.getRuntime().totalMemory() - freeMemory()` snapshot in `BatchMetricsListener`.
- Live results are also available at `GET /api/benchmark/results` and exportable as CSV/JSON/Markdown/HTML.
