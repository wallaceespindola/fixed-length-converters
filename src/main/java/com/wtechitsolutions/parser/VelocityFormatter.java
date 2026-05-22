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
import org.springframework.batch.item.file.mapping.FieldSetMapper;
import org.springframework.batch.item.file.transform.FixedLengthTokenizer;
import org.springframework.batch.item.file.transform.Range;
import org.springframework.stereotype.Component;

import java.io.StringWriter;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Formatter using Apache Velocity 2.4.1 templates.
 *
 * <p>CODA write: pre-pads field maps and merges them into {@code velocity/coda-record.vm}.
 * CODA read:  Velocity is a write-only engine; the read path uses Spring Batch
 *             {@link FixedLengthTokenizer} — a framework utility, not another formatter.
 *
 * <p>SWIFT write/read: tag-based format rendered/parsed via {@link SwiftMtRecord} helpers,
 * consistent with every other SWIFT strategy in the codebase.
 */
@Component
public class VelocityFormatter {

    private static final Logger log = LoggerFactory.getLogger(VelocityFormatter.class);
    private static final String CODA_TEMPLATE  = "velocity/coda-record.vm";
    private static final String SWIFT_TEMPLATE = "velocity/swift-record.vm";

    private VelocityEngine engine;
    private final FixedLengthTokenizer codaTokenizer;
    private final FieldSetMapper<CodaRecord> codaFieldSetMapper;

    public VelocityFormatter() {
        this.codaTokenizer    = buildTokenizer();
        this.codaFieldSetMapper = buildFieldSetMapper();
    }

    @PostConstruct
    public void init() {
        engine = new VelocityEngine();
        engine.setProperty(RuntimeConstants.RESOURCE_LOADERS, "classpath");
        engine.setProperty("resource.loader.classpath.class",
                ClasspathResourceLoader.class.getName());
        engine.init();
    }

    // ── CODA ─────────────────────────────────────────────────────────────────

    public String formatCoda(List<CodaRecord> records) {
        try {
            VelocityContext ctx = new VelocityContext();
            ctx.put("records", records.stream().map(VelocityFormatter::toPaddedMap).toList());
            StringWriter writer = new StringWriter();
            engine.getTemplate(CODA_TEMPLATE).merge(ctx, writer);
            return writer.toString();
        } catch (Exception e) {
            log.warn("Velocity CODA format failed: {}", e.getMessage());
            return "";
        }
    }

    public List<CodaRecord> parseCoda(String content) {
        if (content == null || content.isBlank()) return List.of();
        return Arrays.stream(content.replace("\r\n", "\n").split("\n"))
                .filter(l -> !l.isBlank())
                .map(VelocityFormatter::ensureWidth)
                .map(line -> {
                    try {
                        return codaFieldSetMapper.mapFieldSet(codaTokenizer.tokenize(line));
                    } catch (Exception e) {
                        log.warn("Velocity CODA tokenize failed for line, skipping: {}", e.getMessage());
                        return null;
                    }
                })
                .filter(r -> r != null)
                .toList();
    }

    // ── SWIFT ─────────────────────────────────────────────────────────────────

    public String formatSwift(List<SwiftMtRecord> records) {
        try {
            VelocityContext ctx = new VelocityContext();
            ctx.put("records", records.stream().map(VelocityFormatter::withDefaults).toList());
            StringWriter writer = new StringWriter();
            engine.getTemplate(SWIFT_TEMPLATE).merge(ctx, writer);
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

    // ── write helpers ─────────────────────────────────────────────────────────

    private static Map<String, String> toPaddedMap(CodaRecord r) {
        Map<String, String> m = new HashMap<>();
        m.put("recordType",      padRight(r.recordType(), 1));
        m.put("bankId",          padRight(r.bankId(), 3));
        m.put("referenceNumber", padRight(r.referenceNumber(), 10));
        m.put("accountNumber",   padRight(r.accountNumber(), 37));
        m.put("currency",        padRight(r.currency(), 3));
        m.put("amountStr",       padAmount(r.amount(), 16));
        m.put("entryDate",       padRight(r.entryDate(), 6));
        m.put("valueDate",       padRight(r.valueDate(), 6));
        m.put("description",     padRight(r.description(), 32));
        m.put("transactionCode", padRight(r.transactionCode(), 3));
        m.put("sequenceNumber",  padLeft(r.sequenceNumber(), 4));
        m.put("filler",          padRight(r.filler(), 7));
        return m;
    }

    private static SwiftMtRecord withDefaults(SwiftMtRecord r) {
        return SwiftMtRecord.builder()
                .transactionReference(orEmpty(r.transactionReference()))
                .accountIdentification(orEmpty(r.accountIdentification()))
                .statementNumber(orEmpty(r.statementNumber()))
                .openingBalance(orEmpty(r.openingBalance()))
                .valueDate(or(r.valueDate(), "000000"))
                .entryDate(or(r.entryDate(), "0000"))
                .debitCreditMark(or(r.debitCreditMark(), "C"))
                .amount(or(r.amount(), "0,00"))
                .transactionType(or(r.transactionType(), "NMSC"))
                .customerReference(or(r.customerReference(), "NONREF"))
                .information(orEmpty(r.information()))
                .closingBalance(orEmpty(r.closingBalance()))
                .build();
    }

    // ── parse components ──────────────────────────────────────────────────────

    private static FixedLengthTokenizer buildTokenizer() {
        FixedLengthTokenizer t = new FixedLengthTokenizer();
        t.setColumns(
                new Range(1,   1),   // recordType
                new Range(2,   4),   // bankId
                new Range(5,   14),  // referenceNumber
                new Range(15,  51),  // accountNumber
                new Range(52,  54),  // currency
                new Range(55,  70),  // amountStr
                new Range(71,  76),  // entryDate
                new Range(77,  82),  // valueDate
                new Range(83,  114), // description
                new Range(115, 117), // transactionCode
                new Range(118, 121), // sequenceNumber
                new Range(122, 128)  // filler
        );
        t.setNames("recordType", "bankId", "referenceNumber", "accountNumber",
                "currency", "amountStr", "entryDate", "valueDate", "description",
                "transactionCode", "sequenceNumber", "filler");
        t.setStrict(false);
        return t;
    }

    private static FieldSetMapper<CodaRecord> buildFieldSetMapper() {
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

    // ── static helpers ────────────────────────────────────────────────────────

    private static String ensureWidth(String line) {
        if (line.length() < 128) return line + " ".repeat(128 - line.length());
        return line.length() > 128 ? line.substring(0, 128) : line;
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
