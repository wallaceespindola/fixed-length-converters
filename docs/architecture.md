# System Architecture

**Banking Fixed-Length File Generator & Parser Validation Platform**

---

## 1. System Overview

The platform is a single-module Spring Boot 3.4.5 application that generates, parses, and benchmarks CODA and SWIFT MT940 fixed-length banking files using seven distinct Java formatter libraries. It is designed as a technical laboratory for comparing library correctness, performance, and Spring Batch compatibility.

One command starts the full platform: `mvn spring-boot:run`.

---

## 2. Layered Architecture

```
┌─────────────────────────────────────────────────────────┐
│  React SPA (MUI + Recharts, served from JAR /static/)   │
├─────────────────────────────────────────────────────────┤
│  REST API Layer (Spring MVC controllers + DTO records)   │
├─────────────────────────────────────────────────────────┤
│  Spring Batch Pipeline (reader → processor → writer)    │
├─────────────────────────────────────────────────────────┤
│  Strategy Layer (14 FileGenerationStrategy impls)       │
├─────────────────────────────────────────────────────────┤
│  Parser Wrappers (7 formatter libraries; XML for Camel  │
│  BeanIO, .vm templates for Velocity)                    │
├─────────────────────────────────────────────────────────┤
│  Domain Layer (JPA entities + H2 in-memory DB)          │
└─────────────────────────────────────────────────────────┘
```

Cross-layer dependencies flow strictly downward. The `api` layer never reaches into `parser`; the `batch` layer delegates to `strategy`, never to `api`.

---

## 3. Package Structure

```
com.wtechitsolutions/
├── api/            REST controllers (DomainController, BatchController, BenchmarkController)
│   └── dto/        Java Record DTOs (immutable, no inheritance)
├── batch/          Spring Batch components (reader, processor, writer, listeners, service)
├── benchmark/      BenchmarkService (CSV/JSON/Markdown/HTML export)
├── config/         Spring configuration (BatchConfig, OpenApiConfig, WebConfig)
├── domain/         JPA entities, JPA repositories, DomainDataGenerator, enums
├── parser/         7 formatter wrappers + library-specific annotated model classes
│   └── model/      CodaRecord, SwiftMtRecord, and library-annotated variants
└── strategy/       FileGenerationStrategy interface, StrategyResolver, 14 implementations
```

---

## 4. Spring Batch Pipeline

Every file generation request follows the standard Spring Batch pipeline:

```
DomainEntityItemReader → FileGenerationItemProcessor → FileOutputItemWriter
```

- **Reader** (`@StepScope`): loads all `Transaction` rows from H2 into memory using `@BeforeStep` initialization; returns one at a time.
- **Processor** (`@StepScope`): resolves the correct `FileGenerationStrategy` via `StrategyResolver` on `@BeforeStep`; calls `strategy.generate([transaction], accounts)` per item.
- **Writer** (`@StepScope`): buffers chunk output in memory; on `@AfterStep` writes a single file to `output/` and stores content in the step `ExecutionContext`.

Chunk size: 100. All jobs are **restartable** — `saveState=true` is the default for `@StepScope` readers.

Job parameters uniquely identify each run: `fileType`, `library`, `operationId`, `runTimestamp`. This guarantees a new `JobInstance` per call, enabling restart from the last checkpoint if a run fails.

`BatchMetricsListener` (`JobExecutionListener`) persists a `BenchmarkMetrics` row after each job completion.

**Key constraint:** `@EnableBatchProcessing` is NOT used — Spring Boot 3.x auto-configures `JobRepository`, `JobLauncher`, and `JobExplorer` automatically.

---

## 5. Strategy Pattern

`FileGenerationStrategy` is the central interface:

```java
public interface FileGenerationStrategy {
    String generate(List<Transaction> transactions, List<Account> accounts);
    List<Transaction> parse(String fileContent);
    FileType getFileType();   // CODA or SWIFT
    Library getLibrary();     // BEANIO, FIXFORMAT4J, FIXEDLENGTH, BINDY, CAMEL_BEANIO, VELOCITY, SPRING_BATCH
    default String strategyKey() { return getFileType() + "_" + getLibrary(); }
}
```

`StrategyResolver` receives all 14 strategy beans via Spring injection and maps them into a `Map<String, FileGenerationStrategy>` keyed by `strategyKey()`. Resolution is O(1) — no `if`/`switch` chains anywhere.

