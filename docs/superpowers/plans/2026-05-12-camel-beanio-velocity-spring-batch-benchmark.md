# Camel BeanIO + Velocity + Spring Batch Native Benchmark Integration — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add 3 new parser library participants (Apache Camel BeanIO, Apache Velocity, Spring Batch native flat-file) to the benchmark platform, growing it from 4 to 7 libraries / 8 to 14 strategies / 8 to 28 JMH `@Benchmark` methods. Add a new HTML benchmark report export driven by a Velocity template.

**Architecture:** Each new library follows the existing Strategy Pattern exactly — one `@Component` formatter wrapping the library, two `@Service` strategies (CODA + SWIFT) extending the existing `AbstractCodaStrategy` / `AbstractSwiftStrategy`, registered automatically via `StrategyResolver`. Velocity templates live under `src/main/resources/velocity/`. BeanIO XML mapping lives under `src/main/resources/beanio/`. SWIFT serialisation for all three new libraries delegates to `SwiftMtRecord.toSwiftFormat()` / `fromSwiftSection()` (the standard pattern used by every existing SWIFT strategy).

**Tech Stack:** Java 21, Spring Boot 3.4.5, Spring Batch 5.x, Apache Camel 4.20.0 (`camel-beanio`), Apache Velocity 2.3 (`velocity-engine-core`, `velocity-tools-generic` 3.1), JMH 1.37, JUnit 5 + Mockito + AssertJ. All Maven dependencies are already in `pom.xml`.

**Spec:** [`docs/superpowers/specs/2026-05-12-camel-beanio-velocity-benchmark-design.md`](../specs/2026-05-12-camel-beanio-velocity-benchmark-design.md)

---

## Pre-flight

### Task 0: Verify clean baseline

**Files:** None

- [ ] **Step 1: Verify all existing tests pass**

```bash
mvn test -Pskip-frontend -q
```

Expected: BUILD SUCCESS. All 76 tests pass.

- [ ] **Step 2: Verify Maven dependencies resolve**

```bash
mvn dependency:resolve -Pskip-frontend -q
```

Expected: No errors. `camel-beanio`, `velocity-engine-core`, `velocity-tools-generic` all download.

- [ ] **Step 3: Verify benchmark profile runs**

```bash
mvn test -Pbenchmark -Pskip-frontend -q -DskipTests=false 2>&1 | tail -5
```

Expected: 8 JMH benchmarks complete successfully (current baseline).

---

## Task 1: Apache Camel BeanIO library integration

**Files:**
- Create: `src/main/resources/beanio/coda-mapping.xml`
- Create: `src/main/java/com/wtechitsolutions/parser/CamelBeanIOFormatter.java`
- Create: `src/main/java/com/wtechitsolutions/strategy/CodaCamelBeanIOStrategy.java`
- Create: `src/main/java/com/wtechitsolutions/strategy/SwiftCamelBeanIOStrategy.java`
- Modify: `src/main/java/com/wtechitsolutions/domain/Library.java`

### Step 1: Write failing strategy resolution test for CAMEL_BEANIO

There is no separate test file — `StrategyResolverTest` already iterates `Library.values()`, so adding the enum value will make existing tests fail until strategies exist. Skip writing a new test; rely on the existing parametrized tests.

- [ ] **Step 2: Add CAMEL_BEANIO to the Library enum**

Edit `src/main/java/com/wtechitsolutions/domain/Library.java`:

```java
package com.wtechitsolutions.domain;

public enum Library {
    BEANIO,
    FIXFORMAT4J,
    FIXEDLENGTH,
    BINDY,
    CAMEL_BEANIO
}
```

- [ ] **Step 3: Run tests to verify they fail with no strategy registered**

```bash
mvn test -Pskip-frontend -Dtest=StrategyResolverTest -q
```

Expected: FAIL — "No strategy registered for key: CODA_CAMEL_BEANIO" and "SWIFT_CAMEL_BEANIO".

- [ ] **Step 4: Create the BeanIO XML mapping file**

Create `src/main/resources/beanio/coda-mapping.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beanio xmlns="http://www.beanio.org/2012/03">
    <stream name="coda" format="fixedlength">
        <record name="codaRecord" class="com.wtechitsolutions.parser.model.BeanIoCodaRecord">
            <field name="recordType" length="1"/>
            <field name="bankId" length="3"/>
            <field name="referenceNumber" length="10"/>
            <field name="accountNumber" length="37"/>
            <field name="currency" length="3"/>
            <field name="amountStr" length="16" padding="0" justify="right"/>
            <field name="entryDate" length="6"/>
            <field name="valueDate" length="6"/>
            <field name="description" length="32"/>
            <field name="transactionCode" length="3"/>
            <field name="sequenceNumber" length="4" justify="right"/>
            <field name="filler" length="7"/>
        </record>
    </stream>
</beanio>
```

This mirrors the field layout defined programmatically in `BeanIOFormatter`'s `StreamBuilder` (positions sum to exactly 128 chars).

- [ ] **Step 5: Implement CamelBeanIOFormatter**

Create `src/main/java/com/wtechitsolutions/parser/CamelBeanIOFormatter.java`:

```java
package com.wtechitsolutions.parser;

import com.wtechitsolutions.parser.model.BeanIoCodaRecord;
import com.wtechitsolutions.parser.model.CodaRecord;
import com.wtechitsolutions.parser.model.SwiftMtRecord;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.dataformat.beanio.BeanIODataFormat;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Formatter wrapping Apache Camel BeanIO data format (4.20.0) for CODA serialisation.
 * Uses a classpath BeanIO mapping XML (beanio/coda-mapping.xml) and routes
 * marshal/unmarshal calls through a standalone CamelContext.
 *
 * SWIFT delegates to SwiftMtRecord.toSwiftFormat()/fromSwiftSection() — same as
 * every other SWIFT strategy in the codebase.
 */
@Component
public class CamelBeanIOFormatter {

    private static final Logger log = LoggerFactory.getLogger(CamelBeanIOFormatter.class);
    private static final String MAPPING = "beanio/coda-mapping.xml";
    private static final String STREAM = "coda";

    private CamelContext camelContext;
    private BeanIODataFormat codaFormat;

    @PostConstruct
    public void init() {
        try {
            camelContext = new DefaultCamelContext();
            codaFormat = new BeanIODataFormat(MAPPING, STREAM);
            codaFormat.setCamelContext(camelContext);
            camelContext.start();
            codaFormat.start();
        } catch (Exception e) {
            log.error("Failed to initialise CamelBeanIOFormatter: {}", e.getMessage());
            throw new RuntimeException("Camel BeanIO initialisation failed", e);
        }
    }

    @PreDestroy
    void shutdown() {
        try {
            if (codaFormat != null) codaFormat.stop();
            if (camelContext != null) camelContext.stop();
        } catch (Exception e) {
            log.warn("CamelBeanIOFormatter shutdown error: {}", e.getMessage());
        }
    }

    public String formatCoda(List<CodaRecord> records) {
        try {
            List<BeanIoCodaRecord> beanio = records.stream()
                    .map(this::toBeanIo).collect(Collectors.toList());
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Exchange exchange = new DefaultExchange(camelContext);
            codaFormat.marshal(exchange, beanio, out);
            return out.toString(StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").replace("\r", "\n");
        } catch (Exception e) {
            log.warn("Camel BeanIO CODA format failed: {}", e.getMessage());
            return records.stream().map(CodaRecord::toFixedWidth)
                    .collect(Collectors.joining("\n")) + "\n";
        }
    }

    @SuppressWarnings("unchecked")
    public List<CodaRecord> parseCoda(String content) {
        if (content == null || content.isBlank()) return List.of();
        try {
            String padded = Arrays.stream(content.split("\n"))
                    .filter(l -> !l.isBlank())
                    .map(this::ensureWidth)
                    .collect(Collectors.joining("\n")) + "\n";
            Exchange exchange = new DefaultExchange(camelContext);
            Object parsed = codaFormat.unmarshal(exchange,
                    new ByteArrayInputStream(padded.getBytes(StandardCharsets.UTF_8)));
            List<CodaRecord> result = new ArrayList<>();
            if (parsed instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof BeanIoCodaRecord bcr) result.add(fromBeanIo(bcr));
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("Camel BeanIO CODA parse failed: {}", e.getMessage());
            return Arrays.stream(content.split("\n"))
                    .filter(l -> !l.isBlank())
                    .map(CodaRecord::fromFixedWidth)
                    .collect(Collectors.toList());
        }
    }

    public String formatSwift(List<SwiftMtRecord> records) {
        StringBuilder sb = new StringBuilder();
        for (SwiftMtRecord r : records) sb.append(r.toSwiftFormat()).append("---\n");
        return sb.toString();
    }

    public List<SwiftMtRecord> parseSwift(String content) {
        return Arrays.stream(content.split("---\n"))
                .filter(s -> !s.isBlank())
                .map(SwiftMtRecord::fromSwiftSection)
                .toList();
    }

    private BeanIoCodaRecord toBeanIo(CodaRecord r) {
        BeanIoCodaRecord b = new BeanIoCodaRecord();
        b.setRecordType(orEmpty(r.getRecordType()));
        b.setBankId(orEmpty(r.getBankId()));
        b.setReferenceNumber(orEmpty(r.getReferenceNumber()));
        b.setAccountNumber(orEmpty(r.getAccountNumber()));
        b.setCurrency(orEmpty(r.getCurrency()));
        b.setAmountStr(amountToStr(r.getAmount()));
        b.setEntryDate(orEmpty(r.getEntryDate()));
        b.setValueDate(orEmpty(r.getValueDate()));
        b.setDescription(orEmpty(r.getDescription()));
        b.setTransactionCode(orEmpty(r.getTransactionCode()));
        b.setSequenceNumber(orEmpty(r.getSequenceNumber()));
        b.setFiller(orEmpty(r.getFiller()));
        return b;
    }

    private CodaRecord fromBeanIo(BeanIoCodaRecord b) {
        return CodaRecord.builder()
                .recordType(trim(b.getRecordType()))
                .bankId(trim(b.getBankId()))
                .referenceNumber(trim(b.getReferenceNumber()))
                .accountNumber(trim(b.getAccountNumber()))
                .currency(trim(b.getCurrency()))
                .amount(parseAmount(b.getAmountStr()))
                .entryDate(trim(b.getEntryDate()))
                .valueDate(trim(b.getValueDate()))
                .description(trim(b.getDescription()))
                .transactionCode(trim(b.getTransactionCode()))
                .sequenceNumber(trim(b.getSequenceNumber()))
                .filler(trim(b.getFiller()))
                .build();
    }

    private String ensureWidth(String line) {
        if (line.length() < 128) return line + " ".repeat(128 - line.length());
        return line.length() > 128 ? line.substring(0, 128) : line;
    }

    private static String orEmpty(String s) { return s != null ? s : ""; }
    private static String trim(String s) { return s != null ? s.trim() : ""; }
    private static String amountToStr(BigDecimal a) {
        return (a != null ? a : BigDecimal.ZERO).abs().toBigInteger().toString();
    }
    private static BigDecimal parseAmount(String s) {
        if (s == null || s.isBlank()) return BigDecimal.ZERO;
        try { return new BigDecimal(s.trim()); }
        catch (NumberFormatException e) { return BigDecimal.ZERO; }
    }
}
```

