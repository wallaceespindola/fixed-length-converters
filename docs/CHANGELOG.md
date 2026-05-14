# Changelog

All notable changes to this project are documented here.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

---

## [Unreleased]

### Changed
- Added CODA (Febelfin) and SWIFT MT940 shields.io badges to README header
- Enhanced README architecture diagram with per-layer color theming (Frontend / API / Batch / Strategy / Parsers / Storage) and labeled data-flow arrows

### Added
- 3 additional parser formatter wrappers: `CamelBeanIOFormatter`, `VelocityFormatter`, `SpringBatchFormatter`
- 6 additional strategy implementations: `CodaCamelBeanIOStrategy`, `CodaVelocityStrategy`, `CodaSpringBatchStrategy`, `SwiftCamelBeanIOStrategy`, `SwiftVelocityStrategy`, `SwiftSpringBatchStrategy`
- Total strategies: 14 (7 libraries × 2 file types)
- 28 JMH `@Benchmark` methods (generate + parse for all 14 strategies)
- New endpoint: `GET /api/benchmark/export/html` — Velocity-driven HTML benchmark report
- `Library` enum expanded to: `BEANIO, FIXFORMAT4J, FIXEDLENGTH, BINDY, CAMEL_BEANIO, VELOCITY, SPRING_BATCH`
- Added LOW/HIGH load profile for `POST /api/domain/generate` (LOW: 20 accounts/200 txns/10 statements — default; HIGH: 200 accounts/2 000 txns/100 statements); implemented via `LoadProfile` enum in `com.wtechitsolutions.domain`
- "Run All Combinations" button on Batch Runner fires all 14 fileType × library combinations sequentially with live per-row progress
- Library Summary cards and both bar charts on the Benchmark Dashboard auto-sort by avg throughput (best to worst) on every refresh
- Pre-built frontend bundle committed to `src/main/resources/static/` — `mvn spring-boot:run -Pskip-frontend` serves the latest UI immediately without a frontend rebuild

### Fixed
- Standardized SWIFT inter-message separator to `---` across all 7 formatters (was `###` for Bindy, `===` for FixedLength)
- Fixed CODA Bindy trailer/description text alignment — `BindyCodaRecord` text fields now explicitly `align="L"` so trim+repad is left-aligned (Camel Bindy defaults to right-align, which pushed TOTAL to the end of the description field)
- Refactored `SpringBatchFormatter` to use `LineAggregator` + `FixedLengthTokenizer` + `FieldSetMapper` directly per record, removing the `FlatFileItemWriter`/`FlatFileItemReader` wrappers that caused transactional buffering issues (empty output files) when invoked inside an outer Spring Batch chunk

---

## [1.0.0-SNAPSHOT] — 2026-04-30

### Added

- Spring Boot 3.4.5 + Spring Batch 5.x project scaffold
- Domain model: `Account`, `Transaction`, `BankingStatement`, `BenchmarkMetrics`
- `DomainDataGenerator`: generates 20 accounts, 200 transactions, 10 statements per call
- 4 annotation-based parser formatter wrappers (no XML):
  - `BeanIOFormatter` — BeanIO 3.2.1 with StreamBuilder API
  - `FixedFormat4JFormatter` — fixedformat4j 1.7.0 with `@Record`/`@Field`
  - `FixedLengthFormatter` — fixedlength 0.15 with `@FixedLine`/`@FixedField`
  - `BindyFormatter` — Apache Camel Bindy 4.20.0 with `@FixedLengthRecord`/`@DataField`
- `FileGenerationStrategy` interface and `StrategyResolver`
- 8 initial strategy implementations: `Coda/Swift × BeanIO/FixedFormat4J/FixedLength/Bindy`
- Abstract base classes: `AbstractCodaStrategy`, `AbstractSwiftStrategy`
- Spring Batch pipeline: `DomainEntityItemReader` → `FileGenerationItemProcessor` → `FileOutputItemWriter`
- `BatchMetricsListener` and `ChunkTimingListener`
- REST API: `/api/domain/generate`, `/api/batch/generate`, `/api/batch/history`, `/api/benchmark/results`
- `BenchmarkService` with CSV, JSON, Markdown export
- `GlobalExceptionHandler` with RFC 9457 `ProblemDetail` responses
- Spring Actuator: `/actuator/health`, `/actuator/info`
- OpenAPI / Swagger UI (dev profile only)
- React 18 + Vite + MUI frontend with 5 views
- JaCoCo coverage enforcement (>40% instruction coverage)
- GitHub Actions: build, test, benchmark, CodeQL, release
- Dependabot for Maven and GitHub Actions
- Architecture diagrams: PlantUML + Mermaid formats
- Example banking files: CODA and SWIFT MT940
- Python benchmark aggregation and report generation tools
- `Makefile` with `build`, `run`, `test`, `benchmark`, `clean`, `lint`, `docs`, `help`
