# Banking Fixed-Length File Generator & Parser Validation Platform

[![Build](https://github.com/wallaceespindola/fixed-length-converters/actions/workflows/build.yml/badge.svg)](https://github.com/wallaceespindola/fixed-length-converters/actions/workflows/build.yml)
[![Test](https://github.com/wallaceespindola/fixed-length-converters/actions/workflows/test.yml/badge.svg)](https://github.com/wallaceespindola/fixed-length-converters/actions/workflows/test.yml)
[![CodeQL](https://github.com/wallaceespindola/fixed-length-converters/actions/workflows/codeql.yml/badge.svg)](https://github.com/wallaceespindola/fixed-length-converters/actions/workflows/codeql.yml)
[![Java](https://img.shields.io/badge/Java-21-orange)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.5-brightgreen)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

Enterprise-grade banking file experimentation and benchmarking platform. Generates, parses, and benchmarks **CODA** and
**SWIFT MT940** fixed-length banking files using **4 Java formatter libraries**, all orchestrated through **Spring Batch
** and the **Strategy Pattern**.

---

## Overview

This platform is a technical laboratory for evaluating Java fixed-length parser frameworks across correctness,
performance, and Spring Batch compatibility. Engineers can:

- Generate realistic banking transaction datasets (20 accounts, 200 transactions per call)
- Trigger Spring Batch jobs to produce CODA or SWIFT MT files via any of 4 libraries
- Compare library outputs side-by-side through benchmark dashboards
- Export benchmark results as CSV, JSON, or Markdown

---

## Architecture

```mermaid
graph TB
    subgraph Frontend["React SPA (MUI + Recharts)"]
        UI[Dashboard / Batch Runner / History / Benchmark]
    end
    subgraph API["REST API Layer"]
        DC[POST /api/domain/generate]
        BC[POST /api/batch/generate]
        BNC[GET /api/benchmark/results]
    end
    subgraph Batch["Spring Batch Pipeline"]
        READER[DomainEntityItemReader]
        PROC[FileGenerationItemProcessor]
        WRITER[FileOutputItemWriter]
    end
    subgraph Strategy["Strategy Pattern × 8"]
        SR[StrategyResolver]
        C4[4 CODA Strategies]
        S4[4 SWIFT Strategies]
    end
    subgraph Parsers["Parser Library Wrappers"]
        BIO[BeanIO 2.1.0]
        FF4J[fixedformat4j 1.7.0]
        VL[fixedlength 0.15]
        BINDY[Camel Bindy 4.20.0]
    end
    Frontend --> API
    DC --> H2[(H2 DB)]
    BC --> Batch
    Batch --> Strategy
    Strategy --> Parsers
    WRITER --> output[(output/)]
```

### Batch Pipeline

```
ItemReader (H2) → ItemProcessor (StrategyResolver) → ItemWriter (output/)
```

Each Spring Batch job is parameterised by `fileType` (CODA/SWIFT) and `library` (
BEANIO/FIXEDFORMAT4J/FIXEDLENGTH/BINDY). Jobs are **restartable** from the last checkpoint.

### Strategy Pattern

8 strategy implementations — one per `FileType × Library` combination — all behind a single `FileGenerationStrategy`
interface:

| Class                        | Format      | Library            |
|------------------------------|-------------|--------------------|
| `CodaBeanIOStrategy`         | CODA        | BeanIO             |
| `CodaFixedFormat4JStrategy`  | CODA        | fixedformat4j      |
| `CodaFixedLengthStrategy`    | CODA        | fixedlength        |
| `CodaBindyStrategy`          | CODA        | Apache Camel Bindy |
| `SwiftBeanIOStrategy`        | SWIFT MT940 | BeanIO             |
| `SwiftFixedFormat4JStrategy` | SWIFT MT940 | fixedformat4j      |
| `SwiftFixedLengthStrategy`   | SWIFT MT940 | fixedlength        |
| `SwiftBindyStrategy`         | SWIFT MT940 | Apache Camel Bindy |

`StrategyResolver` selects the correct implementation at runtime via Spring's dependency injection — no `if`/`switch`
chains.

---

## Supported Banking Standards

| Standard        | Description                                                                   | Authority |
|-----------------|-------------------------------------------------------------------------------|-----------|
| **CODA**        | Belgian/European banking statement format — 128-character fixed-width records | Febelfin  |
| **SWIFT MT940** | International banking messaging format — tag-based (`field:value`)            | SWIFT     |

---

## Formatter Library Comparison

| Library                | Version | Grammar Support | Annotation Quality | Spring Batch Fit | Risk   |
|------------------------|---------|-----------------|--------------------|------------------|--------|
| **BeanIO**             | 2.1.0   | Excellent       | Good               | Good             | Low    |
| **fixedformat4j**      | 1.7.0   | Limited         | Excellent          | Excellent        | Low    |
| **fixedlength**        | 0.15    | Limited         | Good               | Good             | Medium |
| **Apache Camel Bindy** | 4.20.0  | Limited         | Good               | Medium           | Medium |

### Strategic Recommendations

| Scenario                          | Recommended Library |
|-----------------------------------|---------------------|
| Maximum CODA grammar correctness  | BeanIO              |
| Simplicity and modern annotations | fixedformat4j       |
| Existing Apache Camel ecosystem   | Apache Camel Bindy  |
| Lightweight experimentation       | fixedlength         |

---

## Some screenshots

### Benchmark

![Fixed lenght benchmark.png](resources/images/Fixed%20lenght%20benchmark.png)

### History

![Fixed lenght history.png](resources/images/Fixed%20lenght%20history.png)

---

## Quick Start

### Prerequisites

- Java 21+ (tested with Amazon Corretto 21)
- Maven 3.9+
- Node.js 22+ (for frontend build)
- Python 3.12+ _(optional — benchmark aggregation tools only)_
- `make` _(optional — simplifies commands; see install instructions below)_

#### Installing `make`

| Platform | Command |
|----------|---------|
| **macOS** | `brew install make` _(already available via Xcode Command Line Tools: `xcode-select --install`)_ |
| **Ubuntu / Debian** | `sudo apt-get install -y make` |
| **Fedora / RHEL** | `sudo dnf install -y make` |
| **Windows** | Install [Git for Windows](https://gitforwindows.org/) (includes `make` in Git Bash), or via [Chocolatey](https://chocolatey.org/): `choco install make`, or via [Scoop](https://scoop.sh/): `scoop install make` |

Verify with: `make --version`

### Build and Run

Each command is shown with `# with make` and `# direct` alternatives.

```bash
# Compile and package — fastest (skips tests and frontend)
# with make
make build
# direct
mvn clean package -DskipTests -Pskip-frontend

# Compile and package including frontend build
# with make
make build-full
# direct
mvn clean package -DskipTests

# Start in dev mode — Swagger UI enabled at http://localhost:8080/swagger-ui.html
# with make
make run
# direct
mvn spring-boot:run -Pskip-frontend -Dspring-boot.run.profiles=dev

# Start without dev profile (no Swagger)
# with make
make run-prod
# direct
mvn spring-boot:run -Pskip-frontend

# Run all tests (unit + integration) with JaCoCo coverage
# with make
make test
# direct
mvn verify -Pskip-frontend

# Run unit tests only
# with make
make test-unit
# direct
mvn test -Pskip-frontend

# Run JMH benchmark suite
# with make
make benchmark
# direct
mvn test -Pbenchmark -Pskip-frontend

# Remove build artifacts and generated output files
# with make
make clean
# direct
mvn clean

# Kill any running Spring Boot / Vite processes (free ports 8080, 5173) — make only
make kill

# Run static analysis (compiler warnings) — make only
make lint

# Generate JaCoCo HTML coverage report → target/site/jacoco/index.html — make only
make docs

# List all available make targets with descriptions — make only
make help
```

Application starts at **http://localhost:8080**  
Swagger UI (dev profile only): **http://localhost:8080/swagger-ui.html**

### Python Benchmark Tools _(optional)_

After running benchmarks (`make benchmark` or `mvn test -Pbenchmark`), use the tools in `tools/python/` to analyse results:

```bash
# Aggregate JMH results and print statistics table (mean, stdev, min per benchmark)
python tools/python/benchmark_aggregator.py
# or with explicit path:
python tools/python/benchmark_aggregator.py target/jmh-result.json

# Generate a Markdown + HTML report from JMH results
python tools/python/report_generator.py
# or with explicit paths:
python tools/python/report_generator.py target/jmh-result.json docs/benchmark-results.md
```

---

## REST API

| Method | Endpoint                         | Description                                    |
|--------|----------------------------------|------------------------------------------------|
| `POST` | `/api/domain/generate`           | Generate 20 accounts + 200 transactions in H2  |
| `POST` | `/api/batch/generate`            | Trigger Spring Batch job `{fileType, library}` |
| `GET`  | `/api/batch/history`             | Last 50 batch job executions                   |
| `GET`  | `/api/benchmark/results`         | All benchmark metrics                          |
| `GET`  | `/api/benchmark/export/csv`      | Export as CSV                                  |
| `GET`  | `/api/benchmark/export/markdown` | Export as Markdown                             |
| `GET`  | `/api/benchmark/export/json`     | Export as JSON                                 |
| `GET`  | `/actuator/health`               | Application health                             |
| `GET`  | `/actuator/info`                 | Application metadata                           |

### Example: Generate Data and Run Batch

```bash
# Step 1: Generate domain data
curl -s -X POST http://localhost:8080/api/domain/generate | jq .

# Step 2: Generate CODA file using BeanIO
curl -s -X POST http://localhost:8080/api/batch/generate \
  -H "Content-Type: application/json" \
  -d '{"fileType":"CODA","library":"BEANIO"}' | jq .

# Step 3: View batch history
curl -s http://localhost:8080/api/batch/history | jq .

# Step 4: Export benchmark results
curl -s http://localhost:8080/api/benchmark/export/csv -o benchmark.csv
```

---

## Swagger UI

Swagger UI is available **only in the `dev` profile**:

```
http://localhost:8080/swagger-ui.html
http://localhost:8080/v3/api-docs
```

```bash
# with make
make run
# direct
mvn spring-boot:run -Pskip-frontend -Dspring-boot.run.profiles=dev
```

---

## Spring Actuator

```bash
# Health check (public)
curl http://localhost:8080/actuator/health

# Application info (public)
curl http://localhost:8080/actuator/info
```

---

## Testing Strategy

| Category            | Test Class                                    | Tools              |
|---------------------|-----------------------------------------------|--------------------|
| Unit                | `DomainDataGeneratorTest`, `CodaRecordTest`   | JUnit 5 + Mockito  |
| Strategy resolution | `StrategyResolverTest`                        | `@SpringBootTest`  |
| CODA correctness    | `CodaStrategyTest`                            | `@SpringBootTest`  |
| SWIFT correctness   | `SwiftStrategyTest`                           | `@SpringBootTest`  |
| Round-trip symmetry | `SymmetryTest`                                | `@SpringBootTest`  |
| REST API            | `DomainControllerTest`, `BatchControllerTest` | MockMvc            |
| Actuator            | `ActuatorTest`                                | `TestRestTemplate` |
| Swagger             | `SwaggerAvailabilityTest`                     | `TestRestTemplate` |

```bash
# All tests (unit + integration) with JaCoCo coverage
# with make
make test
# direct
mvn verify -Pskip-frontend

# Run a specific test class
mvn test -Pskip-frontend -Dtest=StrategyResolverTest

# Run symmetry tests only
mvn test -Pskip-frontend -Dtest=SymmetryTest

# Run API tests only
mvn test -Pskip-frontend -Dtest="DomainControllerTest,BatchControllerTest"
```

---

## Frontend

The React 18 + Vite + MUI frontend provides:

- **Dashboard** — health status, actuator info, quick-action buttons
- **Data Generator** — trigger domain data generation, display results
- **Batch Runner** — select FileType + Library, submit, preview generated file
- **Batch History** — sortable/filterable table of all job executions
- **Benchmark Dashboard** — line charts, bar charts, throughput comparison, library pairwise comparison, CSV/JSON/MD
  export

Build the frontend: `mvn generate-resources` (handled by `frontend-maven-plugin`)

---

## Repository Structure

```
fixed-length-converters/
├── pom.xml                     Maven build
├── Makefile                    Developer commands
├── src/main/java/com/wtechitsolutions/
│   ├── api/                    REST controllers + DTO records
│   ├── batch/                  Spring Batch reader/processor/writer/listeners
│   ├── benchmark/              BenchmarkService (CSV/JSON/MD export)
│   ├── config/                 Spring, Batch, OpenAPI, Web config
│   ├── domain/                 JPA entities + repositories + DomainDataGenerator
│   ├── parser/                 4 formatter wrappers + annotated model classes
│   └── strategy/               FileGenerationStrategy + 8 implementations
├── src/main/frontend/          React 18 + Vite + MUI source
├── docs/
│   ├── examples/coda/          Valid, malformed, edge-case CODA files
│   ├── examples/swift-mt/      Valid, malformed, edge-case SWIFT MT940 files
│   └── diagrams/               Architecture diagrams (.puml + .mmd)
├── tools/python/               Benchmark aggregation + report generation
├── output/                     Generated banking files (gitignored)
└── .github/workflows/          build, test, benchmark, codeql, release
```

---

## Links

- [Febelfin CODA Specification](https://www.febelfin.be/en/payments-standards/coda)
- [SWIFT MT940 Documentation](https://www.swift.com/standards/data-standards/mt)
- [BeanIO on Maven Central](https://mvnrepository.com/artifact/org.beanio/beanio)
- [fixedformat4j on Maven Central](https://mvnrepository.com/artifact/com.ancientprogramming.fixedformat4j/fixedformat4j)
- [fixedlength on Maven Central](https://mvnrepository.com/artifact/name.velikodniy.vitaliy/fixedlength)
- [Apache Camel Bindy](https://camel.apache.org/components/latest/dataformats/bindy-dataformat.html)

---

## Author

**Wallace Espindola**

- Email: [wallace.espindola@gmail.com](mailto:wallace.espindola@gmail.com)
- LinkedIn: [linkedin.com/in/wallaceespindola](https://www.linkedin.com/in/wallaceespindola/)
- GitHub: [github.com/wallaceespindola](https://github.com/wallaceespindola/)