- [ ] **Step 6: Implement CodaCamelBeanIOStrategy**

Create `src/main/java/com/wtechitsolutions/strategy/CodaCamelBeanIOStrategy.java`:

```java
package com.wtechitsolutions.strategy;

import com.wtechitsolutions.domain.Library;
import com.wtechitsolutions.parser.CamelBeanIOFormatter;
import com.wtechitsolutions.parser.model.CodaRecord;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CodaCamelBeanIOStrategy extends AbstractCodaStrategy {

    private final CamelBeanIOFormatter formatter;

    public CodaCamelBeanIOStrategy(CamelBeanIOFormatter formatter) {
        this.formatter = formatter;
    }

    @Override
    public Library getLibrary() { return Library.CAMEL_BEANIO; }

    @Override
    protected String formatRecords(List<CodaRecord> records) {
        return formatter.formatCoda(records);
    }

    @Override
    protected List<CodaRecord> parseRecords(String content) {
        return formatter.parseCoda(content);
    }
}
```

- [ ] **Step 7: Implement SwiftCamelBeanIOStrategy**

Create `src/main/java/com/wtechitsolutions/strategy/SwiftCamelBeanIOStrategy.java`:

```java
package com.wtechitsolutions.strategy;

import com.wtechitsolutions.domain.Library;
import com.wtechitsolutions.parser.CamelBeanIOFormatter;
import com.wtechitsolutions.parser.model.SwiftMtRecord;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SwiftCamelBeanIOStrategy extends AbstractSwiftStrategy {

    private final CamelBeanIOFormatter formatter;

    public SwiftCamelBeanIOStrategy(CamelBeanIOFormatter formatter) {
        this.formatter = formatter;
    }

    @Override
    public Library getLibrary() { return Library.CAMEL_BEANIO; }

    @Override
    protected String formatRecords(List<SwiftMtRecord> records) {
        return formatter.formatSwift(records);
    }

    @Override
    protected List<SwiftMtRecord> parseRecords(String content) {
        return formatter.parseSwift(content);
    }
}
```

- [ ] **Step 8: Run all tests to verify CAMEL_BEANIO strategies are picked up**

```bash
mvn test -Pskip-frontend -q
```

Expected: BUILD SUCCESS. `StrategyResolverTest`, `CodaStrategyTest`, `SwiftStrategyTest`, `SymmetryTest` all pass with CAMEL_BEANIO included (auto-discovered via `Library.values()`).

- [ ] **Step 9: Commit**

```bash
git add src/main/resources/beanio/coda-mapping.xml \
        src/main/java/com/wtechitsolutions/parser/CamelBeanIOFormatter.java \
        src/main/java/com/wtechitsolutions/strategy/CodaCamelBeanIOStrategy.java \
        src/main/java/com/wtechitsolutions/strategy/SwiftCamelBeanIOStrategy.java \
        src/main/java/com/wtechitsolutions/domain/Library.java
git commit -m "feat: add Apache Camel BeanIO as a benchmark library participant"
```

---

## Task 2: Apache Velocity library integration (file generation)

**Files:**
- Create: `src/main/resources/velocity/coda-record.vm`
- Create: `src/main/resources/velocity/swift-record.vm`
- Create: `src/main/java/com/wtechitsolutions/parser/VelocityFormatter.java`
- Create: `src/main/java/com/wtechitsolutions/strategy/CodaVelocityStrategy.java`
- Create: `src/main/java/com/wtechitsolutions/strategy/SwiftVelocityStrategy.java`
- Modify: `src/main/java/com/wtechitsolutions/domain/Library.java`

### Step 1: Create the CODA Velocity template

Create `src/main/resources/velocity/coda-record.vm`:

```velocity
#foreach($r in $records)${r.recordType}${r.bankId}${r.referenceNumber}${r.accountNumber}${r.currency}${r.amountStr}${r.entryDate}${r.valueDate}${r.description}${r.transactionCode}${r.sequenceNumber}${r.filler}
#end
```

Notes:
- Template assumes each field is already padded to its correct length (done in `VelocityFormatter` before merge)
- `#foreach($r in $records) ... #end` emits one 128-char line per record followed by newline
- No whitespace between Velocity tags to avoid producing extra characters

- [ ] **Step 2: Create the SWIFT Velocity template**

Create `src/main/resources/velocity/swift-record.vm`:

```velocity
#foreach($r in $records):20:${r.transactionReference}
:25:${r.accountIdentification}
:28C:${r.statementNumber}
:60F:${r.openingBalance}
:61:${r.valueDate}${r.entryDate}${r.debitCreditMark}${r.amount}${r.transactionType}${r.customerReference}
:86:${r.information}
:62F:${r.closingBalance}
---
#end
```

- [ ] **Step 3: Add VELOCITY to the Library enum**

Edit `src/main/java/com/wtechitsolutions/domain/Library.java`:

```java
package com.wtechitsolutions.domain;

public enum Library {
    BEANIO,
    FIXFORMAT4J,
    FIXEDLENGTH,
    BINDY,
    CAMEL_BEANIO,
    VELOCITY
}
```

- [ ] **Step 4: Run tests to verify failure for VELOCITY**

```bash
mvn test -Pskip-frontend -Dtest=StrategyResolverTest -q
```

Expected: FAIL — "No strategy registered for key: CODA_VELOCITY".

- [ ] **Step 5: Implement VelocityFormatter**

Create `src/main/java/com/wtechitsolutions/parser/VelocityFormatter.java`:

