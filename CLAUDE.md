# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Banking Fixed-Length File Generator & Parser Validation Platform** — enterprise-grade experimentation platform for generating, parsing, and benchmarking CODA and SWIFT MT banking files using 4 Java fixed-length parser libraries via the Strategy Pattern and Spring Batch.

**PRD:** `PRD.md` (v3.0, authoritative) | **Design spec:** `docs/specs/design-spec.md` | **Implementation plan:** `docs/implementation-plan.md`

## Implementation Status: COMPLETE

All 76 tests pass. Application starts and runs end-to-end.

## Technical Stack

| Area | Technology |
|---|---|
| Language | Java 21 (target: 25) |
| Backend | Spring Boot 3.4.5 |
| Batch | Spring Batch 5.x |
| Monitoring | Spring Actuator |
| Database | H2 In-Memory |
| API Docs | OpenAPI V3 + Swagger (dev profile only) |
| Build | Maven 3.9.x |
| Testing | JUnit 5 + Mockito, 75 tests |
| Libraries | BeanIO 2.1.0, fixedformat4j 1.7.0, fixedlength 0.15, Camel Bindy 4.20.0 |
| CI/CD | GitHub Actions (build, test, benchmark, codeql, release) |

## Build & Run Commands

```bash
# Quick build (no tests, no frontend)
mvn clean package -DskipTests -Pskip-frontend

# Run in dev mode (Swagger UI enabled at /swagger-ui.html)
mvn spring-boot:run -Pskip-frontend -Dspring-boot.run.profiles=dev

# All tests
mvn test -Pskip-frontend

# Integration tests with coverage
mvn verify -Pskip-frontend

# Run single test class
mvn test -Pskip-frontend -Dtest=StrategyResolverTest

# JMH benchmarks
mvn test -Pskip-frontend -Pbenchmark

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
├── benchmark/         BenchmarkService (CSV/JSON/Markdown export)
├── config/            BatchConfig (no @EnableBatchProcessing!), OpenApiConfig, WebConfig
├── domain/            JPA entities (Account, Transaction, BankingStatement, BenchmarkMetrics)
│                       Repositories, DomainDataGenerator, enums (FileType, Library, TransactionType)
├── parser/            4 formatter wrappers (all annotation-based, no XML):
│   │                   BeanIOFormatter, FixedFormat4JFormatter, FixedLengthFormatter, BindyFormatter
│   └── model/         Annotated model classes per library:
│                       CodaRecord (shared model with toFixedWidth/fromFixedWidth)
│                       BeanIoCodaRecord, Ff4jCodaRecord, VlCodaRecord, BindyCodaRecord
│                       SwiftMtRecord, BeanIoSwiftRecord
└── strategy/          FileGenerationStrategy interface, StrategyResolver, 8 implementations:
                        AbstractCodaStrategy, AbstractSwiftStrategy (base classes)
                        CodaBeanIOStrategy, CodaFixedFormat4JStrategy, CodaFixedLengthStrategy, CodaBindyStrategy
                        SwiftBeanIOStrategy, SwiftFixedFormat4JStrategy, SwiftFixedLengthStrategy, SwiftBindyStrategy
```

### Core Flow

1. `POST /api/domain/generate` → `DomainDataGenerator` seeds H2 (20 accounts, 200 transactions, 10 statements)
2. `POST /api/batch/generate` → `BatchJobService.launch(fileType, library)` → Spring Batch job
3. Spring Batch: `DomainEntityItemReader` (H2) → `FileGenerationItemProcessor` (StrategyResolver) → `FileOutputItemWriter` (writes to `/output/`)
4. `BatchMetricsListener.afterJob()` → saves `BenchmarkMetrics` row

### Strategy Pattern

`StrategyResolver` maps all `FileGenerationStrategy` beans by `strategyKey()` = `"FILETYPE_LIBRARY"`. Resolution is O(1) map lookup, no if/switch chains.

### CODA Format Notes

- Each record: **exactly 128 characters** per Febelfin spec
- Record types: 0=header, 1=movement, 2=detail, 8=trailer, 9=end
- Amounts: **plain integer** (no decimal places) stored as left-zero-padded 16-char string
- `padAmount()` uses `setScale(0, ROUND_HALF_UP)` to strip decimal scale from H2 BigDecimal values
- BeanIO uses `FieldBuilder.at()` with **0-based** character positions (NOT 1-based)

### SWIFT Format Notes

- All 4 strategies serialise as MT940 tag format (`:20:`, `:25:`, `:28C:`, `:60F:`, `:61:`, `:86:`, `:62F:`) with `---` record delimiters
- BeanIO previously used CSV format; fixed to be consistent with the other strategies

## REST API Endpoints

```
POST /api/domain/generate        → generate + persist 20 accounts, 200 transactions
POST /api/batch/generate         → trigger job; body: {"fileType":"CODA","library":"BEANIO"}
GET  /api/batch/history          → last 50 job executions
GET  /api/benchmark/results      → all benchmark metrics
GET  /api/benchmark/export/csv   → CSV export
GET  /api/benchmark/export/markdown → Markdown export
GET  /api/benchmark/export/json  → JSON export
GET  /actuator/health            → H2 + disk + ping health
GET  /actuator/info              → app name, version, description
```

## Key Constraints

- **NO `@EnableBatchProcessing`** — Spring Boot 3.x auto-configures; adding it disables auto-config
- **Swagger enabled in `dev` profile only** — default profile has springdoc disabled
- **All parser formatters are annotation-based** — NO XML mapping files anywhere
- **BeanIO FieldBuilder.at() is 0-based** — confirmed from library source; annotation @Field(at=X) may differ
- **Output files** written to `/output/` directory (gitignored)
- **Generated files are reproducible**: same domain data + params → same output
- **Test coverage**: JaCoCo enforced at 40% minimum (mvn verify)

## Testing Strategy

| Category | Test Class | What it tests |
|---|---|---|
| Unit | `DomainDataGeneratorTest` | Mock repos, counts |
| Unit | `CodaRecordTest` | toFixedWidth/fromFixedWidth, 128-char lines |
| Integration | `StrategyResolverTest` | All 8 strategies resolve by key |
| Integration | `CodaStrategyTest` | Each CODA library: 128-char lines, header/trailer |
| Integration | `SwiftStrategyTest` | All 4 SWIFT libraries: MT940 tag assertions (12 tests) |
| Integration | `SymmetryTest` | Round-trip: generate→parse preserves amount+type |
| Web | `DomainControllerTest` | MockMvc: POST /api/domain/generate |
| Web | `BatchControllerTest` | MockMvc: POST /api/batch/generate, GET /api/batch/history |
| Integration | `ActuatorTest` | TestRestTemplate: /actuator/health, /actuator/info |
| Integration | `SwaggerAvailabilityTest` | TestRestTemplate (dev profile): Swagger UI + OpenAPI spec |

| Integration | `GoldenFileTest` | 128-char CODA lines, required record types and MT940 tags |
| Benchmark | `FileGenerationBenchmark` | JMH: throughput for all 8 strategies (run with -Pbenchmark) |

Run specific test: `mvn test -Pskip-frontend -Dtest=SymmetryTest`

## Java Package Convention

All packages: `com.wtechitsolutions.*`

## Repository Structure

See `PRD.md` §18 and `docs/specs/design-spec.md` for full specs.
