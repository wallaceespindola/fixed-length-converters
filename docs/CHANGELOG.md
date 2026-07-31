# Changelog

All notable changes to this project are documented here.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

---

## [Unreleased]

### Added
- **Files view** in the frontend — lists the generated banking files in `output/` (name, size, modified,
  newest first) with click-to-preview; a "View Generated Files" shortcut sits on the Batch Runner.
  Backed by `GET /api/batch/files` and `GET /api/batch/files/{name}`; file names are validated against
  `[A-Za-z0-9._-]+\.txt` and resolved paths must stay inside `output/` (path-traversal guard, verified
  with an encoded `../` probe returning 400)
- **Clear Database button** in the Generate Data view, backed by `DELETE /api/domain/reset` — deletes accounts,
  transactions, statements and stored benchmark metrics so a benchmark can start from an empty database, and
  reports the deleted counts. Two-click confirm (arms for 5 s) instead of a blocking `window.confirm()`.
  Spring Batch job-execution metadata is deliberately kept, so Batch History survives a reset
- **Diagrams view** — 7th nav tab in the frontend SPA rendering live Mermaid@11.15.0 diagrams:
  system architecture, component diagram, batch sequence, strategy class hierarchy,
  benchmark flow, database schema, and deployment topology
- **MEDIUM load profile** (`LoadProfile.MEDIUM`) — 100 accounts / 1 000 transactions / 50 statements,
  available via `?loadProfile=MEDIUM`; Generate Data view now shows three buttons (Low / Medium / High)
- `run.sh --skip-build` flag — skip Maven build and start from existing JAR
- Separate `backend-err.log` for Spring Boot stderr in `run.sh`
- `AGENTS.md` — project guidance file for Codex / OpenAI coding agents
- **Two new formatter strategies** — `SPRING_BATCH_FIXFORMAT4J` and `SPRING_BATCH_FIXEDLENGTH`
  (18 strategies total, 9 per file type). Both use Spring Batch's `FormatterLineAggregator` /
  `FixedLengthTokenizer`, but the column layout is derived by reflection from the model annotations
  (`@Field` of fixedformat4j, `@FixedField` of fixedlength) instead of hand-written `Range(1,1), Range(2,4), ...`
  slicing — one source of truth for read path, write path and column widths
- `AnnotatedLayout` — reflects annotated models into Spring Batch columns; validates offsets for
  gaps/overlaps and total record width (128 chars) at construction
- `AnnotatedSpringBatchFormatter` base class plus `SpringBatchFixedFormat4JFormatter` and
  `SpringBatchFixedLengthFormatter` beans; four new strategy classes (CODA + SWIFT per approach)
- `AnnotatedLayoutTest` — asserts both annotation-derived layouts match the hand-written ranges and
  produce byte-identical CODA output; total test count 118 → 149
- **Formatter analysis refresh** across README, `docs/benchmark-results.md` and both slide decks:
  library health (pinned vs latest version, release dates, repo activity), adoption and governance
  (stars, governance model, deps.dev dependents, license), supply-chain weight (jar sizes, transitive
  cost, CVE-2020-13936 note for Velocity), a bank-suitability matrix and a decision guide.
  All facts verified 2026-07-27 from Maven Central metadata, the GitHub REST API and deps.dev
- Measured throughput published for all 9 approaches: 36-benchmark JMH run (ops/s, CODA + SWIFT,
  read + write) plus end-to-end batch-pipeline records/second on the MEDIUM profile
- Slide deck grown from 15 to 22 slides — new slides for annotation-derived layouts, library health,
  adoption & governance, supply-chain weight, bank suitability, and both performance views
- README gained a Presentation section linking the Marp deck, the PPTX and the regeneration command
- Documentation sweep for the two new strategies: `AGENTS.md` regenerated from `CLAUDE.md`,
  `docs/architecture.md`, `docs/PRD.md` and `docs/specs/design-spec.md` updated to 9 approaches /
  18 strategies / 36 JMH methods, and the strategy, architecture and component diagrams
  (`.mmd` + `.puml` + the frontend Mermaid copy) now include the four new classes and two new formatters
- PRD library table replaced with verified versions (pinned + latest + release date + risk)
- Camel 4.20.0 → 4.21.0 references synced across docs and formatter Javadoc after Dependabot #29

### Changed
- `Library.SPRING_BATCH_FF4J` renamed to **`SPRING_BATCH_FIXFORMAT4J`**, matching the existing `FIXFORMAT4J`
  constant. Classes follow the codebase convention (`FixedFormat4J`): `SpringBatchFf4jFormatter` →
  `SpringBatchFixedFormat4JFormatter`, `CodaSpringBatchFf4jStrategy` → `CodaSpringBatchFixedFormat4JStrategy`,
  `SwiftSpringBatchFf4jStrategy` → `SwiftSpringBatchFixedFormat4JStrategy`; JMH fields and benchmark methods,
  the frontend dropdown/`LIBS`/chart colours, all diagrams and every doc follow. **Breaking for API callers**
  posting `{"library":"SPRING_BATCH_FF4J"}`