```java
package com.wtechitsolutions.parser;

import com.wtechitsolutions.parser.model.CodaRecord;
import com.wtechitsolutions.parser.model.SwiftMtRecord;
import jakarta.annotation.PostConstruct;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.StringWriter;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Formatter using Apache Velocity 2.3 templates for CODA and SWIFT MT serialisation.
 *
 * Fields are padded in Java before being merged into the template, so templates
 * stay simple ($field concatenation). Parse path delegates to the shared
 * CodaRecord/SwiftMtRecord parsers since Velocity is a one-way template engine.
 */
@Component
public class VelocityFormatter {

    private static final Logger log = LoggerFactory.getLogger(VelocityFormatter.class);
    private static final String CODA_TEMPLATE = "velocity/coda-record.vm";
    private static final String SWIFT_TEMPLATE = "velocity/swift-record.vm";

    private VelocityEngine engine;

    @PostConstruct
    public void init() {
        engine = new VelocityEngine();
        engine.setProperty(RuntimeConstants.RESOURCE_LOADERS, "classpath");
        engine.setProperty("resource.loader.classpath.class",
                ClasspathResourceLoader.class.getName());
        engine.init();
    }

    public String formatCoda(List<CodaRecord> records) {
        try {
            List<Map<String, String>> padded = records.stream()
                    .map(VelocityFormatter::padCoda)
                    .collect(Collectors.toList());
            VelocityContext context = new VelocityContext();
            context.put("records", padded);
            StringWriter writer = new StringWriter();
            engine.getTemplate(CODA_TEMPLATE).merge(context, writer);
            return writer.toString();
        } catch (Exception e) {
            log.warn("Velocity CODA format failed: {}", e.getMessage());
            return records.stream().map(CodaRecord::toFixedWidth)
                    .collect(Collectors.joining("\n")) + "\n";
        }
    }

    public List<CodaRecord> parseCoda(String content) {
        if (content == null || content.isBlank()) return List.of();
        return Arrays.stream(content.split("\n"))
                .filter(l -> !l.isBlank())
                .map(CodaRecord::fromFixedWidth)
                .collect(Collectors.toList());
    }

    public String formatSwift(List<SwiftMtRecord> records) {
        try {
            VelocityContext context = new VelocityContext();
            context.put("records", records.stream()
                    .map(VelocityFormatter::swiftFallbacks)
                    .collect(Collectors.toList()));
            StringWriter writer = new StringWriter();
            engine.getTemplate(SWIFT_TEMPLATE).merge(context, writer);
            return writer.toString();
        } catch (Exception e) {
            log.warn("Velocity SWIFT format failed: {}", e.getMessage());
            StringBuilder sb = new StringBuilder();
            for (SwiftMtRecord r : records) sb.append(r.toSwiftFormat()).append("---\n");
            return sb.toString();
        }
    }

    public List<SwiftMtRecord> parseSwift(String content) {
        return Arrays.stream(content.split("---\n"))
                .filter(s -> !s.isBlank())
                .map(SwiftMtRecord::fromSwiftSection)
                .toList();
    }

    private static Map<String, String> padCoda(CodaRecord r) {
        Map<String, String> m = new HashMap<>();
        m.put("recordType", padRight(r.getRecordType(), 1));
        m.put("bankId", padRight(r.getBankId(), 3));
        m.put("referenceNumber", padRight(r.getReferenceNumber(), 10));
        m.put("accountNumber", padRight(r.getAccountNumber(), 37));
        m.put("currency", padRight(r.getCurrency(), 3));
        m.put("amountStr", padAmount(r.getAmount(), 16));
        m.put("entryDate", padRight(r.getEntryDate(), 6));
        m.put("valueDate", padRight(r.getValueDate(), 6));
        m.put("description", padRight(r.getDescription(), 32));
        m.put("transactionCode", padRight(r.getTransactionCode(), 3));
        m.put("sequenceNumber", padLeft(r.getSequenceNumber(), 4));
        m.put("filler", padRight(r.getFiller(), 7));
        return m;
    }

    private static SwiftMtRecord swiftFallbacks(SwiftMtRecord r) {
        return SwiftMtRecord.builder()
                .transactionReference(orEmpty(r.getTransactionReference()))
                .accountIdentification(orEmpty(r.getAccountIdentification()))
                .statementNumber(orEmpty(r.getStatementNumber()))
                .openingBalance(orEmpty(r.getOpeningBalance()))
                .valueDate(or(r.getValueDate(), "000000"))
                .entryDate(or(r.getEntryDate(), "0000"))
                .debitCreditMark(or(r.getDebitCreditMark(), "C"))
                .amount(or(r.getAmount(), "0,00"))
                .transactionType(or(r.getTransactionType(), "NMSC"))
                .customerReference(or(r.getCustomerReference(), "NONREF"))
                .information(orEmpty(r.getInformation()))
                .closingBalance(orEmpty(r.getClosingBalance()))
                .build();
    }

    private static String padRight(String value, int length) {
        String v = value != null ? value : "";
        if (v.length() >= length) return v.substring(0, length);
        return v + " ".repeat(length - v.length());
    }

    private static String padLeft(String value, int length) {
        String v = value != null ? value : "";
        if (v.length() >= length) return v.substring(0, length);
        return " ".repeat(length - v.length()) + v;
    }

    private static String padAmount(BigDecimal amount, int length) {
        BigDecimal a = amount != null ? amount : BigDecimal.ZERO;
        String s = a.abs().toBigInteger().toString();
        if (s.length() >= length) return s.substring(0, length);
        return "0".repeat(length - s.length()) + s;
    }

    private static String orEmpty(String s) { return s != null ? s : ""; }
    private static String or(String s, String fb) { return s != null && !s.isBlank() ? s : fb; }
}
```

- [ ] **Step 6: Implement CodaVelocityStrategy**

Create `src/main/java/com/wtechitsolutions/strategy/CodaVelocityStrategy.java`:

```java
package com.wtechitsolutions.strategy;

import com.wtechitsolutions.domain.Library;
import com.wtechitsolutions.parser.VelocityFormatter;
import com.wtechitsolutions.parser.model.CodaRecord;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CodaVelocityStrategy extends AbstractCodaStrategy {

    private final VelocityFormatter formatter;

    public CodaVelocityStrategy(VelocityFormatter formatter) {
        this.formatter = formatter;
    }

    @Override
    public Library getLibrary() { return Library.VELOCITY; }

    @Override
    protected String formatRecords(List<CodaRecord> records) {
        return formatter.formatCoda(records);
    }

    @Override
    protected List<CodaRecord> parseRecords(String content) {
        return formatter.parseCoda(content);
    }
}
```

- [ ] **Step 7: Implement SwiftVelocityStrategy**

Create `src/main/java/com/wtechitsolutions/strategy/SwiftVelocityStrategy.java`:

```java
package com.wtechitsolutions.strategy;

import com.wtechitsolutions.domain.Library;
import com.wtechitsolutions.parser.VelocityFormatter;
import com.wtechitsolutions.parser.model.SwiftMtRecord;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SwiftVelocityStrategy extends AbstractSwiftStrategy {

    private final VelocityFormatter formatter;

    public SwiftVelocityStrategy(VelocityFormatter formatter) {
        this.formatter = formatter;
    }

    @Override
    public Library getLibrary() { return Library.VELOCITY; }

    @Override
    protected String formatRecords(List<SwiftMtRecord> records) {
        return formatter.formatSwift(records);
    }

    @Override
    protected List<SwiftMtRecord> parseRecords(String content) {
        return formatter.parseSwift(content);
    }
}
```

- [ ] **Step 8: Run all tests to verify VELOCITY strategies are picked up**

```bash
mvn test -Pskip-frontend -q
```

Expected: BUILD SUCCESS. All Coda/Swift/Symmetry parameterized tests pass for VELOCITY.

Note: Velocity 2.x routes logs through SLF4J by default — no explicit log configuration is needed.

- [ ] **Step 9: Commit**

```bash
git add src/main/resources/velocity/coda-record.vm \
        src/main/resources/velocity/swift-record.vm \
        src/main/java/com/wtechitsolutions/parser/VelocityFormatter.java \
        src/main/java/com/wtechitsolutions/strategy/CodaVelocityStrategy.java \
        src/main/java/com/wtechitsolutions/strategy/SwiftVelocityStrategy.java \
        src/main/java/com/wtechitsolutions/domain/Library.java
git commit -m "feat: add Apache Velocity as a benchmark library participant"
```

---

## Task 3: Spring Batch native library integration

**Files:**
- Create: `src/main/java/com/wtechitsolutions/parser/SpringBatchFormatter.java`
- Create: `src/main/java/com/wtechitsolutions/strategy/CodaSpringBatchStrategy.java`
- Create: `src/main/java/com/wtechitsolutions/strategy/SwiftSpringBatchStrategy.java`
- Modify: `src/main/java/com/wtechitsolutions/domain/Library.java`

### Step 1: Add SPRING_BATCH to the Library enum

Edit `src/main/java/com/wtechitsolutions/domain/Library.java`:

```java
package com.wtechitsolutions.domain;

public enum Library {
    BEANIO,
    FIXFORMAT4J,
    FIXEDLENGTH,
    BINDY,
    CAMEL_BEANIO,
    VELOCITY,
    SPRING_BATCH
}
```

- [ ] **Step 2: Run tests to verify failure**

```bash
mvn test -Pskip-frontend -Dtest=StrategyResolverTest -q
```

Expected: FAIL — "No strategy registered for key: CODA_SPRING_BATCH".

- [ ] **Step 3: Implement SpringBatchFormatter**

Create `src/main/java/com/wtechitsolutions/parser/SpringBatchFormatter.java`:

