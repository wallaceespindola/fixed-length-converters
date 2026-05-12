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
                    .toList();
            VelocityContext context = new VelocityContext();
            context.put("records", padded);
            StringWriter writer = new StringWriter();
            engine.getTemplate(CODA_TEMPLATE).merge(context, writer);
            return writer.toString();
        } catch (Exception e) {
            log.warn("Velocity CODA format failed: {}", e.getMessage());
            return records.stream().map(CodaRecord::toFixedWidth)
                    .collect(java.util.stream.Collectors.joining("\n")) + "\n";
        }
    }

    public List<CodaRecord> parseCoda(String content) {
        if (content == null || content.isBlank()) return List.of();
        return Arrays.stream(content.split("\n"))
                .filter(l -> !l.isBlank())
                .map(CodaRecord::fromFixedWidth)
                .toList();
    }

    public String formatSwift(List<SwiftMtRecord> records) {
        try {
            VelocityContext context = new VelocityContext();
            context.put("records", records.stream()
                    .map(VelocityFormatter::swiftFallbacks)
                    .toList());
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
