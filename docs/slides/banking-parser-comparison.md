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

# Banking Fixed-Length File Platform

## Generating, parsing and benchmarking CODA & SWIFT MT940 files
## across 7 Java formatter libraries via Strategy Pattern + Spring Batch

**Wallace Espindola** · wallace.espindola@gmail.com
[linkedin.com/in/wallaceespindola](https://www.linkedin.com/in/wallaceespindola/) · [github.com/wallaceespindola](https://github.com/wallaceespindola/)

---

## Problem Statement

Multiple Java libraries claim to support fixed-length banking file formats.
**Which one is best for enterprise Spring Batch use?**

Evaluation criteria:

1. **Correctness** — Does output conform to Febelfin / SWIFT specifications?
2. **Performance** — Throughput in records/second under realistic load
3. **Maintainability** — Annotation quality, no XML, clean code
4. **Spring Batch fit** — Chunk-oriented reader/writer compatibility

> *One codebase, 7 libraries, identical domain data, automated benchmarks.*

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
                              /    |    |    |    |    |    \
                         BeanIO  ff4j  VL  Bindy CamelBIO Vel SB
                              ↓
                       14 FileGenerationStrategy implementations
                       (7 libraries × CODA + SWIFT)
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

## 7 Parser Libraries

| Library | Mechanism | CODA Write | CODA Read | SWIFT |
|---------|-----------|-----------|-----------|-------|
| **BeanIO** | `@Record` + `@Field` annotations | ✅ | ✅ | ✅ |
| **fixedformat4j** | `@Record(length=128)` + `@Field(offset, length)` | ✅ | ✅ | ✅ |
| **fixedlength** | `@FixedLine` + `@FixedField(offset, length)` | ✅ | ✅ | ✅ |
| **Camel Bindy** | `@FixedLengthRecord` + `@DataField(pos, length)` | ✅ | ✅ | ✅ |
| **Camel BeanIO** | XML stream mapping | ✅ | ✅ | ✅ |
| **Velocity** | `.vm` template files (write-only for CODA) | ✅ | — | ✅ |
| **Spring Batch** | `FormatterLineAggregator` + `FixedLengthTokenizer` | ✅ | ✅ | ✅ |

All libraries share the same domain data and produce comparable output files.

---

## Strategy Pattern — One Interface, 14 Implementations

```java
public interface FileGenerationStrategy {
    String generate(List<Transaction> txs, List<Account> accounts);
    List<Transaction> parse(String fileContent);
    FileType getFileType();   // CODA | SWIFT
    Library   getLibrary();   // BEANIO | FIXFORMAT4J | FIXEDLENGTH
                              // BINDY | CAMEL_BEANIO | VELOCITY | SPRING_BATCH
    default String strategyKey() { return getFileType() + "_" + getLibrary(); }
}
```

```java
// Resolution — O(1) map lookup, no if/switch chains
FileGenerationStrategy s = resolver.resolve(FileType.CODA, Library.BEANIO);
String codaFile = s.generate(transactions, accounts);
```

`StrategyResolver` auto-wires all 14 beans from Spring context at startup.

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

## REST API

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/domain/generate` | Seed H2 with sample data (`?loadProfile=LOW\|HIGH`) |
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
# Run JMH benchmark suite (28 @Benchmark methods)
mvn test -Pbenchmark

# Export results
curl http://localhost:8080/api/benchmark/export/csv -o results.csv
curl http://localhost:8080/api/benchmark/export/json
```

---

## Library Recommendations

| Use Case | Recommended Library | Reason |
|----------|--------------------|----|
| Enterprise CODA processing | **BeanIO** | Best grammar support, battle-tested |
| New projects, modern code | **fixedformat4j** | Best annotation DX, no boilerplate |
| Existing Camel ecosystem | **Camel Bindy** | Native Camel route integration |
| Lightweight / prototyping | **fixedlength** | Minimal setup, pure annotations |
| Template-driven reports | **Velocity** | Flexible .vm template rendering |
| Tightest Spring Batch fit | **Spring Batch native** | Reuses existing batch components |

> **Recommendation:** Pick one library and standardise across the codebase.
> Don't mix libraries in production — benchmark first, then commit.

---

## Code Quality & CI/CD

**Testing** — 118 tests across 12 test classes:

| Category | Tests | Coverage |
|----------|-------|----------|
| Unit | DomainDataGeneratorTest, CodaRecordTest | Mock repos, field validation |
| Integration | StrategyResolverTest, CodaStrategyTest, SwiftStrategyTest | All 14 strategies |
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

# Step 2 — Run batch job (pick any library)
curl -X POST http://localhost:8080/api/batch/generate \
  -H "Content-Type: application/json" \
  -d '{"fileType":"CODA","library":"BEANIO"}'

# Step 3 — Export benchmark results
curl http://localhost:8080/api/benchmark/export/csv -o results.csv
```

---

## Technology Stack

| Area | Technology |
|------|-----------|
| Language | Java 21 |
| Backend | Spring Boot, Spring Batch, Spring Data JPA |
| Database | H2 In-Memory |
| API Docs | OpenAPI + Swagger UI (dev profile) |
| Monitoring | Spring Actuator (`/health`, `/info`) |
| Frontend | Vanilla HTML/CSS/JS + Chart.js |
| Build | Maven (single `mvn clean install`, no profiles) |
| Testing | JUnit 5 + Mockito, 118 tests, JMH benchmarks |
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