```java
package com.wtechitsolutions.parser;

import com.wtechitsolutions.parser.model.CodaRecord;
import com.wtechitsolutions.parser.model.SwiftMtRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.FlatFileItemWriter;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.item.file.builder.FlatFileItemWriterBuilder;
import org.springframework.batch.item.file.mapping.FieldSetMapper;
import org.springframework.batch.item.file.transform.FieldExtractor;
import org.springframework.batch.item.file.transform.FixedLengthTokenizer;
import org.springframework.batch.item.file.transform.FormatterLineAggregator;
import org.springframework.batch.item.file.transform.LineAggregator;
import org.springframework.batch.item.file.transform.Range;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Formatter using Spring Batch's native flat-file infrastructure:
 *   - FlatFileItemWriter + custom LineAggregator (formatRecords path)
 *   - FlatFileItemReader + FixedLengthTokenizer + FieldSetMapper (parseRecords path)
 *
 * Uses temp files because Spring Batch I/O is designed around WritableResource;
 * the file I/O overhead is part of the benchmark — it shows the cost of using
 * Spring Batch's native components.
 *
 * SWIFT delegates to SwiftMtRecord.toSwiftFormat()/fromSwiftSection().
 */
@Component
public class SpringBatchFormatter {

    private static final Logger log = LoggerFactory.getLogger(SpringBatchFormatter.class);

    private final LineAggregator<CodaRecord> codaAggregator;
    private final FixedLengthTokenizer codaTokenizer;
    private final FieldSetMapper<CodaRecord> codaFieldSetMapper;

    public SpringBatchFormatter() {
        this.codaAggregator = buildCodaAggregator();
        this.codaTokenizer = buildCodaTokenizer();
        this.codaFieldSetMapper = buildCodaFieldSetMapper();
    }

    public String formatCoda(List<CodaRecord> records) {
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("sb-coda-", ".dat");
            FlatFileItemWriter<CodaRecord> writer = new FlatFileItemWriterBuilder<CodaRecord>()
                    .name("codaWriter")
                    .resource(new FileSystemResource(tempFile.toFile()))
                    .lineAggregator(codaAggregator)
                    .build();
            writer.open(new ExecutionContext());
            try {
                writer.write(new Chunk<>(records));
            } finally {
                writer.close();
            }
            return Files.readString(tempFile);
        } catch (Exception e) {
            log.warn("Spring Batch CODA format failed: {}", e.getMessage());
            return records.stream().map(CodaRecord::toFixedWidth)
                    .collect(Collectors.joining("\n")) + "\n";
        } finally {
            deleteQuietly(tempFile);
        }
    }

    public List<CodaRecord> parseCoda(String content) {
        if (content == null || content.isBlank()) return List.of();
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("sb-coda-parse-", ".dat");
            String padded = Arrays.stream(content.split("\n"))
                    .filter(l -> !l.isBlank())
                    .map(SpringBatchFormatter::ensureWidth)
                    .collect(Collectors.joining("\n")) + "\n";
            Files.writeString(tempFile, padded);

            FlatFileItemReader<CodaRecord> reader = new FlatFileItemReaderBuilder<CodaRecord>()
                    .name("codaReader")
                    .resource(new FileSystemResource(tempFile.toFile()))
                    .lineTokenizer(codaTokenizer)
                    .fieldSetMapper(codaFieldSetMapper)
                    .build();
            reader.open(new ExecutionContext());
            try {
                List<CodaRecord> out = new ArrayList<>();
                CodaRecord r;
                while ((r = reader.read()) != null) out.add(r);
                return out;
            } finally {
                reader.close();
            }
        } catch (Exception e) {
            log.warn("Spring Batch CODA parse failed: {}", e.getMessage());
            return Arrays.stream(content.split("\n"))
                    .filter(l -> !l.isBlank())
                    .map(CodaRecord::fromFixedWidth)
                    .collect(Collectors.toList());
        } finally {
            deleteQuietly(tempFile);
        }
    }

    public String formatSwift(List<SwiftMtRecord> records) {
        StringBuilder sb = new StringBuilder();
        for (SwiftMtRecord r : records) sb.append(r.toSwiftFormat()).append("---\n");
        return sb.toString();
    }

    public List<SwiftMtRecord> parseSwift(String content) {
        return Arrays.stream(content.split("---\n"))
                .filter(s -> !s.isBlank())
                .map(SwiftMtRecord::fromSwiftSection)
                .toList();
    }

    // ── Spring Batch component builders ───────────────────────────────────────

    private LineAggregator<CodaRecord> buildCodaAggregator() {
        FormatterLineAggregator<CodaRecord> agg = new FormatterLineAggregator<>();
        agg.setFormat("%-1.1s%-3.3s%-10.10s%-37.37s%-3.3s%16.16s%-6.6s%-6.6s%-32.32s%-3.3s%4.4s%-7.7s");
        agg.setFieldExtractor((FieldExtractor<CodaRecord>) r -> new Object[]{
                orEmpty(r.getRecordType()),
                orEmpty(r.getBankId()),
                orEmpty(r.getReferenceNumber()),
                orEmpty(r.getAccountNumber()),
                orEmpty(r.getCurrency()),
                padAmount(r.getAmount(), 16),
                orEmpty(r.getEntryDate()),
                orEmpty(r.getValueDate()),
                orEmpty(r.getDescription()),
                orEmpty(r.getTransactionCode()),
                padLeft(orEmpty(r.getSequenceNumber()), 4),
                orEmpty(r.getFiller())
        });
        return agg;
    }

    private FixedLengthTokenizer buildCodaTokenizer() {
        FixedLengthTokenizer t = new FixedLengthTokenizer();
        t.setColumns(
                new Range(1, 1), new Range(2, 4), new Range(5, 14), new Range(15, 51),
                new Range(52, 54), new Range(55, 70), new Range(71, 76), new Range(77, 82),
                new Range(83, 114), new Range(115, 117), new Range(118, 121), new Range(122, 128)
        );
        t.setNames("recordType", "bankId", "referenceNumber", "accountNumber",
                "currency", "amountStr", "entryDate", "valueDate", "description",
                "transactionCode", "sequenceNumber", "filler");
        t.setStrict(false);
        return t;
    }

    private FieldSetMapper<CodaRecord> buildCodaFieldSetMapper() {
        return fs -> {
            String amountStr = fs.readString("amountStr").trim();
            BigDecimal amount;
            try {
                amount = amountStr.isBlank() ? BigDecimal.ZERO : new BigDecimal(amountStr);
            } catch (NumberFormatException e) {
                amount = BigDecimal.ZERO;
            }
            return CodaRecord.builder()
                    .recordType(fs.readString("recordType").trim())
                    .bankId(fs.readString("bankId").trim())
                    .referenceNumber(fs.readString("referenceNumber").trim())
                    .accountNumber(fs.readString("accountNumber").trim())
                    .currency(fs.readString("currency").trim())
                    .amount(amount)
                    .entryDate(fs.readString("entryDate").trim())
                    .valueDate(fs.readString("valueDate").trim())
                    .description(fs.readString("description").trim())
                    .transactionCode(fs.readString("transactionCode").trim())
                    .sequenceNumber(fs.readString("sequenceNumber").trim())
                    .filler(fs.readString("filler").trim())
                    .build();
        };
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static String ensureWidth(String line) {
        if (line.length() < 128) return line + " ".repeat(128 - line.length());
        return line.length() > 128 ? line.substring(0, 128) : line;
    }

    private static String orEmpty(String s) { return s != null ? s : ""; }

    private static String padLeft(String value, int length) {
        if (value == null) value = "";
        if (value.length() >= length) return value.substring(0, length);
        return " ".repeat(length - value.length()) + value;
    }

    private static String padAmount(BigDecimal amount, int length) {
        BigDecimal a = amount != null ? amount : BigDecimal.ZERO;
        String s = a.abs().toBigInteger().toString();
        if (s.length() >= length) return s.substring(0, length);
        return "0".repeat(length - s.length()) + s;
    }

    private static void deleteQuietly(Path p) {
        if (p != null) {
            try { Files.deleteIfExists(p); } catch (IOException ignored) { }
        }
    }
}
```

- [ ] **Step 4: Implement CodaSpringBatchStrategy**

Create `src/main/java/com/wtechitsolutions/strategy/CodaSpringBatchStrategy.java`:

```java
package com.wtechitsolutions.strategy;

import com.wtechitsolutions.domain.Library;
import com.wtechitsolutions.parser.SpringBatchFormatter;
import com.wtechitsolutions.parser.model.CodaRecord;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CodaSpringBatchStrategy extends AbstractCodaStrategy {

    private final SpringBatchFormatter formatter;

    public CodaSpringBatchStrategy(SpringBatchFormatter formatter) {
        this.formatter = formatter;
    }

    @Override
    public Library getLibrary() { return Library.SPRING_BATCH; }

    @Override
    protected String formatRecords(List<CodaRecord> records) {
        return formatter.formatCoda(records);
    }

    @Override
    protected List<CodaRecord> parseRecords(String content) {
        return formatter.parseCoda(content);
    }
}
```

