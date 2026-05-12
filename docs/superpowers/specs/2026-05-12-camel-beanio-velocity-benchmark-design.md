# Design: Camel BeanIO + Velocity + Spring Batch Native as Benchmark Library Participants

| Field | Value |
|---|---|
| **Date** | 2026-05-12 |
| **Author** | Wallace Espindola |
| **Status** | Approved |
| **PRD Reference** | docs/PRD.md §5, §11, §13 |

---

## Summary

Extend the benchmark platform from 4 to 7 parser library participants by integrating:

1. **Apache Camel BeanIO** (`camel-beanio` 4.20.0) — Camel's BeanIO DataFormat as a distinct integration path from the existing direct-BeanIO formatter
2. **Apache Velocity** (`velocity-engine-core` 2.3, `velocity-tools-generic` 3.1) — template-driven file generation (`.vm` templates) as a 6th approach; additionally used as the HTML benchmark report exporter
3. **Spring Batch Native** — `FlatFileItemWriter` + custom `LineAggregator` + `FlatFileItemReader` + `FixedLengthTokenizer` + `FieldSetMapper` as a 7th approach, using Spring Batch's own flat-file infrastructure directly

The platform grows from **8 strategies / 8 JMH benchmarks** to **14 strategies / 28 JMH benchmarks** (7 libraries × 2 formats × generate+parse).

---

## 1. Library Enum

`com.wtechitsolutions.domain.Library` gains three values:

```java
BEANIO, FIXFORMAT4J, FIXEDLENGTH, BINDY, CAMEL_BEANIO, VELOCITY, SPRING_BATCH
```

`StrategyResolver` and `StrategyResolverTest` require no changes — they iterate `Library.values()` and automatically cover the new entries.

---

## 2. New Formatters

### 2.1 CamelBeanIOFormatter

**Location:** `parser/CamelBeanIOFormatter.java`

