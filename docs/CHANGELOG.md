# Changelog

All notable changes to this project are documented here.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

---

## [Unreleased]

### Added
- **Diagrams view** — 7th nav tab in the frontend SPA rendering live Mermaid@11.15.0 diagrams:
  system architecture, component diagram, batch sequence, strategy class hierarchy,
  benchmark flow, database schema, and deployment topology
- **MEDIUM load profile** (`LoadProfile.MEDIUM`) — 100 accounts / 1 000 transactions / 50 statements,
  available via `?loadProfile=MEDIUM`; Generate Data view now shows three buttons (Low / Medium / High)
- `run.sh --skip-build` flag — skip Maven build and start from existing JAR
- Separate `backend-err.log` for Spring Boot stderr in `run.sh`
- `AGENTS.md` — project guidance file for Codex / OpenAI coding agents

### Changed
- **Load profiles rescaled** — LOW: 10 accounts / 100 txns / 5 statements (was 20/200/10);
  HIGH: 1 000 accounts / 10 000 txns / 500 statements (was 200/2 000/100)
- `run.sh` banner switched from `╔══╗` box to open `════` style; port-conflict now exits with error;
  process-died detection added to health-wait loop; summary links include UI, API, H2 DB, Health
- `kill.sh` uses `PID_FILE` variable; fuser fallback added; pattern match updated to `FixedLengthConvertersApplication`
- `DomainDataGeneratorTest` now uses `LoadProfile.LOW.*()` accessors instead of hardcoded counts

### Fixed
- `DomainDataGeneratorTest` test failure caused by hardcoded account/transaction counts that no longer
  matched the updated `LoadProfile.LOW` values

---

### Changed (prior session)
- Upgraded `velocity-engine-core` from 2.3 to 2.4.1 (backward-compatible; includes bug fixes and performance improvements)
- Added CODA (Febelfin) and SWIFT MT940 shields.io badges to README header
- Enhanced README architecture diagram with per-layer color theming (Frontend / API / Batch / Strategy / Parsers / Storage) and labeled data-flow arrows
- Replaced React 18 + Vite + MUI frontend with a self-contained vanilla HTML/CSS/JS single-page UI (`src/main/resources/static/index.html`); Chart.js (CDN) used for benchmark charts — no Node.js, npm, or build step required
- Removed `frontend-maven-plugin` and `skip-frontend` Maven profile; `mvn clean install` and `mvn spring-boot:run` now work with no flags or profiles
- Simplified all start/stop scripts (`run.*`, `kill.*`) and `Makefile` to remove npm/Node/Vite references; only Java and Maven are required
- Updated all documentation to reflect the simplified build and frontend architecture

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
- Frontend replaced with a single vanilla HTML/CSS/JS file (`src/main/resources/static/index.html`) — no Node.js, npm, or build step required; `mvn spring-boot:run` serves the UI directly

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
- Vanilla HTML/CSS/JS single-page UI with 5 views (Dashboard, Data Generator, Batch Runner, History, Benchmark)
- JaCoCo coverage enforcement (>40% instruction coverage)
- GitHub Actions: build, test, benchmark, CodeQL, release
- Dependabot for Maven and GitHub Actions
- Architecture diagrams: PlantUML + Mermaid formats
- Example banking files: CODA and SWIFT MT940
- Python benchmark aggregation and report generation tools
- `Makefile` with `build`, `run`, `test`, `benchmark`, `clean`, `lint`, `docs`, `help`