- [ ] **Step 5: Implement SwiftSpringBatchStrategy**

Create `src/main/java/com/wtechitsolutions/strategy/SwiftSpringBatchStrategy.java`:

```java
package com.wtechitsolutions.strategy;

import com.wtechitsolutions.domain.Library;
import com.wtechitsolutions.parser.SpringBatchFormatter;
import com.wtechitsolutions.parser.model.SwiftMtRecord;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SwiftSpringBatchStrategy extends AbstractSwiftStrategy {

    private final SpringBatchFormatter formatter;

    public SwiftSpringBatchStrategy(SpringBatchFormatter formatter) {
        this.formatter = formatter;
    }

    @Override
    public Library getLibrary() { return Library.SPRING_BATCH; }

    @Override
    protected String formatRecords(List<SwiftMtRecord> records) {
        return formatter.formatSwift(records);
    }

    @Override
    protected List<SwiftMtRecord> parseRecords(String content) {
        return formatter.parseSwift(content);
    }
}
```

- [ ] **Step 6: Run all tests**

```bash
mvn test -Pskip-frontend -q
```

Expected: BUILD SUCCESS. All Coda/Swift/Symmetry tests pass for SPRING_BATCH (14 strategies total now).

- [ ] **Step 7: Verify StrategyResolver logs 14 strategies on startup**

```bash
mvn test -Pskip-frontend -Dtest=StrategyResolverTest -q 2>&1 | grep -E "Registered.*strategies"
```

Expected output line: `Registered 14 file generation strategies: [CODA_BEANIO, CODA_FIXFORMAT4J, ...]`

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/wtechitsolutions/parser/SpringBatchFormatter.java \
        src/main/java/com/wtechitsolutions/strategy/CodaSpringBatchStrategy.java \
        src/main/java/com/wtechitsolutions/strategy/SwiftSpringBatchStrategy.java \
        src/main/java/com/wtechitsolutions/domain/Library.java
git commit -m "feat: add Spring Batch native (FlatFileItemWriter+LineAggregator) as a benchmark library participant"
```

---

## Task 4: Velocity HTML benchmark report export

**Files:**
- Create: `src/main/resources/velocity/benchmark-report.vm`
- Create: `src/test/java/com/wtechitsolutions/api/BenchmarkControllerTest.java`
- Modify: `src/main/java/com/wtechitsolutions/benchmark/BenchmarkService.java`
- Modify: `src/main/java/com/wtechitsolutions/api/BenchmarkController.java`

### Step 1: Write the failing test

Create `src/test/java/com/wtechitsolutions/api/BenchmarkControllerTest.java`:

```java
package com.wtechitsolutions.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BenchmarkControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void exportHtml_returns_200_with_text_html_content() throws Exception {
        mockMvc.perform(get("/api/benchmark/export/html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"));
    }

    @Test
    void exportHtml_contains_html_table_structure() throws Exception {
        mockMvc.perform(get("/api/benchmark/export/html"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<table")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Benchmark Report")));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