- `@Component` with `@PostConstruct` / `@PreDestroy` lifecycle (mirrors `BindyFormatter`)
- Initialises a standalone `CamelContext` + `BeanIODataFormat` backed by a classpath mapping file
- BeanIO mapping file: `src/main/resources/beanio/coda-mapping.xml` (same field layout as `BeanIOFormatter`'s `StreamBuilder`)
- `formatCoda(List<CodaRecord>)` → `BeanIODataFormat.marshal(exchange, records, out)`
- `parseCoda(String)` → `BeanIODataFormat.unmarshal(exchange, in)`
- `formatSwift` / `parseSwift` → delegates to `SwiftMtRecord.toSwiftFormat()` / `SwiftMtRecord.fromSwiftSection()` (same as all other Swift strategies)

**Benchmark rationale:** Exercises Camel's marshalling pipeline overhead vs the direct BeanIO API used by `BeanIOFormatter`. A legitimate comparison point for teams already in the Camel ecosystem.

### 2.2 VelocityFormatter

**Location:** `parser/VelocityFormatter.java`

- `@Component`, no lifecycle hooks (VelocityEngine is stateless after init)
- Uses `ClasspathResourceLoader`; templates at:
  - `src/main/resources/velocity/coda-record.vm` — produces 128-char fixed-width CODA lines
  - `src/main/resources/velocity/swift-record.vm` — produces SWIFT MT940 tag-based output
  - `src/main/resources/velocity/benchmark-report.vm` — HTML benchmark report (Section 5)
- `formatCoda(List<CodaRecord>)` → merges `$records` context into `coda-record.vm`
- `parseCoda(String)` → delegates to `CodaRecord.fromFixedWidth()` (Velocity output has identical structure to all other CODA strategies)
- `formatSwift(List<SwiftMtRecord>)` → merges `$records` context into `swift-record.vm`
- `parseSwift(String)` → delegates to `SwiftMtRecord.fromSwiftSection()`

### 2.3 SpringBatchFormatter

**Location:** `parser/SpringBatchFormatter.java`

- `@Component`
- Uses Spring Batch's flat-file infrastructure in standalone (in-memory) mode:
  - **Write path:** custom `LineAggregator<CodaRecord>` that produces a 128-char fixed-width string; an in-memory `ItemWriter` collects lines into a `StringBuilder`
  - **Read path:** `FixedLengthTokenizer` with named columns and field ranges matching the CODA layout; `BeanWrapperFieldSetMapper<BeanIoCodaRecord>` maps tokens to fields; adapter converts to `CodaRecord`
- `formatCoda(List<CodaRecord>)` → aggregates each record via `LineAggregator`, joins with newline
- `parseCoda(String)` → tokenizes each line via `FixedLengthTokenizer`, maps via `FieldSetMapper`, converts to `CodaRecord`
- `formatSwift` / `parseSwift` → delegates to `SwiftMtRecord.toSwiftFormat()` / `SwiftMtRecord.fromSwiftSection()` (same as all other Swift strategies)

No Camel or external framework context required — uses only Spring Batch classes already on the classpath.

---

## 3. New Strategy Classes

Six new classes, each 15–25 lines, following the identical pattern of existing strategies:

| Class | Format | Library | Formatter |
|---|---|---|---|
| `CodaCamelBeanIOStrategy` | CODA | CAMEL_BEANIO | `CamelBeanIOFormatter` |
| `SwiftCamelBeanIOStrategy` | SWIFT | CAMEL_BEANIO | `CamelBeanIOFormatter` |
| `CodaVelocityStrategy` | CODA | VELOCITY | `VelocityFormatter` |
| `SwiftVelocityStrategy` | SWIFT | VELOCITY | `VelocityFormatter` |
| `CodaSpringBatchStrategy` | CODA | SPRING_BATCH | `SpringBatchFormatter` |
| `SwiftSpringBatchStrategy` | SWIFT | SPRING_BATCH | `SpringBatchFormatter` |

Each is a `@Service` that extends `AbstractCodaStrategy` or `AbstractSwiftStrategy`, injects its formatter, implements `getLibrary()`, and delegates `formatRecords()` / `parseRecords()` to the formatter.

---

## 4. Velocity Templates

### 4.1 `coda-record.vm`

Iterates `$records`, writes each as a 128-character fixed-width line using Velocity's `#foreach` + string padding macros. Output is structurally identical to all other CODA formatters.

### 4.2 `swift-record.vm`

Iterates `$records`, emits MT940 tags (`:20:`, `:25:`, `:28C:`, `:60F:`, `:61:`, `:86:`, `:62F:`) with `---` record delimiter. Output matches all other SWIFT strategies.

### 4.3 `benchmark-report.vm`

Produces a self-contained HTML5 page:
- Inline CSS — table styled with color coding per library name
- One `<tr>` per `BenchmarkMetrics` entry
- Columns: ID, FileType, Library, Throughput (ops/s), Batch Duration (ms), Gen Duration (ms), Records, Success Rate, Timestamp

---

## 5. Velocity HTML Report Export

**`BenchmarkService.exportAsHtml()`** — new method injecting `VelocityFormatter` (or using `VelocityEngine` directly). Retrieves all metrics, merges into `benchmark-report.vm`, returns the rendered HTML string.

**`BenchmarkController`** — new endpoint:

```
GET /api/benchmark/export/html   → 200 text/html; charset=UTF-8
```

Sits alongside the existing `/export/csv`, `/export/json`, `/export/markdown` endpoints.

---

## 6. JMH Benchmark Expansion

`FileGenerationBenchmark` grows from 8 to **28 `@Benchmark` methods**:

| Category | Methods |
|---|---|
| Generate (all 7 libraries × CODA) | `codaBeanIO`, `codaFixedFormat4J`, `codaFixedLength`, `codaBindy`, `codaCamelBeanIO`, `codaVelocity`, `codaSpringBatch` |
| Generate (all 7 libraries × SWIFT) | `swiftBeanIO`, `swiftFixedFormat4J`, `swiftFixedLength`, `swiftBindy`, `swiftCamelBeanIO`, `swiftVelocity`, `swiftSpringBatch` |
| Parse (all 7 libraries × CODA) | `codaBeanIOParse`, `codaFixedFormat4JParse`, `codaFixedLengthParse`, `codaBindyParse`, `codaCamelBeanIOParse`, `codaVelocityParse`, `codaSpringBatchParse` |
| Parse (all 7 libraries × SWIFT) | `swiftBeanIOParse`, `swiftFixedFormat4JParse`, `swiftFixedLengthParse`, `swiftBindyParse`, `swiftCamelBeanIOParse`, `swiftVelocityParse`, `swiftSpringBatchParse` |

Parse benchmarks pre-generate a file string per strategy in `@Setup` and call `.parse()` in the benchmark method.

---

## 7. New Classpath Resources

```
src/main/resources/
├── beanio/
│   └── coda-mapping.xml          BeanIO fixed-length stream definition (mirrors BeanIOFormatter StreamBuilder)
└── velocity/
    ├── coda-record.vm             CODA 128-char fixed-width template
    ├── swift-record.vm            SWIFT MT940 tag template
    └── benchmark-report.vm        HTML benchmark report template
```

---

## 8. Test Expansion

| Test Class | Change |
|---|---|
| `StrategyResolverTest` | No change — parametrized test auto-covers new enum values |
| `CodaStrategyTest` | Add test cases for `CAMEL_BEANIO`, `VELOCITY`, `SPRING_BATCH`: 128-char lines, header/trailer presence |
| `SwiftStrategyTest` | Add test cases for `CAMEL_BEANIO`, `VELOCITY`, `SPRING_BATCH`: MT940 tags, `---` delimiter |
| `SymmetryTest` | Add round-trip cases for `CAMEL_BEANIO`, `VELOCITY`, `SPRING_BATCH` (generate → parse → field equality) |
| `BenchmarkControllerTest` | Add `GET /api/benchmark/export/html` → 200 + `text/html` assertion |

---

## 9. Frontend Changes

**`BatchRunner` component** — add `CAMEL_BEANIO`, `VELOCITY`, and `SPRING_BATCH` to the library selector dropdown.

**Benchmark dashboard charts** — no code changes required. Charts read library names from the API response (`/api/benchmark/results`), which naturally includes new library metrics after jobs are run.

---

## 10. Files to Create / Modify

### New files
- `src/main/java/com/wtechitsolutions/parser/CamelBeanIOFormatter.java`
- `src/main/java/com/wtechitsolutions/parser/VelocityFormatter.java`
- `src/main/java/com/wtechitsolutions/parser/SpringBatchFormatter.java`
- `src/main/java/com/wtechitsolutions/strategy/CodaCamelBeanIOStrategy.java`
- `src/main/java/com/wtechitsolutions/strategy/SwiftCamelBeanIOStrategy.java`
- `src/main/java/com/wtechitsolutions/strategy/CodaVelocityStrategy.java`
- `src/main/java/com/wtechitsolutions/strategy/SwiftVelocityStrategy.java`
- `src/main/java/com/wtechitsolutions/strategy/CodaSpringBatchStrategy.java`
- `src/main/java/com/wtechitsolutions/strategy/SwiftSpringBatchStrategy.java`
- `src/main/resources/beanio/coda-mapping.xml`
- `src/main/resources/velocity/coda-record.vm`
- `src/main/resources/velocity/swift-record.vm`
- `src/main/resources/velocity/benchmark-report.vm`

### Modified files
- `src/main/java/com/wtechitsolutions/domain/Library.java` — add `CAMEL_BEANIO`, `VELOCITY`, `SPRING_BATCH`
- `src/main/java/com/wtechitsolutions/benchmark/BenchmarkService.java` — add `exportAsHtml()`
- `src/main/java/com/wtechitsolutions/api/BenchmarkController.java` — add `/export/html` endpoint
- `src/test/java/com/wtechitsolutions/benchmark/FileGenerationBenchmark.java` — add 20 new methods (12 parse methods for existing 4 libraries + 6 generate + 6 parse for 3 new libraries × 2 formats)
- `src/test/java/com/wtechitsolutions/strategy/CodaStrategyTest.java` — add 3 new library cases (CAMEL_BEANIO, VELOCITY, SPRING_BATCH)
- `src/test/java/com/wtechitsolutions/strategy/SwiftStrategyTest.java` — add 3 new library cases
- `src/test/java/com/wtechitsolutions/strategy/SymmetryTest.java` — add 3 new round-trip cases
- `src/test/java/com/wtechitsolutions/api/BenchmarkControllerTest.java` — add HTML export test
- `src/main/frontend/src/` — `BatchRunner` dropdown update

---

## 11. Acceptance Criteria

- [ ] `Library` enum has 7 values; `StrategyResolver` registers 14 strategies on startup
- [ ] All 14 `@Benchmark` generate methods produce non-empty file content
- [ ] All 14 `@Benchmark` parse methods return a non-empty transaction list
- [ ] `CodaStrategyTest` passes for all 7 CODA libraries (128-char lines, header+trailer)
- [ ] `SwiftStrategyTest` passes for all 7 SWIFT libraries (MT940 tags, `---` delimiter)
- [ ] `SymmetryTest` passes for all 7 libraries (round-trip amount + type preserved)
- [ ] `GET /api/benchmark/export/html` returns 200 with `text/html` content
- [ ] `mvn test -Pskip-frontend` passes all 76+ tests
- [ ] `mvn test -Pbenchmark -Pskip-frontend` runs 28 JMH benchmarks
