---
marp: true
theme: default
paginate: true
backgroundColor: '#ffffff'
color: '#1a1a1a'
style: |
  section {
    font-family: 'Segoe UI', Arial, sans-serif;
    font-size: 22px;
  }
  section.title {
    background: #e65100;
    color: #ffffff;
    text-align: center;
  }
  section.title h1 { font-size: 42px; margin-bottom: 12px; }
  section.title h2 { font-size: 22px; font-weight: 400; opacity: 0.9; }
  section.title p  { font-size: 16px; opacity: 0.8; margin-top: 32px; }
  h1, h2 { color: #e65100; }
  h3 { color: #bf360c; }
  code { background: #f4f4f4; border-radius: 4px; padding: 2px 6px; font-size: 18px; }
  pre  { background: #1e1e1e; color: #d4d4d4; border-radius: 6px;
         padding: 16px; font-size: 15px; }
  table { font-size: 17px; width: 100%; border-collapse: collapse; }
  th { background: #e65100; color: #fff; padding: 8px 12px; }
  td { padding: 7px 12px; border-bottom: 1px solid #ddd; }
  tr:nth-child(even) td { background: #fef6f2; }
  .footer { position: absolute; bottom: 16px; left: 0; right: 0;
            text-align: center; font-size: 13px; color: #888; }
---

<!-- _class: title -->

# Banking Fixed-Length File Benchmark Platform

## Generating, parsing and benchmarking CODA & SWIFT MT940 files
## across 9 Java formatter approaches via Strategy Pattern + Spring Batch

**Wallace Espindola** · wallace.espindola@gmail.com
[linkedin.com/in/wallaceespindola](https://www.linkedin.com/in/wallaceespindola/) · [github.com/wallaceespindola](https://github.com/wallaceespindola/)

---

## Problem Statement

Multiple Java libraries claim to support fixed-length banking file formats.
**Which one should a bank standardise on for Spring Batch workloads?**

Evaluation criteria:

1. **Correctness** — Does output conform to Febelfin / SWIFT specifications?
2. **Performance** — Throughput in records/second, measured with JMH
3. **Maintainability** — Annotation quality, layout auditability, no hidden XML
4. **Spring Batch fit** — Chunk-oriented reader/writer compatibility
5. **Supply-chain health** — Release cadence, governance, dependency weight
6. **Operational risk** — Support model, key-person risk, CVE history

> *One codebase, 9 approaches, identical domain data, automated benchmarks.*

---

## Architecture

```
Web UI (HTML/CSS/JS)
        │  HTTP REST
        ▼
   REST API Layer           POST /api/domain/generate
   (Spring MVC)             POST /api/batch/generate
        │                   GET  /api/benchmark/results
        ▼
  Spring Batch Pipeline
  DomainEntityItemReader → FileGenerationItemProcessor → FileOutputItemWriter
                                      │
                               StrategyResolver  (O(1) map lookup)
                     /    |    |    |    |    |    |    |    \
                BeanIO  ff4j  VL  Bindy CamelBIO Vel  SB  SB+FF4J  SB+VL
                              ↓
                       18 FileGenerationStrategy implementations
                       (9 approaches × CODA + SWIFT)
```

---

## CODA Format — Belgian Banking Standard

**Febelfin CODA** — fixed-width ASCII, exactly **128 characters** per record

| Record | Meaning |
|--------|---------|
| `0` | File header |
| `1` | Movement (debit/credit transaction) |
| `2` | Movement detail / free communication |
| `8` | Information record (closing balance) |
| `9` | File trailer |

```
0310HDR       BE68539007547034                     EUR000000000000000022052622052...
1310REF0000001BE12345678901234567890123456789012345EUR000000000000075029042629042...
9000TRAILER   ...
```

Each field has an exact byte offset — annotations define the mapping.

---

## SWIFT MT940 — International Statement Format

**SWIFT MT940** — tag-based messages, inter-message separator `---`

| Tag | Field | Example |
|-----|-------|---------|
| `:20:` | Transaction reference | `STMT000001` |
| `:25:` | Account identification | `BE68539007547034/EUR` |
| `:28C:` | Statement / sequence | `00001/001` |
| `:60F:` | Opening balance | `C260429EUR10000,00` |
| `:61:` | Statement line | `260429260429C750NMSCREF001` |
| `:86:` | Narrative | `Payment for services` |
| `:62F:` | Closing balance | `C260429EUR10750,00` |

```
:20:STMT000001
:25:BE68539007547034/EUR
:60F:C260429EUR10000,00
:61:260429260429C750NMSCREF001NONREF
:62F:C260429EUR10750,00
---
```

---

## 9 Formatter Approaches

| Approach | Mechanism | CODA W | CODA R | SWIFT |
|---------|-----------|-----------|-----------|-------|
| **BeanIO** | `StreamBuilder` + `FieldBuilder.at()` (0-based) | ✅ | ✅ | ✅ |
| **fixedformat4j** | `@Record(length=128)` + `@Field(offset, length)` | ✅ | ✅ | ✅ |
| **fixedlength** | `@FixedLine` + `@FixedField(offset, length)` | ✅ | ✅ | ✅ |
| **Camel Bindy** | `@FixedLengthRecord` + `@DataField(pos, length)` | ✅ | ✅ | ✅ |
| **Camel BeanIO** | XML stream mapping via Camel dataformat | ✅ | ✅ | ✅ |
| **Velocity** | `.vm` template files (write-only for CODA) | ✅ | — | ✅ |
| **Spring Batch** | `FormatterLineAggregator` + `FixedLengthTokenizer` | ✅ | ✅ | ✅ |
| **Spring Batch + fixedformat4j** | Spring Batch components, layout from `@Field` | ✅ | ✅ | ✅ |
| **Spring Batch + fixedlength** | Spring Batch components, layout from `@FixedField` | ✅ | ✅ | ✅ |

All approaches share the same domain data and produce byte-comparable output files.

---

## Annotation-Derived Layouts — Fixing the Slicing Problem

Native Spring Batch needs the layout **twice**: `Range` list to read, format string to write.
Two copies of the same offsets drift apart silently.

```java
// Before — layout restated by hand, 12 magic ranges
t.setColumns(new Range(1,1), new Range(2,4), new Range(5,14), /* ... */);

// After — layout read from the annotated model, once
AnnotatedLayout layout = AnnotatedLayout.fromFixedFormat4j(Ff4jCodaRecord.class);
FixedLengthTokenizer tokenizer = layout.tokenizer();   // read path
String format = layout.formatString();                 // write path
```

- Offsets, widths, alignment and padding char come from `@Field` / `@FixedField`
- Constructor validates gaps, overlaps and the 128-char total — layout errors fail fast
- Output is **byte-identical** to hand-sliced Spring Batch (asserted in `AnnotatedLayoutTest`)

---

## Strategy Pattern — One Interface, 18 Implementations

```java
public interface FileGenerationStrategy {
    String generate(List<Transaction> txs, List<Account> accounts);
    List<Transaction> parse(String fileContent);
    FileType getFileType();   // CODA | SWIFT
    Library   getLibrary();   // BEANIO | FIXFORMAT4J | FIXEDLENGTH | BINDY
                              // CAMEL_BEANIO | VELOCITY | SPRING_BATCH
                              // SPRING_BATCH_FIXFORMAT4J | SPRING_BATCH_FIXEDLENGTH
    default String strategyKey() { return getFileType() + "_" + getLibrary(); }
}
```

```java
// Resolution — O(1) map lookup, no if/switch chains
FileGenerationStrategy s = resolver.resolve(FileType.CODA, Library.SPRING_BATCH_FIXFORMAT4J);
String codaFile = s.generate(transactions, accounts);
```

`StrategyResolver` auto-wires all 18 beans from the Spring context at startup.

---

## Spring Batch Pipeline

```
bankingFileGenerationJob  (restartable — saveState=true)
└── fileGenerationStep    (chunk-size = 100)
    ├── DomainEntityItemReader
    │     Loads all Transaction rows from H2
    ├── FileGenerationItemProcessor
    │     Resolves FileGenerationStrategy by (fileType, library)
    │     Calls strategy.generate([transaction], accounts)
    └── FileOutputItemWriter
          Buffers chunk output; on @AfterStep writes output/*.txt
          Stores file content + metadata in step ExecutionContext
               │
               ▼
         BatchMetricsListener (JobExecutionListener)
         Saves BenchmarkMetrics row to H2 on job completion
```

Job parameters: `fileType`, `library`, `operationId`, `runTimestamp`

---

## Library Health — Verified 2026-07-27

| Library | Coordinates (G:A) | Pinned | Latest | Latest released | Last repo activity |
|---|---|---|---|---|---|
| BeanIO | `com.github.beanio:beanio` | 3.2.1 | 3.2.1 | 2025-02-07 | 2025-02-07 |
| fixedformat4j | `com.ancientprogramming.fixedformat4j:fixedformat4j` | 1.9.1 | 1.9.1 | 2026-06-17 | 2026-07-25 |
| fixedlength | `name.velikodniy.vitaliy:fixedlength` | 0.15 | 0.15 | 2026-02-26 | 2026-02-26 |
| Camel Bindy / BeanIO | `org.apache.camel:camel-bindy` · `camel-beanio` | 4.21.0 | 4.21.0 | 2026-06-27 | daily |
| Velocity | `org.apache.velocity:velocity-engine-core` | 2.4.1 | 2.4.1 | 2024-10-14 | 2026-06-14 |
| Spring Batch | `org.springframework.batch:spring-batch-core` | 5.2.2 | 6.0.4 | 2026-06-10 | 2026-07-23 |

Sources: Maven Central `maven-metadata.xml` + artifact `Last-Modified`, GitHub REST API.
Spring Batch 5.2.2 is what Spring Boot 3.4.5 resolves; 6.x needs Boot 4.x.

---

## Adoption & Governance

| Library | GitHub repo | Stars | Governance | Dependents¹ | License |
|---|---|---:|---|---:|---|
| BeanIO | `beanio/beanio` | 68 | Community fork of the 2014 `org.beanio` line | 10 | Apache-2.0 |
| fixedformat4j | `jeyben/fixedformat4j` | 52 | Single maintainer, active | 2 | Apache-2.0 |
| fixedlength | `g0ddest/fixedlength` | 23 | Single maintainer, 0.x versioning | 0 | Apache-2.0 |
| Camel Bindy / BeanIO | `apache/camel` | 6 273 | Apache Software Foundation | 4 | Apache-2.0 |
| Velocity | `apache/velocity-engine` | 413 | Apache Software Foundation | 1 560 | Apache-2.0 |
| Spring Batch | `spring-projects/spring-batch` | 2 947 | Broadcom/VMware, commercial support | 40 | Apache-2.0 |

¹ deps.dev dependent packages for the **pinned version only** — a directional proxy.
**Maven Central publishes no public download counts**; treat any "downloads" figure elsewhere as an estimate.

---

## Supply-Chain Weight

| Approach | Own jar | Transitive cost | Notes |
|---|---:|---|---|
| BeanIO | 430 KB | none | Self-contained |
| fixedformat4j | 125 KB | none | Smallest annotation-driven option |
| fixedlength | 33 KB | none | Tiny; 0.x API stability caveat |
| Camel Bindy | 171 KB | `camel-support`, `camel-api`, **icu4j 14 MB** | 45 Camel artifacts on the tree |
| Camel BeanIO | 27 KB | Camel core + BeanIO 2.x | Two ecosystems in one path |
| Velocity | 503 KB | commons-lang3, slf4j | Templates are executable code |
| Spring Batch (3 variants) | 0 KB extra | already on the classpath | Batch runtime is a given |

**Security note:** Velocity < 2.3 carried CVE-2020-13936 (template → RCE). Pinned 2.4.1 is not affected,
but the risk class remains: `.vm` templates must be version-controlled and never user-supplied.

---

## Bank Suitability Matrix

| Approach | Grammar power | Layout auditability | Batch fit | Support model | Bank verdict |
|---|---|---|---|---|---|
| BeanIO | **High** (record groups, repeating segments) | Programmatic builder | Good | Community only | Good for complex CODA grammars; accept key-person risk |
| fixedformat4j | Low (flat records) | **Annotations** | Excellent | Single maintainer | Strong for simple fixed layouts |
| fixedlength | Low | Annotations | Good | Single maintainer, 0.x | Prototyping, not core payments |
| Camel Bindy | Medium | Annotations | Medium | ASF | Only if Camel already runs in production |
| Camel BeanIO | High | XML mapping files | Medium | ASF | Auditable XML, heavy dependency path |
| Velocity | N/A (write-only) | Templates | Low | ASF | Report rendering, never parsing |
| Spring Batch native | Medium | **Code** (`Range` list) | **Native** | Broadcom/VMware | Safe default; layout duplicated by hand |
| **Spring Batch + fixedformat4j** | Medium | **Annotations** | **Native** | Broadcom + single maintainer | **Best overall fit for a bank** |
| **Spring Batch + fixedlength** | Medium | **Annotations** | **Native** | Broadcom + single maintainer | Same shape, smaller dependency |

---

## Measured Performance — JMH

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

Throughput in ops/s (one op = 20 transactions × 5 accounts). Highest per column in bold.

- **fixedformat4j leads CODA** — ~1.3× the write throughput and ~1.5× the read throughput of the next best
- **Annotation-derived layout costs nothing at runtime** — both hybrids sit within noise of native Spring Batch;
  reflection runs once at bean construction, never per record
- **SWIFT converges (~11k write / ~13–14k read)** — all approaches share `SwiftMtRecord`; only Velocity's
  template rendering (4 110 ops/s) separates from the pack
- Median of 3 runs. A run taken while the machine had heavy background load produced numbers up to 75 % lower
  on the allocation-heavy CODA paths — measure on a quiet machine, and treat gaps under ~20 % as noise

Method: JMH throughput mode, 1 fork, 2×1 s warm-up, 3×2 s measurement, 5 accounts / 20 transactions per op,
Java 21 on Apple Silicon, **median of 3 runs**. Re-run with `mvn test -Pbenchmark` — absolute values are
machine-specific, the **ranking** is what transfers.

---

## Measured Performance — Batch Pipeline

End-to-end Spring Batch job (`POST /api/batch/generate`), MEDIUM profile — 100 accounts / 1 000 transactions,
H2 in-memory, chunk size 100. Median of 4 warm runs, records/second:

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

Every approach clears 1 000 records in **under 60 ms** end-to-end; most land between 14 ms and 18 ms,
where millisecond timer resolution dominates. Only Velocity and Camel Bindy separate from the pack here.

Pipeline numbers include JPA read, strategy call, file write and metrics persistence,
so they compress the differences visible in the JMH numbers.

---

## Decision Guide

| Use case | Pick | Why |
|----------|------|-----|
| New Spring Batch job in a bank | **Spring Batch + fixedformat4j** | Batch-native runtime, layout declared once in annotations, no extra runtime deps |
| Minimal dependency footprint | **Spring Batch + fixedlength** | 33 KB library, same annotation-driven layout |
| Complex CODA grammar (record groups) | **BeanIO** | Richest grammar model of the set |
| Camel routes already in production | **Camel Bindy** | Native dataformat inside existing routes |
| Auditor wants layout outside the code | **Camel BeanIO** | XML mapping files, reviewable without Java |
| Rendering statements/reports | **Velocity** | Template engine, write-only by design |
| No new dependency allowed at all | **Spring Batch native** | Ships with the batch runtime |

> **Recommendation:** standardise on one approach per system. Benchmark on your own hardware first —
> then pin the version and treat the layout model as a controlled artefact.

---

## REST API

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/domain/generate` | Seed H2 with sample data (`?loadProfile=LOW\|MEDIUM\|HIGH`) |
| `POST` | `/api/batch/generate` | Trigger Spring Batch job `{fileType, library}` |
| `GET` | `/api/batch/history` | Last 50 job executions |
| `GET` | `/api/benchmark/results` | All benchmark metrics |
| `GET` | `/api/benchmark/export/csv` | Export as CSV |
| `GET` | `/api/benchmark/export/json` | Export as JSON |
| `GET` | `/api/benchmark/export/markdown` | Export as Markdown |
| `GET` | `/api/benchmark/export/html` | Velocity-rendered HTML report |
| `GET` | `/actuator/health` | Application health + version |
| `GET` | `/actuator/info` | App name, version, description |

---

## Benchmark Metrics

| Metric | Description |
|--------|-------------|
| `throughputRps` | Records processed per second |
| `batchDurationMs` | Total Spring Batch job wall-clock time |
| `generationDurationMs` | File serialisation time only |
| `parseDurationMs` | File parsing time (round-trip) |
| `symmetryRate` | % of parsed transactions matching original domain data |
| `successRate` | % of chunks completed without error |

```bash
# Run JMH benchmark suite (36 @Benchmark methods)
mvn test -Pbenchmark

# Export results
curl http://localhost:8080/api/benchmark/export/csv -o results.csv
curl http://localhost:8080/api/benchmark/export/json
```

---

## Code Quality & CI/CD

**Testing** — 149 tests across 13 test classes:

| Category | Tests | Coverage |
|----------|-------|----------|
| Unit | DomainDataGeneratorTest, CodaRecordTest, AnnotatedLayoutTest | Mock repos, field validation, layout reflection |
| Integration | StrategyResolverTest, CodaStrategyTest, SwiftStrategyTest | All 18 strategies |
| Symmetry | SymmetryTest | Round-trip: generate → parse → compare |
| Golden file | GoldenFileTest | 128-char CODA lines, MT940 tags |
| API | DomainControllerTest, BatchControllerTest | MockMvc |
| Actuator | ActuatorTest, SwaggerAvailabilityTest | TestRestTemplate |

**CI/CD:** GitHub Actions — build · test · benchmark · CodeQL · release
**Coverage:** JaCoCo enforced at minimum threshold · Dependabot weekly PRs

---

## Quick Start

```bash
# Clone and build (Java 21 + Maven 3.9 required — no Node.js needed)
git clone https://github.com/wallaceespindola/fixed-length-converters
cd fixed-length-converters
mvn clean install

# Start in dev mode (Swagger UI at /swagger-ui.html)
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Step 1 — Generate domain data
curl -X POST http://localhost:8080/api/domain/generate?loadProfile=HIGH

# Step 2 — Run batch job (pick any approach)
curl -X POST http://localhost:8080/api/batch/generate \
  -H "Content-Type: application/json" \
  -d '{"fileType":"CODA","library":"SPRING_BATCH_FIXFORMAT4J"}'

# Step 3 — Export benchmark results
curl http://localhost:8080/api/benchmark/export/csv -o results.csv
```

---

## Technology Stack

| Area | Technology |
|------|-----------|
| Language | Java 21 |
| Backend | Spring Boot 3.4.5, Spring Batch 5.2.2, Spring Data JPA |
| Database | H2 In-Memory |
| API Docs | OpenAPI + Swagger UI (dev profile) |
| Monitoring | Spring Actuator (`/health`, `/info`, version indicator) |
| Frontend | Vanilla HTML/CSS/JS + Chart.js |
| Build | Maven (single `mvn clean install`, no profiles) |
| Testing | JUnit 5 + Mockito, 149 tests, 36 JMH benchmarks |
| CI/CD | GitHub Actions (build, test, benchmark, CodeQL) |

---

<!-- _class: title -->

# Thank You

## github.com/wallaceespindola/fixed-length-converters

**Wallace Espindola**
wallace.espindola@gmail.com

[linkedin.com/in/wallaceespindola](https://www.linkedin.com/in/wallaceespindola/)
[github.com/wallaceespindola](https://github.com/wallaceespindola/)

*Questions welcome — slides, code and benchmarks all open source*
