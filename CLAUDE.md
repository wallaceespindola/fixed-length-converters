# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Banking Fixed-Length File Benchmark Platform** — enterprise-grade experimentation platform for generating, parsing, and benchmarking CODA and SWIFT MT banking files using 9 Java fixed-length formatter approaches (7 libraries + 2 Spring Batch hybrids driven by annotation-derived layouts) via the Strategy Pattern and Spring Batch.

**PRD:** `docs/PRD.md` (v3.0, authoritative) | **Design spec:** `docs/specs/design-spec.md` | **Implementation plan:** `docs/implementation-plan.md`
**Library analysis:** `docs/benchmark-results.md` (health, adoption, bank suitability, JMH numbers) | **Slides:** `docs/slides/` (Marp `.md` + 22-slide PPTX)

## Implementation Status: COMPLETE

All 155 tests pass. Application starts and runs end-to-end.

Frontend uses an orange color theme (`#e65100`; throughput chart bars also orange). Single `index.html` served from `src/main/resources/static/` — no Node.js or npm required.

## Technical Stack

| Area | Technology |
|---|---|
| Language | Java 21 (target: 25) |
| Backend | Spring Boot 3.4.5 |
| Batch | Spring Batch 5.x |
| Monitoring | Spring Actuator |
| Database | H2 In-Memory |
| API Docs | OpenAPI V3 + Swagger (dev profile only) |
| Build | Maven 3.9.x — no profiles required |
| Testing | JUnit 5 + Mockito, 155 tests |
| Libraries | BeanIO 3.2.1, fixedformat4j 1.9.1, fixedlength 0.15, Camel Bindy 4.21.0, Camel BeanIO 4.21.0, Velocity 2.4.1, Spring Batch 5.x |
| Frontend | Vanilla HTML/CSS/JS (`src/main/resources/static/index.html`), Chart.js via CDN |
| CI/CD | GitHub Actions (build, test, benchmark, codeql, release) |

## Build & Run Commands

```bash
# Full pipeline (Java compile + 155 tests + JaCoCo + repackage + install)
mvn clean install

# Quick build (no tests)
mvn clean install -DskipTests

# Run in dev mode (Swagger UI enabled at /swagger-ui.html)
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# All tests
mvn test

# Integration tests with coverage
mvn verify

# Run single test class
mvn test -Dtest=StrategyResolverTest

# JMH benchmarks
mvn test -Pbenchmark

# Makefile shortcuts
make build | make run | make test | make benchmark | make clean | make kill
```

## Architecture

### Package Structure

```
src/main/java/com/wtechitsolutions/
├── api/               REST controllers (DomainController, BatchController, BenchmarkController)
│   └── dto/           Java Record DTOs (BatchJobRequest/Response, GenerateDomainResponse, etc.)
├── batch/             Spring Batch: DomainEntityItemReader, FileGenerationItemProcessor,
│                       FileOutputItemWriter, BatchMetricsListener, ChunkTimingListener, BatchJobService
├── benchmark/         BenchmarkService (CSV/JSON/Markdown/HTML export)
├── config/            BatchConfig (no @EnableBatchProcessing!), OpenApiConfig, WebConfig, VersionHealthIndicator
├── domain/            JPA entities (Account, Transaction, BankingStatement, BenchmarkMetrics)
│                       Repositories, DomainDataGenerator, enums (FileType, Library, LoadProfile, TransactionType)
├── parser/            9 formatter wrappers (annotation-based, template-based, or programmatic):
│   │                   BeanIOFormatter, FixedFormat4JFormatter, FixedLengthFormatter, BindyFormatter,
│   │                   CamelBeanIOFormatter, VelocityFormatter, SpringBatchFormatter,
│   │                   SpringBatchFixedFormat4JFormatter, SpringBatchFixedLengthFormatter
│   │                   (both extend AnnotatedSpringBatchFormatter; layout read via AnnotatedLayout)
│   └── model/         Annotated model classes per library (CodaRecord, BeanIoCodaRecord, etc.)
└── strategy/          FileGenerationStrategy interface, StrategyResolver, 18 implementations:
                        AbstractCodaStrategy, AbstractSwiftStrategy (base classes)
                        CodaBeanIOStrategy, CodaFixedFormat4JStrategy, CodaFixedLengthStrategy, CodaBindyStrategy
                        CodaCamelBeanIOStrategy, CodaVelocityStrategy, CodaSpringBatchStrategy
                        SwiftBeanIOStrategy, SwiftFixedFormat4JStrategy, SwiftFixedLengthStrategy, SwiftBindyStrategy
                        SwiftCamelBeanIOStrategy, SwiftVelocityStrategy, SwiftSpringBatchStrategy
                        CodaSpringBatchFixedFormat4JStrategy, CodaSpringBatchFixedLengthStrategy
                        SwiftSpringBatchFixedFormat4JStrategy, SwiftSpringBatchFixedLengthStrategy
```

