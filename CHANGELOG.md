# Changelog

All notable changes to this project are documented here.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

---

## [1.0.0-SNAPSHOT] — 2026-04-30

### Added

- Spring Boot 3.4.5 + Spring Batch 5.x project scaffold
- Domain model: `Account`, `Transaction`, `BankingStatement`, `BenchmarkMetrics`
- `DomainDataGenerator`: generates 20 accounts, 200 transactions, 10 statements per call
- 4 annotation-based parser formatter wrappers (no XML):
  - `BeanIOFormatter` — BeanIO 2.1.0 with StreamBuilder API
  - `FixedFormat4JFormatter` — fixedformat4j 1.7.0 with `@Record`/`@Field`
  - `FixedLengthFormatter` — fixedlength 0.15 with `@FixedLine`/`@FixedField`
  - `BindyFormatter` — Apache Camel Bindy 4.20.0 with `@FixedLengthRecord`/`@DataField`
- `FileGenerationStrategy` interface and `StrategyResolver`
- 8 strategy implementations: `Coda/Swift × BeanIO/FixedFormat4J/FixedLength/Bindy`
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