Two abstract base classes share domain-mapping logic:
- `AbstractCodaStrategy` — builds `CodaRecord` list (header, movements, trailer), delegates format/parse to subclass
- `AbstractSwiftStrategy` — builds `SwiftMtRecord` list per transaction, delegates to subclass

---

## 6. Parser Library Wrappers

Seven wrappers in `com.wtechitsolutions.parser`:

| Wrapper | Library | Mechanism |
|---|---|---|
| `BeanIOFormatter` | BeanIO 3.2.1 | `StreamBuilder` + `FieldBuilder` (0-based positions); CSV for SWIFT |
| `FixedFormat4JFormatter` | fixedformat4j 1.7.0 | `@Record(length=128)` + `@Field(offset=X, length=Y)` on `Ff4jCodaRecord` |
| `FixedLengthFormatter` | fixedlength 0.15 | `@FixedLine(startsWith="")` + `@FixedField(offset=X, length=Y)` on `VlCodaRecord` |
| `BindyFormatter` | Camel Bindy 4.20.0 | `@FixedLengthRecord(length=128)` + `@DataField(pos=X, length=Y)` on `BindyCodaRecord` |
| `CamelBeanIOFormatter` | Apache Camel BeanIO 4.20.0 | Camel BeanIO DataFormat with XML stream mapping |
| `VelocityFormatter` | Apache Velocity 2.4 | `.vm` template files; `VelocityEngine` renders records to string |
| `SpringBatchFormatter` | Spring Batch 5.x native | `FlatFileItemWriter` with `BeanWrapperFieldExtractor` + `FormatterLineAggregator` |

**CODA amount encoding:** Amounts are stored as plain integers (scale stripped via `setScale(0, ROUND_HALF_UP)`) in a 16-character zero-padded field. BeanIO `FieldBuilder` positions are 0-based; XML would use 1-based (different convention).

---

## 7. Data Model

Four JPA entities mapped to H2 with `ddl-auto: create-drop`:

- **Account** — IBAN, bank code, currency, balance, holder name
- **Transaction** — FK to account, reference, amount, type (CREDIT/DEBIT), dates
- **BankingStatement** — FK to account, opening/closing balance, date, sequence number
- **BenchmarkMetrics** — job execution ID, library, file type, throughput, durations, rates

Spring Batch's own tables (`BATCH_JOB_EXECUTION`, `BATCH_STEP_EXECUTION`, etc.) are auto-created by `spring.batch.jdbc.initialize-schema: always`.

---

## 8. REST API

| Method | Path | Description |
|---|---|---|
| POST | `/api/domain/generate` | Generate 20 accounts + 200 transactions + 10 statements |
| POST | `/api/batch/generate` | Trigger batch job `{fileType, library}` |
| GET | `/api/batch/history` | Last 50 job executions |
| GET | `/api/benchmark/results` | All benchmark metrics |
| GET | `/api/benchmark/export/csv` | CSV export |
| GET | `/api/benchmark/export/markdown` | Markdown export |
| GET | `/api/benchmark/export/json` | JSON export |
| GET | `/api/benchmark/export/html` | HTML export (Velocity-rendered) |
| GET | `/actuator/health` | Health (H2 + disk + ping) |
| GET | `/actuator/info` | App name, version, build time |

All responses include an `Instant timestamp` field (ISO-8601). Error responses use RFC 9457 `ProblemDetail`. Swagger UI is available only in the `dev` Spring profile.

---

## 9. Security

- Actuator: only `/health` and `/info` are exposed (`management.endpoints.web.exposure.include: health,info`). All other endpoints (metrics, beans, env) are blocked.
- Swagger: `springdoc.swagger-ui.enabled: false` in default profile; enabled only via `application-dev.yml`.
- No secrets, API keys, or credentials in source.

---

## 10. Diagrams

All diagrams are in `docs/diagrams/` in both PlantUML (`.puml`) and Mermaid (`.mmd`) formats:

| Diagram | Files |
|---|---|
| Architecture overview | `architecture-overview.puml/.mmd` |
| Component relationships | `component-diagram.puml/.mmd` |
| Spring Batch sequence | `batch-sequence.puml/.mmd` |
| Strategy class hierarchy | `strategy-class-diagram.puml/.mmd` |
| Benchmark data flow | `benchmark-flow.puml/.mmd` |
| Deployment topology | `deployment-diagram.puml/.mmd` |
| Database schema | `database-diagram.mmd` |