### Core Flow

1. `POST /api/domain/generate` → `DomainDataGenerator` seeds H2; supports `?loadProfile=LOW` (default: 10 accounts, 100 transactions, 5 statements), `?loadProfile=MEDIUM` (100 accounts, 1 000 transactions, 50 statements), or `?loadProfile=HIGH` (1 000 accounts, 10 000 transactions, 500 statements)
2. `POST /api/batch/generate` → `BatchJobService.launch(fileType, library)` → Spring Batch job
3. Spring Batch: `DomainEntityItemReader` (H2) → `FileGenerationItemProcessor` (StrategyResolver) → `FileOutputItemWriter` (writes to `/output/`)
4. `BatchMetricsListener.afterJob()` → saves `BenchmarkMetrics` row

### Strategy Pattern

`StrategyResolver` maps all `FileGenerationStrategy` beans by `strategyKey()` = `"FILETYPE_LIBRARY"`. Resolution is O(1) map lookup, no if/switch chains.

### Annotation-Derived Layouts (SPRING_BATCH_FIXFORMAT4J / SPRING_BATCH_FIXEDLENGTH)

- `AnnotatedLayout` reflects over a model's field annotations — fixedformat4j `@Field` or fixedlength
  `@FixedField` — and generates the Spring Batch `FixedLengthTokenizer` ranges **and** the
  `FormatterLineAggregator` printf format string. No hand-written `Range(1,1), Range(2,4), ...` list
- Layout is validated at construction: gaps/overlaps throw, total width must be 128
- printf pads with spaces only, so columns whose annotation declares a non-space padding char
  (the zero-padded amount) are pre-filled by `AnnotatedSpringBatchFormatter.pad()`
- `@FixedField` defaults to `Align.RIGHT` — `VlCodaRecord` therefore declares `align = Align.LEFT`
  explicitly on every text field; the annotations are now the single source of truth for that layout
- Output is byte-identical to the hand-sliced `SPRING_BATCH` strategy (asserted by `AnnotatedLayoutTest`)

### CODA Format Notes

- Each record: **exactly 128 characters** per Febelfin spec
- Record types: 0=header, 1=movement, 2=detail, 8=trailer, 9=end
- Amounts: **plain integer** (no decimal places) stored as left-zero-padded 16-char string
- `padAmount()` uses `setScale(0, ROUND_HALF_UP)` to strip decimal scale from H2 BigDecimal values
- BeanIO uses `FieldBuilder.at()` with **0-based** character positions (NOT 1-based)
- `BindyCodaRecord` text fields carry explicit `align="L"` to force left-alignment (Camel Bindy defaults to right-align, which pushed "TOTAL" to the end of the description field in trailer records)
- **Windows CRLF**: `AbstractCodaStrategy.generate()` and `AbstractSwiftStrategy.generate()` call `.replace("\r\n", "\n")` on `formatRecords()` output — BeanIO uses `System.lineSeparator()` and Velocity uses template file endings, both produce CRLF on Windows which would make lines 129 chars

### SWIFT Format Notes

- All 9 formatters serialise as MT940 tag format (`:20:`, `:25:`, `:28C:`, `:60F:`, `:61:`, `:86:`, `:62F:`) with `---` record delimiters between messages
- The `---\n` separator is standardized across all formatters; previously `BindyFormatter` used `###` and `FixedLengthFormatter` used `===`
- BeanIO previously used CSV format; fixed to be consistent with the other strategies

## REST API Endpoints

```
POST /api/domain/generate        → generate + persist domain data; optional ?loadProfile=LOW|MEDIUM|HIGH
DELETE /api/domain/reset         → wipe accounts/transactions/statements + BenchmarkMetrics (Spring Batch job metadata kept)
POST /api/batch/generate         → trigger job; body: {"fileType":"CODA","library":"BEANIO"}
GET  /api/batch/history          → last 50 job executions
GET  /api/batch/files            → list generated files in output/ (newest first)
GET  /api/batch/files/{name}     → file content; name validated ([A-Za-z0-9._-]+\.txt, no separators) against path traversal
GET  /api/benchmark/results      → all benchmark metrics
GET  /api/benchmark/export/csv   → CSV export
GET  /api/benchmark/export/markdown → Markdown export
GET  /api/benchmark/export/json  → JSON export
GET  /api/benchmark/export/html  → styled HTML export (Velocity template)
GET  /actuator/health            → H2 + disk + ping health
GET  /actuator/info              → app name, version, description
```

## Key Constraints