mvn test -Pskip-frontend -Dtest=BenchmarkControllerTest -q
```

Expected: FAIL — 404 (endpoint does not exist).

- [ ] **Step 3: Create the HTML report Velocity template**

Create `src/main/resources/velocity/benchmark-report.vm`:

```velocity
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <title>Benchmark Report</title>
  <style>
    body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Arial, sans-serif; margin: 2rem; color: #2c3e50; }
    h1 { border-bottom: 2px solid #3498db; padding-bottom: 0.5rem; }
    .meta { color: #7f8c8d; font-size: 0.9em; margin-bottom: 1.5rem; }
    table { border-collapse: collapse; width: 100%; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }
    thead th { background: #2c3e50; color: white; padding: 10px 12px; text-align: left; font-weight: 600; }
    tbody td { padding: 8px 12px; border-bottom: 1px solid #ecf0f1; }
    tbody tr:hover { background: #f8f9fa; }
    .lib-BEANIO        { color: #2980b9; font-weight: 600; }
    .lib-FIXFORMAT4J   { color: #27ae60; font-weight: 600; }
    .lib-FIXEDLENGTH   { color: #d35400; font-weight: 600; }
    .lib-BINDY         { color: #8e44ad; font-weight: 600; }
    .lib-CAMEL_BEANIO  { color: #c0392b; font-weight: 600; }
    .lib-VELOCITY      { color: #16a085; font-weight: 600; }
    .lib-SPRING_BATCH  { color: #f39c12; font-weight: 600; }
    .num { text-align: right; font-variant-numeric: tabular-nums; }
  </style>
</head>
<body>
  <h1>Benchmark Report</h1>
  <p class="meta">Generated: $generatedAt &middot; Records: $count</p>
  <table>
    <thead>
      <tr>
        <th>ID</th>
        <th>File Type</th>
        <th>Library</th>
        <th class="num">Throughput (ops/s)</th>
        <th class="num">Batch (ms)</th>
        <th class="num">Gen (ms)</th>
        <th class="num">Parse (ms)</th>
        <th class="num">Records</th>
        <th class="num">Success Rate</th>
        <th>Timestamp</th>
      </tr>
    </thead>
    <tbody>
#foreach($m in $metrics)
      <tr>
        <td>${m.id}</td>
        <td>${m.fileType}</td>
        <td class="lib-${m.library}">${m.library}</td>
        <td class="num">${m.throughputRps}</td>
        <td class="num">${m.batchDurationMs}</td>
        <td class="num">${m.generationDurationMs}</td>
        <td class="num">${m.parseDurationMs}</td>
        <td class="num">${m.recordsProcessed}</td>
        <td class="num">${m.successRate}</td>
        <td>${m.timestamp}</td>
      </tr>
#end
    </tbody>
  </table>
</body>
</html>
```

- [ ] **Step 4: Add `exportAsHtml()` to BenchmarkService**

Edit `src/main/java/com/wtechitsolutions/benchmark/BenchmarkService.java`. Add these imports near the top:

```java
import jakarta.annotation.PostConstruct;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;

import java.io.StringWriter;
import java.time.Instant;
```

Add a private `VelocityEngine` field, a `@PostConstruct` initialiser, and the new method. The full updated class:

```java
package com.wtechitsolutions.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wtechitsolutions.domain.BenchmarkMetrics;
import com.wtechitsolutions.domain.BenchmarkMetricsRepository;
import jakarta.annotation.PostConstruct;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.StringWriter;
import java.time.Instant;
import java.util.List;

@Service
public class BenchmarkService {

    private static final Logger log = LoggerFactory.getLogger(BenchmarkService.class);
    private static final String HTML_TEMPLATE = "velocity/benchmark-report.vm";

    private final BenchmarkMetricsRepository repository;
    private VelocityEngine velocityEngine;

    public BenchmarkService(BenchmarkMetricsRepository repository) {
        this.repository = repository;
    }

    @PostConstruct
    void initVelocity() {
        velocityEngine = new VelocityEngine();
        velocityEngine.setProperty(RuntimeConstants.RESOURCE_LOADERS, "classpath");
        velocityEngine.setProperty("resource.loader.classpath.class",
                ClasspathResourceLoader.class.getName());
        velocityEngine.init();
    }

    public List<BenchmarkMetrics> getAll() {
        return repository.findTop50ByOrderByTimestampDesc();
    }

    public String exportAsCsv() {
        List<BenchmarkMetrics> metrics = repository.findAll();
        StringBuilder csv = new StringBuilder(
                "id,jobExecutionId,fileType,library,throughputRps,batchDurationMs,generationDurationMs,parseDurationMs,recordsProcessed,successRate,timestamp\n");
        for (BenchmarkMetrics m : metrics) {
            csv.append(String.join(",",
                    str(m.getId()), str(m.getJobExecutionId()), str(m.getFileType()), str(m.getLibrary()),
                    str(m.getThroughputRps()), str(m.getBatchDurationMs()),
                    str(m.getGenerationDurationMs()), str(m.getParseDurationMs()),
                    str(m.getRecordsProcessed()), str(m.getSuccessRate()), str(m.getTimestamp())
            )).append("\n");
        }
        return csv.toString();
    }

    public String exportAsMarkdown() {
        List<BenchmarkMetrics> metrics = repository.findAll();
        StringBuilder md = new StringBuilder(
                "| ID | FileType | Library | Throughput (ops/s) | Batch Duration (ms) | Gen Duration (ms) | Records | Success Rate | Timestamp |\n");
        md.append("|---|---|---|---|---|---|---|---|---|\n");
        for (BenchmarkMetrics m : metrics) {
            md.append(String.format("| %s | %s | %s | %.2f | %s | %s | %s | %.2f | %s |\n",
                    str(m.getId()), str(m.getFileType()), str(m.getLibrary()),
                    m.getThroughputRps() != null ? m.getThroughputRps() : 0.0,
                    str(m.getBatchDurationMs()), str(m.getGenerationDurationMs()),
                    str(m.getRecordsProcessed()),
                    m.getSuccessRate() != null ? m.getSuccessRate() : 0.0,
                    str(m.getTimestamp())));
        }
        return md.toString();
    }

    public String exportAsJson() {
        try {
            ObjectMapper mapper = new ObjectMapper()
                    .registerModule(new JavaTimeModule())
                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(repository.findAll());
        } catch (Exception e) {
            log.error("Failed to serialize benchmark metrics as JSON", e);
            return "[]";
        }
    }

    public String exportAsHtml() {
        try {
            List<BenchmarkMetrics> metrics = repository.findAll();
            VelocityContext context = new VelocityContext();
            context.put("metrics", metrics);
            context.put("count", metrics.size());
            context.put("generatedAt", Instant.now().toString());
            StringWriter sw = new StringWriter();
            velocityEngine.getTemplate(HTML_TEMPLATE).merge(context, sw);
            return sw.toString();
        } catch (Exception e) {
            log.error("Failed to render benchmark HTML report", e);
            return "<html><body><h1>Benchmark Report</h1><p>Rendering failed: "
                    + e.getMessage() + "</p></body></html>";
        }
    }

    private static String str(Object o) {
        return o != null ? o.toString() : "";
    }
}
```

- [ ] **Step 5: Add the `/export/html` endpoint to BenchmarkController**

Edit `src/main/java/com/wtechitsolutions/api/BenchmarkController.java`. Add this method below `exportJson()`:

```java
    @GetMapping("/export/html")
    @Operation(summary = "Export all benchmark results as a styled HTML report (Velocity)")
    public ResponseEntity<String> exportHtml() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "text/html; charset=UTF-8")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"benchmark.html\"")
                .body(benchmarkService.exportAsHtml());
    }
```

- [ ] **Step 6: Run the test to verify it passes**

```bash
mvn test -Pskip-frontend -Dtest=BenchmarkControllerTest -q
```

Expected: PASS. Both test methods green.

- [ ] **Step 7: Run all tests**

```bash
mvn test -Pskip-frontend -q
```

Expected: BUILD SUCCESS. All tests including the new BenchmarkControllerTest pass.

- [ ] **Step 8: Commit**

```bash
git add src/main/resources/velocity/benchmark-report.vm \
        src/main/java/com/wtechitsolutions/benchmark/BenchmarkService.java \
        src/main/java/com/wtechitsolutions/api/BenchmarkController.java \
        src/test/java/com/wtechitsolutions/api/BenchmarkControllerTest.java
git commit -m "feat: add Velocity-driven HTML benchmark report export at /api/benchmark/export/html"
```

---

## Task 5: JMH benchmark expansion — generate + parse for all 14 strategies

**Files:**
- Modify: `src/test/java/com/wtechitsolutions/benchmark/FileGenerationBenchmark.java`

### Step 1: Replace the benchmark class with the expanded version

Open `src/test/java/com/wtechitsolutions/benchmark/FileGenerationBenchmark.java` and replace its entire content with:

```java
package com.wtechitsolutions.benchmark;

import com.wtechitsolutions.domain.Account;
import com.wtechitsolutions.domain.Transaction;
import com.wtechitsolutions.domain.TransactionType;
import com.wtechitsolutions.parser.BeanIOFormatter;
import com.wtechitsolutions.parser.BindyFormatter;
import com.wtechitsolutions.parser.CamelBeanIOFormatter;
import com.wtechitsolutions.parser.FixedFormat4JFormatter;
import com.wtechitsolutions.parser.FixedLengthFormatter;
import com.wtechitsolutions.parser.SpringBatchFormatter;
import com.wtechitsolutions.parser.VelocityFormatter;
import com.wtechitsolutions.strategy.CodaBeanIOStrategy;
import com.wtechitsolutions.strategy.CodaBindyStrategy;
import com.wtechitsolutions.strategy.CodaCamelBeanIOStrategy;
import com.wtechitsolutions.strategy.CodaFixedFormat4JStrategy;
import com.wtechitsolutions.strategy.CodaFixedLengthStrategy;
import com.wtechitsolutions.strategy.CodaSpringBatchStrategy;
import com.wtechitsolutions.strategy.CodaVelocityStrategy;
import com.wtechitsolutions.strategy.FileGenerationStrategy;
import com.wtechitsolutions.strategy.SwiftBeanIOStrategy;
import com.wtechitsolutions.strategy.SwiftBindyStrategy;
import com.wtechitsolutions.strategy.SwiftCamelBeanIOStrategy;
import com.wtechitsolutions.strategy.SwiftFixedFormat4JStrategy;
import com.wtechitsolutions.strategy.SwiftFixedLengthStrategy;
import com.wtechitsolutions.strategy.SwiftSpringBatchStrategy;
import com.wtechitsolutions.strategy.SwiftVelocityStrategy;
import org.junit.jupiter.api.Test;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * JMH micro-benchmarks for all 14 FileGenerationStrategy implementations
 * (7 libraries × 2 formats), covering both generate and parse operations.
 * 28 @Benchmark methods total.
 *
 * Run via: mvn test -Pbenchmark -Pskip-frontend
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 2)
public class FileGenerationBenchmark {

    private List<Transaction> transactions;
    private List<Account> accounts;

    // CODA strategies
    private FileGenerationStrategy codaBeanIO, codaFixedFormat4J, codaFixedLength, codaBindy;
    private FileGenerationStrategy codaCamelBeanIO, codaVelocity, codaSpringBatch;
    // SWIFT strategies
    private FileGenerationStrategy swiftBeanIO, swiftFixedFormat4J, swiftFixedLength, swiftBindy;
    private FileGenerationStrategy swiftCamelBeanIO, swiftVelocity, swiftSpringBatch;

    // Pre-generated files (one per strategy) for parse benchmarks
    private String codaBeanIOFile, codaFf4jFile, codaVlFile, codaBindyFile;
    private String codaCamelBeanIOFile, codaVelocityFile, codaSpringBatchFile;
    private String swiftBeanIOFile, swiftFf4jFile, swiftVlFile, swiftBindyFile;
    private String swiftCamelBeanIOFile, swiftVelocityFile, swiftSpringBatchFile;

    @Setup
    public void setup() {
        accounts = buildAccounts(5);
        transactions = buildTransactions(accounts, 20);

        BeanIOFormatter beanIO = new BeanIOFormatter();
        FixedFormat4JFormatter ff4j = new FixedFormat4JFormatter();
        FixedLengthFormatter vl = new FixedLengthFormatter();
        BindyFormatter bindy = new BindyFormatter();
        bindy.init();
        CamelBeanIOFormatter camelBeanIO = new CamelBeanIOFormatter();
        camelBeanIO.init();
        VelocityFormatter velocity = new VelocityFormatter();
        velocity.init();
        SpringBatchFormatter springBatch = new SpringBatchFormatter();

        codaBeanIO        = new CodaBeanIOStrategy(beanIO);
        codaFixedFormat4J = new CodaFixedFormat4JStrategy(ff4j);
        codaFixedLength   = new CodaFixedLengthStrategy(vl);
        codaBindy         = new CodaBindyStrategy(bindy);
        codaCamelBeanIO   = new CodaCamelBeanIOStrategy(camelBeanIO);
        codaVelocity      = new CodaVelocityStrategy(velocity);
        codaSpringBatch   = new CodaSpringBatchStrategy(springBatch);

        swiftBeanIO        = new SwiftBeanIOStrategy(beanIO);
        swiftFixedFormat4J = new SwiftFixedFormat4JStrategy(ff4j);
        swiftFixedLength   = new SwiftFixedLengthStrategy(vl);
        swiftBindy         = new SwiftBindyStrategy(bindy);
        swiftCamelBeanIO   = new SwiftCamelBeanIOStrategy(camelBeanIO);
        swiftVelocity      = new SwiftVelocityStrategy(velocity);
        swiftSpringBatch   = new SwiftSpringBatchStrategy(springBatch);

        // Pre-generate file content for parse benchmarks
        codaBeanIOFile        = codaBeanIO.generate(transactions, accounts);
        codaFf4jFile          = codaFixedFormat4J.generate(transactions, accounts);
        codaVlFile            = codaFixedLength.generate(transactions, accounts);
        codaBindyFile         = codaBindy.generate(transactions, accounts);
        codaCamelBeanIOFile   = codaCamelBeanIO.generate(transactions, accounts);
        codaVelocityFile      = codaVelocity.generate(transactions, accounts);
        codaSpringBatchFile   = codaSpringBatch.generate(transactions, accounts);

        swiftBeanIOFile        = swiftBeanIO.generate(transactions, accounts);
        swiftFf4jFile          = swiftFixedFormat4J.generate(transactions, accounts);
        swiftVlFile            = swiftFixedLength.generate(transactions, accounts);
        swiftBindyFile         = swiftBindy.generate(transactions, accounts);
        swiftCamelBeanIOFile   = swiftCamelBeanIO.generate(transactions, accounts);
        swiftVelocityFile      = swiftVelocity.generate(transactions, accounts);
        swiftSpringBatchFile   = swiftSpringBatch.generate(transactions, accounts);
    }

    // ── CODA generate ─────────────────────────────────────────────────────────
    @Benchmark public String codaBeanIO()        { return codaBeanIO.generate(transactions, accounts); }
    @Benchmark public String codaFixedFormat4J() { return codaFixedFormat4J.generate(transactions, accounts); }
    @Benchmark public String codaFixedLength()   { return codaFixedLength.generate(transactions, accounts); }
    @Benchmark public String codaBindy()         { return codaBindy.generate(transactions, accounts); }
    @Benchmark public String codaCamelBeanIO()   { return codaCamelBeanIO.generate(transactions, accounts); }
    @Benchmark public String codaVelocity()      { return codaVelocity.generate(transactions, accounts); }
    @Benchmark public String codaSpringBatch()   { return codaSpringBatch.generate(transactions, accounts); }

    // ── SWIFT generate ────────────────────────────────────────────────────────
    @Benchmark public String swiftBeanIO()        { return swiftBeanIO.generate(transactions, accounts); }
    @Benchmark public String swiftFixedFormat4J() { return swiftFixedFormat4J.generate(transactions, accounts); }
    @Benchmark public String swiftFixedLength()   { return swiftFixedLength.generate(transactions, accounts); }
    @Benchmark public String swiftBindy()         { return swiftBindy.generate(transactions, accounts); }
    @Benchmark public String swiftCamelBeanIO()   { return swiftCamelBeanIO.generate(transactions, accounts); }
    @Benchmark public String swiftVelocity()      { return swiftVelocity.generate(transactions, accounts); }
    @Benchmark public String swiftSpringBatch()   { return swiftSpringBatch.generate(transactions, accounts); }

    // ── CODA parse ────────────────────────────────────────────────────────────
    @Benchmark public List<Transaction> codaBeanIOParse()        { return codaBeanIO.parse(codaBeanIOFile); }
    @Benchmark public List<Transaction> codaFixedFormat4JParse() { return codaFixedFormat4J.parse(codaFf4jFile); }
    @Benchmark public List<Transaction> codaFixedLengthParse()   { return codaFixedLength.parse(codaVlFile); }
    @Benchmark public List<Transaction> codaBindyParse()         { return codaBindy.parse(codaBindyFile); }
    @Benchmark public List<Transaction> codaCamelBeanIOParse()   { return codaCamelBeanIO.parse(codaCamelBeanIOFile); }
    @Benchmark public List<Transaction> codaVelocityParse()      { return codaVelocity.parse(codaVelocityFile); }
    @Benchmark public List<Transaction> codaSpringBatchParse()   { return codaSpringBatch.parse(codaSpringBatchFile); }

    // ── SWIFT parse ───────────────────────────────────────────────────────────
    @Benchmark public List<Transaction> swiftBeanIOParse()        { return swiftBeanIO.parse(swiftBeanIOFile); }
    @Benchmark public List<Transaction> swiftFixedFormat4JParse() { return swiftFixedFormat4J.parse(swiftFf4jFile); }
    @Benchmark public List<Transaction> swiftFixedLengthParse()   { return swiftFixedLength.parse(swiftVlFile); }
    @Benchmark public List<Transaction> swiftBindyParse()         { return swiftBindy.parse(swiftBindyFile); }
    @Benchmark public List<Transaction> swiftCamelBeanIOParse()   { return swiftCamelBeanIO.parse(swiftCamelBeanIOFile); }
    @Benchmark public List<Transaction> swiftVelocityParse()      { return swiftVelocity.parse(swiftVelocityFile); }
    @Benchmark public List<Transaction> swiftSpringBatchParse()   { return swiftSpringBatch.parse(swiftSpringBatchFile); }

    @Test
    public void runBenchmarks() throws Exception {
        Options opts = new OptionsBuilder()
                .include(FileGenerationBenchmark.class.getSimpleName())
                .resultFormat(org.openjdk.jmh.results.format.ResultFormatType.JSON)
                .result("target/jmh-result.json")
                .build();
        new Runner(opts).run();
    }

    private static List<Account> buildAccounts(int count) {
        List<Account> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Account a = new Account();
            a.setId((long) (i + 1));
            a.setAccountNumber(String.format("BE%018d", 100L + i));
            a.setIban(String.format("BE%018d", 100L + i));
            a.setBankCode(String.format("%03d", 310 + i));
            a.setCurrency("EUR");
            a.setBalance(BigDecimal.valueOf(10000L + i * 1000L));
            a.setHolderName("Holder " + i);
            a.setCreatedAt(Instant.now());
            list.add(a);
        }
        return list;
    }

    private static List<Transaction> buildTransactions(List<Account> accounts, int perAccount) {
        List<Transaction> list = new ArrayList<>();
        long id = 1;
        for (Account a : accounts) {
            for (int i = 0; i < perAccount; i++) {
                Transaction t = new Transaction();
                t.setId(id++);
                t.setAccountId(a.getId());
                t.setReference(String.format("REF%012d", id));
                t.setAmount(BigDecimal.valueOf(100L + i * 50L));
                t.setType(i % 2 == 0 ? TransactionType.CREDIT : TransactionType.DEBIT);
                t.setDescription("Benchmark transaction " + i);
                t.setValueDate(LocalDate.now().minusDays(i));
                t.setEntryDate(LocalDate.now().minusDays(i));
                t.setCreatedAt(Instant.now());
                list.add(t);
            }
        }
        return list;
    }
}
```

- [ ] **Step 2: Verify the benchmark class compiles**

```bash
mvn test-compile -Pskip-frontend -q
```

Expected: BUILD SUCCESS.

- [ ] **Step 3: Run the full test suite (must still pass)**

```bash
mvn test -Pskip-frontend -q
```

Expected: BUILD SUCCESS. All existing tests still green.

- [ ] **Step 4: Run the benchmark profile (smoke test only — 1 iteration)**

```bash
mvn test -Pbenchmark -Pskip-frontend -q 2>&1 | tail -40
```

Expected: 28 JMH benchmarks complete (look for "28 benchmarks to run" or final "Benchmark" results table with 28 rows). Total run time ~3-5 minutes. Look for the "Benchmark ... Mode Cnt Score Error Units" table.

If JMH reports an error for any benchmark, that strategy's `formatRecords()` is throwing — fix the underlying formatter.

- [ ] **Step 5: Commit**

```bash
git add src/test/java/com/wtechitsolutions/benchmark/FileGenerationBenchmark.java
git commit -m "feat: expand JMH benchmark to 28 methods (7 libraries × 2 formats × generate+parse)"
```

---

## Task 6: Frontend dropdown — add 3 new library options

**Files:**
- Modify: `src/main/frontend/src/api/client.ts`
- Modify: `src/main/frontend/src/views/BatchRunnerView.tsx`

### Step 1: Extend the Library type union in the API client

Edit `src/main/frontend/src/api/client.ts` line 6:

Before:

```typescript
export type Library = 'BEANIO' | 'FIXFORMAT4J' | 'FIXEDLENGTH' | 'BINDY'
```

After:

```typescript
export type Library = 'BEANIO' | 'FIXFORMAT4J' | 'FIXEDLENGTH' | 'BINDY' | 'CAMEL_BEANIO' | 'VELOCITY' | 'SPRING_BATCH'
```

- [ ] **Step 2: Add the new entries to the LIBRARIES array in BatchRunnerView**

Edit `src/main/frontend/src/views/BatchRunnerView.tsx` line 9:

Before:

```typescript
const LIBRARIES: Library[] = ['BEANIO', 'FIXFORMAT4J', 'FIXEDLENGTH', 'BINDY']
```

After:

```typescript
const LIBRARIES: Library[] = ['BEANIO', 'FIXFORMAT4J', 'FIXEDLENGTH', 'BINDY', 'CAMEL_BEANIO', 'VELOCITY', 'SPRING_BATCH']
```

- [ ] **Step 3: Verify TypeScript compiles**

```bash
cd src/main/frontend && npx tsc --noEmit 2>&1 | tail -20; cd ../../..
```

Expected: No type errors (or only pre-existing errors unrelated to this change).

- [ ] **Step 4: Commit**

```bash
git add src/main/frontend/src/api/client.ts src/main/frontend/src/views/BatchRunnerView.tsx
git commit -m "feat(frontend): add CAMEL_BEANIO, VELOCITY, SPRING_BATCH to library dropdown"
```

---

## Task 7: Update README and CLAUDE.md

**Files:**
- Modify: `README.md`
- Modify: `CLAUDE.md`

### Step 1: Update the library comparison table in README.md

Find the table under `## Formatter Library Comparison` and replace it with:

```markdown
| Library                | Version | Grammar Support | Annotation Quality | Spring Batch Fit | Risk   |
|------------------------|---------|-----------------|--------------------|------------------|--------|
| **BeanIO**             | 3.2.1   | Excellent       | Good               | Good             | Low    |
| **fixedformat4j**      | 1.7.0   | Limited         | Excellent          | Excellent        | Low    |
| **fixedlength**        | 0.15    | Limited         | Good               | Good             | Medium |
| **Apache Camel Bindy** | 4.20.0  | Limited         | Good               | Medium           | Medium |
| **Apache Camel BeanIO**| 4.20.0  | Excellent       | XML-based          | Medium           | Medium |
| **Apache Velocity**    | 2.3     | N/A (template)  | N/A                | Low (gen-only)   | Low    |
| **Spring Batch Native**| 5.x     | Excellent       | Programmatic       | Native           | Low    |
```

- [ ] **Step 2: Update the strategy class table in README.md**

Find `### Strategy Pattern` and replace the 8-row table with this 14-row table:

```markdown
| Class                          | Format      | Library                |
|--------------------------------|-------------|------------------------|
| `CodaBeanIOStrategy`           | CODA        | BeanIO                 |
| `CodaFixedFormat4JStrategy`    | CODA        | fixedformat4j          |
| `CodaFixedLengthStrategy`      | CODA        | fixedlength            |
| `CodaBindyStrategy`            | CODA        | Apache Camel Bindy     |
| `CodaCamelBeanIOStrategy`      | CODA        | Apache Camel BeanIO    |
| `CodaVelocityStrategy`         | CODA        | Apache Velocity        |
| `CodaSpringBatchStrategy`      | CODA        | Spring Batch Native    |
| `SwiftBeanIOStrategy`          | SWIFT MT940 | BeanIO                 |
| `SwiftFixedFormat4JStrategy`   | SWIFT MT940 | fixedformat4j          |
| `SwiftFixedLengthStrategy`     | SWIFT MT940 | fixedlength            |
| `SwiftBindyStrategy`           | SWIFT MT940 | Apache Camel Bindy     |
| `SwiftCamelBeanIOStrategy`     | SWIFT MT940 | Apache Camel BeanIO    |
| `SwiftVelocityStrategy`        | SWIFT MT940 | Apache Velocity        |
| `SwiftSpringBatchStrategy`     | SWIFT MT940 | Spring Batch Native    |
```

- [ ] **Step 3: Update the architecture Mermaid diagram in README.md**

Find the Mermaid `subgraph Parsers` block and replace its body:

```mermaid
    subgraph Parsers["Parser Library Wrappers"]
        BIO[BeanIO 3.2.1]
        FF4J[fixedformat4j 1.7.0]
        VL[fixedlength 0.15]
        BINDY[Camel Bindy 4.20.0]
        CBIO[Camel BeanIO 4.20.0]
        VEL[Velocity 2.3]
        SB[Spring Batch native]
    end
```

Also replace the `Strategy Pattern × 8` heading with `Strategy Pattern × 14` and replace:

```mermaid
        C4[4 CODA Strategies]
        S4[4 SWIFT Strategies]
```

with:

```mermaid
        C7[7 CODA Strategies]
        S7[7 SWIFT Strategies]
```

- [ ] **Step 4: Update the REST API table in README.md to include the HTML export endpoint**

Find the REST API table and add this row after the JSON export row:

```markdown
| `GET`  | `/api/benchmark/export/html`     | Export as styled HTML (Velocity template)      |
```

- [ ] **Step 5: Update CLAUDE.md package structure section**

Edit `CLAUDE.md`, find the `└── strategy/` line and replace the strategy listing with:

```
└── strategy/          FileGenerationStrategy interface, StrategyResolver, 14 implementations:
                        AbstractCodaStrategy, AbstractSwiftStrategy (base classes)
                        CodaBeanIOStrategy, CodaFixedFormat4JStrategy, CodaFixedLengthStrategy, CodaBindyStrategy
                        CodaCamelBeanIOStrategy, CodaVelocityStrategy, CodaSpringBatchStrategy
                        SwiftBeanIOStrategy, SwiftFixedFormat4JStrategy, SwiftFixedLengthStrategy, SwiftBindyStrategy
                        SwiftCamelBeanIOStrategy, SwiftVelocityStrategy, SwiftSpringBatchStrategy
```

Also update the parser block:

```
├── parser/            7 formatter wrappers (all annotation- or template-based, no XML for code mapping):
│   │                   BeanIOFormatter, FixedFormat4JFormatter, FixedLengthFormatter, BindyFormatter,
│   │                   CamelBeanIOFormatter, VelocityFormatter, SpringBatchFormatter
│   └── model/         Annotated model classes per library (CodaRecord, BeanIoCodaRecord, etc.)
```

And update the test count reference: `All 76 tests pass` → `All 78+ tests pass`. Update the libraries row in the Technical Stack table:

```
| Libraries | BeanIO 3.2.1, fixedformat4j 1.7.0, fixedlength 0.15, Camel Bindy 4.20.0, Camel BeanIO 4.20.0, Velocity 2.3, Spring Batch 5.x |
```

- [ ] **Step 6: Final full verification**

```bash
mvn test -Pskip-frontend -q
```

Expected: BUILD SUCCESS. All tests pass.

- [ ] **Step 7: Commit**

```bash
git add README.md CLAUDE.md
git commit -m "docs: document Camel BeanIO, Velocity, and Spring Batch native libraries in README and CLAUDE.md"
```

---

## Final Verification

### Task 8: Acceptance verification

- [ ] **Step 1: Confirm 14 strategies are registered**

```bash
mvn test -Pskip-frontend -Dtest=StrategyResolverTest -q 2>&1 | grep "Registered"
```

Expected: `Registered 14 file generation strategies: [...]`

- [ ] **Step 2: Confirm full test suite passes**

```bash
mvn verify -Pskip-frontend -q
```

Expected: BUILD SUCCESS. All tests pass. JaCoCo coverage at or above 40%.

- [ ] **Step 3: Confirm 28 JMH benchmarks run**

```bash
mvn test -Pbenchmark -Pskip-frontend 2>&1 | grep -E "^# Benchmark:" | wc -l
```

Expected: `28`

- [ ] **Step 4: Smoke-test the HTML report endpoint**

```bash
# In one terminal:
mvn spring-boot:run -Pskip-frontend -Dspring-boot.run.profiles=dev

# In another:
curl -s http://localhost:8080/api/benchmark/export/html | head -5
```

Expected: HTML content starting with `<!DOCTYPE html>`.

Kill the server when done: `make kill` or Ctrl+C.

- [ ] **Step 5: Confirm git log shows all 7 commits**

```bash
git log --oneline -n 8
```

Expected: 7 commits added on top of the prior `dc84bfd` design-spec commit.

---

## Notes for Implementer

1. **BeanIO XML mapping vs programmatic**: The existing `BeanIOFormatter` uses programmatic `StreamBuilder` config; `CamelBeanIOFormatter` uses the XML mapping at `src/main/resources/beanio/coda-mapping.xml`. The field layout must remain identical (positions 1–128 across 12 fields). If the XML schema URI `http://www.beanio.org/2012/03` is not recognised by the bundled BeanIO version, drop the `xmlns` attribute — modern BeanIO accepts no-namespace XML.

2. **Velocity 2.x logging**: Velocity 2.x routes logs through SLF4J by default. If `NullLogChute` is unavailable in the bundled jar, just remove the `RUNTIME_LOG_INSTANCE` setProperty call entirely — Velocity will use SLF4J automatically.

3. **Spring Batch temp files**: The `SpringBatchFormatter` writes to `Files.createTempFile(...)` in the system temp directory and deletes after each call. This is correct — `FlatFileItemWriter` requires a `WritableResource` backed by a file. The temp-file I/O is part of what the benchmark measures vs in-memory approaches.

4. **`FormatterLineAggregator` format string**: The `%-1.1s%-3.3s%-10.10s%-37.37s...` format uses `printf` syntax — `%-N.Ns` means left-justified, exactly N chars wide. For the amount column (`%16.16s`), no left-justify because it should be right-aligned/zero-padded (the value is pre-padded in the `FieldExtractor`).

5. **Frontend test running**: The frontend has no unit tests configured in this repo; visual verification via the dev server is sufficient.

6. **JMH benchmark run time**: With 28 benchmarks at 2-warmup × 3-measurement iterations × 2s, expect ~6 minutes total. Reduce iterations during development by editing `@Warmup` / `@Measurement` annotations if needed.
