# Banking Fixed-Length Parser Comparison
## Slide Deck

**Author:** Wallace Espindola | **Date:** 2026-04-30

---

## Slide 1 — Platform Overview

**Banking Fixed-Length File Generator & Parser Validation Platform**

- Generates and parses **CODA** (Febelfin) and **SWIFT MT940** banking files
- Uses **4 Java formatter libraries** benchmarked side-by-side
- Orchestrated via **Spring Batch** + **Strategy Pattern**
- Single-JAR deployment: `mvn spring-boot:run`

---

## Slide 2 — Problem Statement

**Challenge:** Multiple Java libraries claim to support fixed-length banking file formats.  
Which one is best for enterprise Spring Batch use?

Evaluation criteria:
1. **Correctness** — Does output conform to Febelfin/SWIFT specifications?
2. **Performance** — Throughput in records/second under load
3. **Maintainability** — Annotation quality, XML-free, code clarity
4. **Spring Batch integration** — Chunk-oriented reader/writer compatibility

---

## Slide 3 — Architecture

```
React SPA → REST API → Spring Batch Pipeline
                              ↓
                    StrategyResolver (O(1) map)
                    ↙    ↙    ↘    ↘
              CODA×    CODA×   SWIFT×  SWIFT×
              BeanIO  ff4j    BeanIO  ff4j  ...
                    ↓
              Parser Wrappers (annotation-only, no XML)
                    ↓
              H2 In-Memory DB + output/ files
```

---

## Slide 4 — CODA Format

**Febelfin CODA** — Belgian/European banking statement format

- **128 characters** per record, fixed-width
- Record types: `0` header, `1` movement, `2` detail, `8` trailer, `9` end
- Fields: bank ID (3), reference (10), IBAN (37), currency (3), amount (16), dates (6+6), description (32), codes (3+4), filler (7)
- Amounts: plain integer with implied 2 decimal places

```
0310HDR       BE0010000000000000000000000000000000  EUR00000000000000000000290426290426CODA HEADER                     0000000000
1310REF0000001 BE001234567890123456789012345678900EUR00000000000007500290426290426Payment for services             0010001
```

---

## Slide 5 — SWIFT MT940 Format

**SWIFT MT940** — International bank statement message

- **Tag-based** format: `:TAG:value`
- Key tags: `:20:` reference, `:25:` account, `:28C:` sequence, `:60F:` opening balance, `:61:` entry, `:86:` narrative, `:62F:` closing balance

```
:20:STMT000001
:25:BE68539007547034/EUR
:28C:00001/001
:60F:C260429EUR10000,00
:61:260429260429C750NMSCREF000001NONREF
:86:Payment for services rendered
:62F:C260429EUR10750,00
```

---

## Slide 6 — Library Comparison

| Library | Version | Annotation Style | XML Required | Spring Batch Fit |
|---|---|---|---|---|
| **BeanIO** | 2.1.0 | `@Record` + `FieldBuilder` | ❌ None | ✅ Good |
| **fixedformat4j** | 1.7.0 | `@Record` + `@Field(offset, length)` | ❌ None | ✅ Excellent |
| **fixedlength** | 0.15 | `@FixedLine` + `@FixedField(offset, length)` | ❌ None | ✅ Good |
| **Camel Bindy** | 4.20.0 | `@FixedLengthRecord` + `@DataField(pos, length)` | ❌ None | ⚠️ Needs CamelContext |

---

## Slide 7 — Strategy Pattern Implementation

```java
// One interface, 8 implementations
public interface FileGenerationStrategy {
    String generate(List<Transaction> txs, List<Account> accts);
    List<Transaction> parse(String fileContent);
    FileType getFileType();   // CODA or SWIFT
    Library getLibrary();     // BEANIO, FIXEDFORMAT4J, FIXEDLENGTH, BINDY
}

// Resolution — no if/switch, O(1) map lookup
FileGenerationStrategy s = strategyResolver.resolve(FileType.CODA, Library.BEANIO);
String codaFile = s.generate(transactions, accounts);
```

8 concrete classes: `Coda/Swift × BeanIO/FixedFormat4J/FixedLength/Bindy`

---

## Slide 8 — Spring Batch Pipeline

```
bankingFileGenerationJob (restartable)
└── fileGenerationStep  (chunk-size = 100)
    ├── DomainEntityItemReader   → reads Transactions from H2
    ├── FileGenerationItemProcessor → applies FileGenerationStrategy
    └── FileOutputItemWriter     → writes to output/*.txt
          ↓
    BatchMetricsListener → saves BenchmarkMetrics to H2
```

Job parameters: `fileType`, `library`, `operationId`, `runTimestamp`  
Each run = unique `JobInstance` → fully restartable from last checkpoint.

---

## Slide 9 — Benchmark Results

> Execute: `mvn test -Pbenchmark -Pskip-frontend`

| Metric | Description |
|---|---|
| `throughputRps` | Records processed per second |
| `batchDurationMs` | Total job wall-clock time |
| `generationDurationMs` | File serialisation time only |
| `parseDurationMs` | File parsing time (round-trip) |
| `symmetryRate` | % of parsed records matching original |
| `successRate` | % of chunks completed without error |

Export: `GET /api/benchmark/export/csv` or `/json` or `/markdown`

---

## Slide 10 — Recommendations

| Use Case | Recommended Library | Reason |
|---|---|---|
| Enterprise CODA processing | **BeanIO** | Best grammar, restartable batch |
| New projects, clean code | **fixedformat4j** | Best annotation DX, no boilerplate |
| Existing Camel ecosystem | **Camel Bindy** | Native Camel integration |
| Prototyping / lightweight | **fixedlength** | Minimal setup, pure annotations |

**Avoid** mixing multiple libraries in production — pick one and standardise.

---

## Slide 11 — Code Quality & CI/CD

- **Tests:** 62 tests passing (unit, integration, symmetry, API, Actuator, Swagger)
- **Coverage:** JaCoCo enforced (minimum threshold configured)
- **CI:** GitHub Actions — build, test, benchmark, CodeQL security scan, releases
- **Dependencies:** Dependabot auto-PRs weekly for Maven + Actions + npm
- **Security:** CodeQL scans on push + weekly schedule; no secrets in repo

---

## Slide 12 — References

- [Febelfin CODA Specification](https://www.febelfin.be/en/payments-standards/coda)
- [SWIFT MT940 Documentation](https://www.swift.com/standards/data-standards/mt)
- [BeanIO](https://mvnrepository.com/artifact/org.beanio/beanio)
- [fixedformat4j](https://mvnrepository.com/artifact/com.ancientprogramming.fixedformat4j/fixedformat4j)
- [fixedlength](https://mvnrepository.com/artifact/name.velikodniy.vitaliy/fixedlength)
- [Apache Camel Bindy](https://camel.apache.org/components/latest/dataformats/bindy-dataformat.html)

---

*Wallace Espindola — wallace.espindola@gmail.com — [linkedin.com/in/wallaceespindola](https://www.linkedin.com/in/wallaceespindola/)*