- Product name is now **Banking Fixed-Length File Benchmark Platform** — UI title bar, browser title, footer,
  OpenAPI title, actuator `info.app.description`, Makefile banner, README, PRD, design spec, architecture doc,
  CLAUDE.md/AGENTS.md and both slide decks
- `.ra-lib` note updated: `SPRING_BATCH_FIXFORMAT4J` (24 chars) is now the longest `Library` value; verified in
  a browser that the 200px column still keeps all 18 rows aligned
- **Load profiles rescaled** — LOW: 10 accounts / 100 txns / 5 statements (was 20/200/10);
  HIGH: 1 000 accounts / 10 000 txns / 500 statements (was 200/2 000/100)
- `run.sh` banner switched from `╔══╗` box to open `════` style; port-conflict now exits with error;
  process-died detection added to health-wait loop; summary links include UI, API, H2 DB, Health
- `kill.sh` uses `PID_FILE` variable; fuser fallback added; pattern match updated to `FixedLengthConvertersApplication`
- `DomainDataGeneratorTest` now uses `LoadProfile.LOW.*()` accessors instead of hardcoded counts
- `VlCodaRecord` now declares `align` explicitly on every field — `@FixedField` defaults to
  `Align.RIGHT` while CODA text fields are left-aligned, so the annotations, not the formatter code,
  are the authoritative layout
- `tools/python/report_generator.py` now recognises all 9 approaches (was 4) and defaults to
  `docs/jmh-report.md` — it no longer overwrites the hand-curated `docs/benchmark-results.md`

### Removed
- **Retired React frontend** (`src/main/frontend/` — 13 tracked files: Vite config, TSX views, API client).
  Nothing referenced it: no `frontend-maven-plugin`, no Makefile or CI target, no script. The UI has been
  the vanilla `src/main/resources/static/index.html` since the frontend refactor. Also drops the
  now-obsolete `src/main/frontend/*` entries from `.gitignore` (and 439 MB of local `node_modules`)
- `BeanIoSwiftRecord` — unreferenced model class; the BeanIO SWIFT path uses `SwiftMtRecord` like every
  other SWIFT formatter
- `org.apache.velocity.tools:velocity-tools-generic` dependency and its version property — no import of
  `org.apache.velocity.tools` anywhere in main or test code; the HTML benchmark report renders through
  `velocity-engine-core` alone (verified live: `GET /api/benchmark/export/html` still returns a rendered report)
- Stray `.DS_Store` files across the working tree

### Fixed
- **`mvn test -Pbenchmark` ran zero benchmarks.** The `benchmark` profile added an `<includes>` for
  `**/*Benchmark.java`, but the base surefire config's `<excludes>` for the same pattern still applied
  (profile plugin config merges with the base), so the CI Benchmark job completed in ~37 s, ran nothing
  and uploaded no `jmh-result.json`. The profile now overrides the excludes; a full run takes ~5.5 min
  and produces all 36 results
- Benchmark numbers republished as the **median of 3 runs**. A run taken while macOS background indexing
  saturated the CPU reported up to 75 % lower throughput on the allocation-heavy CODA paths; the
  fixedformat4j CODA lead is ~1.3× write / ~1.5× read (was quoted as 1.8× / 1.7× from a single run)
- Stale `-Pskip-frontend` flag removed from the `FileGenerationBenchmark` javadoc — that profile no longer exists
- **Frontend Component Diagram showed 7 strategies and 7 parser wrappers** — the Mermaid copy embedded in
  `index.html` had not been updated when the two hybrids landed. Now `9x CodaStrategy` / `9x SwiftStrategy`
  with `SpringBatch+FixedFormat4J` and `SpringBatch+FixedLength` wired into the parser layer; all 7 diagrams
  re-checked in a browser (7 SVGs, no Mermaid errors)
- Diagram labels spell the approach out — `SB+FF4J` / `SpringBatch+FF4J` are now `SpringBatch+FixedFormat4J`
  in the deck, the PPTX generator and the frontend system-architecture diagram
- **Run All column misalignment** — `SPRING_BATCH_FIXEDLENGTH` (23 chars) overflowed the 126px library column and
  shifted the duration and file name of those rows. Columns are now `flex: none` with `.ra-lib` at 200px; verified
  in a browser across all 18 rows (one distinct x-offset per column)
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
