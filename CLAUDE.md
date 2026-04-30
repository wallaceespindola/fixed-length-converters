# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Banking Fixed-Length File Generator & Parser Validation Platform** — an enterprise-grade experimentation platform for generating, parsing, and benchmarking CODA and SWIFT MT banking files using multiple Java fixed-length parser libraries via the Strategy Pattern and Spring Batch.

PRDs: `PRD-1.md` (v1.3), `PRD-2.md` (v1.4, authoritative).

## Technical Stack

| Area | Technology |
|---|---|
| Language | Java 25 |
| Backend | Spring Boot 3.4.x |
| Batch | Spring Batch |
| Monitoring | Spring Actuator |
| Database | H2 In-Memory |
| API Docs | OpenAPI V3 + Swagger (dev only) |
| Build | Maven |
| Testing | JUnit 5 + JMH for benchmarks |
| CI/CD | GitHub Actions |

## Build & Run Commands

Once `pom.xml` and source exist, standard commands:

```bash
mvn clean install          # build + test
mvn spring-boot:run        # run application
mvn test                   # all tests
mvn test -Dtest=ClassName  # single test class
mvn test -pl . -Dtest=ClassName#methodName  # single test method
mvn verify                 # integration tests
make build / make run / make test / make benchmark / make clean / make docs
```

## Architecture

### Layered Structure

```
src/main/java/com/wtechitsolutions/
├── batch/          # Spring Batch jobs, readers, processors, writers
├── strategy/       # Strategy Pattern implementations per library+format
├── domain/         # Banking domain entities (Account, Transaction, Statement)
├── parser/         # Parser/formatter library wrappers
├── api/            # REST controllers
└── config/         # Spring configuration
```

### Core Flow

1. **REST** → `POST /api/domain/generate` seeds H2 with accounts/transactions
2. **REST** → `POST /api/batch/generate` triggers a Spring Batch job
3. **Spring Batch**: `ItemReader` (H2) → `ItemProcessor` (Strategy resolution) → `ItemWriter` (file to `/output/`)
4. **Strategy** selects the formatter library at runtime; generated file content is returned to the frontend

### Strategy Pattern

Each combination of file format × library gets its own strategy class:

```
CodaBeanIOStrategy, CodaFixedFormat4JStrategy, CodaFixedLengthStrategy, CodaBindyStrategy
SwiftBeanIOStrategy, SwiftFixedFormat4JStrategy, SwiftFixedLengthStrategy, SwiftBindyStrategy
```

All implement a common `FileGenerationStrategy` interface resolved by the `ItemProcessor`.

### Supported Formatter Libraries

| Library | Maven Group | Notes |
|---|---|---|
| BeanIO | `org.beanio` | Best grammar support for CODA; XML/annotation mapping |
| fixedformat4j | `com.ancientprogramming.fixedformat4j` | Best annotation quality; pure annotation-driven |
| fixedlength | (lightweight lib) | Simple, medium risk |
| Apache Camel Bindy | `org.apache.camel` | Use only if Camel ecosystem already present |

### REST API Endpoints

```
POST /api/domain/generate    → generate and persist banking data
POST /api/batch/generate     → trigger batch job (params: fileType, library)
GET  /api/batch/history      → batch execution history
GET  /api/benchmark/results  → benchmark metrics
GET  /actuator/health
GET  /actuator/info
```

## Java Package Convention

All packages: `com.wtechitsolutions.*`

```java
com.wtechitsolutions.batch
com.wtechitsolutions.strategy
com.wtechitsolutions.domain
com.wtechitsolutions.parser
com.wtechitsolutions.api
```

## Testing Strategy

- **Unit tests**: strategy classes, parsers, domain generation
- **Integration tests**: Spring Batch job execution, H2 persistence
- **Symmetry tests**: `Domain Object → generate file → parse file → rebuild → assertEquals`
- **Benchmark tests**: JMH micro-benchmarks per library for throughput/latency
- **API tests**: REST endpoint correctness, Actuator health/info, Swagger availability
- **Golden file tests**: compare output against known-good reference files in `docs/examples/`

## Repository Structure (Target)

```
root/
├── pom.xml
├── Makefile
├── src/main/java/com/wtechitsolutions/
├── src/test/java/com/wtechitsolutions/
├── docs/
│   ├── examples/coda/         # valid, malformed, edge-case, benchmark files
│   ├── examples/swift-mt/
│   ├── diagrams/              # .puml and .mmd files
│   └── slides/
├── tools/python/              # benchmark aggregation, report/markdown generation
├── output/                    # generated banking files (gitignored)
└── .github/
    ├── workflows/             # build.yml, test.yml, benchmark.yml, codeql.yml, release.yml
    └── dependabot.yml
```

## Key Constraints

- **Swagger** enabled in `dev` profile only
- **Spring Batch jobs** must be restartable
- **Output files** written to `/output/` directory (gitignored)
- **Benchmark datasets**: support 100,000+ transactions; small datasets must complete under 5 seconds
- Benchmark metrics exportable to CSV/JSON/Markdown
- All generated files must be reproducible