- **NO `@EnableBatchProcessing`** — Spring Boot 3.x auto-configures; adding it disables auto-config
- **Swagger enabled in `dev` profile only** — default profile has springdoc disabled
- **All parser formatters are annotation-based** — NO XML mapping files anywhere (except Camel BeanIO which uses XML stream mapping)
- **BeanIO FieldBuilder.at() is 0-based** — confirmed from library source; annotation @Field(at=X) may differ
- **Output files** written to `/output/` directory (gitignored)
- **Generated files are reproducible**: same domain data + params → same output
- **Test coverage**: JaCoCo enforced at 40% minimum (mvn verify)
- **Clear Database** button (Generate Data view) calls `DELETE /api/domain/reset`; it is a two-click confirm
  (`.btn-d.armed`, 5 s timeout) rather than `window.confirm()`, so it never blocks headless browser automation
- **Frontend** is a single vanilla HTML/CSS/JS file (`src/main/resources/static/index.html`); edit directly — no Node.js, no npm, no build step required
- **Run All result rows** (`.ra-row`) are fixed-width flex columns: `.ra-row > * { flex: none }` stops any cell from
  pushing its neighbours, `.ra-lib` is 200px (sized for `SPRING_BATCH_FIXFORMAT4J`, the longest `Library` value at
  24 chars), and the file name keeps `flex: 1 1 auto; min-width: 0` so it ellipsises. **Adding a longer `Library`
  enum value means widening `.ra-lib`** or the duration and file-name columns fall out of alignment
- **No Maven profiles needed** — `mvn clean install` and `mvn spring-boot:run` work with no flags; only the `benchmark` profile exists (JMH)
- **Line endings**: `.gitattributes` enforces LF for all source files; `.ps1`/`.bat` keep CRLF; `.ps1` files must use **ASCII-only** characters (no Unicode box-drawing) — PowerShell 5.x on Windows reads scripts as the system code page and misinterprets multibyte UTF-8
- **Actuator health includes version**: `VersionHealthIndicator` in `config/` exposes `version`, `artifact`, `description` as a `version` component alongside `db`, `diskSpace`, `ping`

## Docs & Slides

- `docs/benchmark-results.md` — library health (versions, release dates, repo activity), adoption/governance,
  supply-chain weight, bank-suitability matrix, JMH throughput and batch-pipeline throughput.
  **Facts verified 2026-07-27** from Maven Central `maven-metadata.xml` + artifact `Last-Modified`,
  GitHub REST API, deps.dev. Re-verify dates/stars before reusing them
- **Maven Central publishes no public download counts** — never quote download numbers; use dependents
  (deps.dev, pinned version only), stars and release cadence instead
- `docs/slides/banking-parser-comparison.md` — Marp deck (22 slides); `banking-parser-platform.pptx` — PowerPoint
- The PPTX is **generated**: edit `tools/python/generate_pptx.py`, then `python3 tools/python/generate_pptx.py`.
  Never hand-edit the `.pptx`. `TOTAL` in that script must match the number of `slide_*()` calls in `main()`
- Keep the Marp deck, the generator and `docs/slides/google-slides-export.md` slide list in sync

## Testing Strategy

| Category | Test Class | What it tests |
|---|---|---|
| Unit | `DomainDataGeneratorTest` | Mock repos, counts |
| Unit | `CodaRecordTest` | toFixedWidth/fromFixedWidth, 128-char lines |
| Integration | `StrategyResolverTest` | All 18 strategies resolve by key |
| Integration | `CodaStrategyTest` | Each CODA library: 128-char lines, header/trailer |
| Integration | `SwiftStrategyTest` | All 9 SWIFT libraries: MT940 tag assertions |
| Integration | `SymmetryTest` | Round-trip: generate→parse preserves amount+type |
| Web | `DomainControllerTest` | MockMvc: POST /api/domain/generate, DELETE /api/domain/reset |
| Web | `BatchControllerTest` | MockMvc: POST /api/batch/generate, GET /api/batch/history |
| Integration | `ActuatorTest` | TestRestTemplate: /actuator/health, /actuator/info |
| Integration | `SwaggerAvailabilityTest` | TestRestTemplate (dev profile): Swagger UI + OpenAPI spec |
| Integration | `GoldenFileTest` | 128-char CODA lines, required record types and MT940 tags |
| Unit | `AnnotatedLayoutTest` | Annotation-derived layout matches hand-written Ranges, byte-identical output |
| Benchmark | `FileGenerationBenchmark` | JMH: throughput for all 18 strategies (run with -Pbenchmark) |

Run specific test: `mvn test -Dtest=SymmetryTest`

## Java Package Convention

All packages: `com.wtechitsolutions.*`

## Repository Structure

See `docs/PRD.md` §18 and `docs/specs/design-spec.md` for full specs.
