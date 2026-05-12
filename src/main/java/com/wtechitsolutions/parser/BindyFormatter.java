package com.wtechitsolutions.parser;

import com.wtechitsolutions.parser.model.BindyCodaRecord;
import com.wtechitsolutions.parser.model.CodaRecord;
import com.wtechitsolutions.parser.model.SwiftMtRecord;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.dataformat.bindy.fixed.BindyFixedLengthDataFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Formatter wrapping Apache Camel Bindy (4.20.0) for CODA and SWIFT MT serialisation.
 * Field layout, padding and alignment are declared via @FixedLengthRecord/@DataField
 * annotations on BindyCodaRecord — no manual string padding in conversion methods.
 */
@Component
public class BindyFormatter {

    private static final Logger log = LoggerFactory.getLogger(BindyFormatter.class);

    private CamelContext camelContext;
    private BindyFixedLengthDataFormat codaFormat;

    @PostConstruct
    public void init() {
        try {
            camelContext = new DefaultCamelContext();
            codaFormat = new BindyFixedLengthDataFormat(BindyCodaRecord.class);
            camelContext.start();
            codaFormat.start();
        } catch (Exception e) {
            log.error("Failed to initialise BindyFormatter: {}", e.getMessage());
            throw new RuntimeException("Camel Bindy initialisation failed", e);
        }
    }

    @PreDestroy
    void shutdown() {
        try {
            if (codaFormat != null) codaFormat.stop();
            if (camelContext != null) camelContext.stop();
        } catch (Exception e) {
            log.warn("BindyFormatter shutdown error: {}", e.getMessage());
        }
    }

    public String formatCoda(List<CodaRecord> records) {
        StringBuilder sb = new StringBuilder();
        for (CodaRecord record : records) {
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                Exchange exchange = new DefaultExchange(camelContext);
                codaFormat.marshal(exchange, List.of(toBindy(record)), out);
                String line = out.toString(StandardCharsets.UTF_8).replace("\r\n", "\n").replace("\r", "\n");
                for (String l : line.split("\n")) {
                    if (!l.isBlank()) sb.append(ensureWidth(l)).append("\n");
                }
            } catch (Exception e) {
                log.warn("Bindy CODA format failed for recordType={}: {}", record.getRecordType(), e.getMessage());
                sb.append(record.toFixedWidth()).append("\n");
            }
        }
        return sb.toString();
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

            if (parsed instanceof List<?> list) {
                return list.stream()
                        .map(item -> {
                            if (item instanceof BindyCodaRecord bcr) return fromBindy(bcr);
                            if (item instanceof Map<?, ?> map) return fromMap((Map<String, Object>) map);
                            return CodaRecord.builder().build();
                        })
                        .collect(Collectors.toList());
            }
            return List.of();
        } catch (Exception e) {
            log.warn("Bindy CODA parse failed: {}", e.getMessage());
            return Arrays.stream(content.split("\n"))
                    .filter(l -> !l.isBlank())
                    .map(CodaRecord::fromFixedWidth)
                    .collect(Collectors.toList());
        }
    }

    public String formatSwift(List<SwiftMtRecord> records) {
        StringBuilder sb = new StringBuilder();
        for (SwiftMtRecord record : records) {
            sb.append(record.toSwiftFormat()).append("---\n");
        }
        return sb.toString();
    }

    public List<SwiftMtRecord> parseSwift(String content) {
        return Arrays.stream(content.split("---\n"))
                .filter(s -> !s.isBlank())
                .map(SwiftMtRecord::fromSwiftSection)
                .toList();
    }

    // ── conversion: raw values only; @DataField annotations handle all padding ─

    private BindyCodaRecord toBindy(CodaRecord r) {
        BindyCodaRecord b = new BindyCodaRecord();
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

    private CodaRecord fromBindy(BindyCodaRecord b) {
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

    @SuppressWarnings("unchecked")
    private CodaRecord fromMap(Map<String, Object> map) {
        return CodaRecord.builder()
                .recordType(str(map.get("recordType")))
                .bankId(str(map.get("bankId")))
                .referenceNumber(str(map.get("referenceNumber")))
                .accountNumber(str(map.get("accountNumber")))
                .currency(str(map.get("currency")))
                .amount(parseAmount(str(map.get("amountStr"))))
                .entryDate(str(map.get("entryDate")))
                .valueDate(str(map.get("valueDate")))
                .description(str(map.get("description")))
                .transactionCode(str(map.get("transactionCode")))
                .sequenceNumber(str(map.get("sequenceNumber")))
                .filler(str(map.get("filler")))
                .build();
    }

    // ── shared read-path helpers ──────────────────────────────────────────────

    private String ensureWidth(String line) {
        if (line.length() < 128) return line + " ".repeat(128 - line.length());
        return line.length() > 128 ? line.substring(0, 128) : line;
    }

    private static String orEmpty(String s) { return s != null ? s : ""; }

    private static String amountToStr(BigDecimal a) {
        return (a != null ? a : BigDecimal.ZERO).abs().toBigInteger().toString();
    }

    private static String trim(String s) { return s != null ? s.trim() : ""; }

    private static String str(Object o) { return o != null ? o.toString().trim() : ""; }

    private static BigDecimal parseAmount(String s) {
        if (s == null || s.isBlank()) return BigDecimal.ZERO;
        try { return new BigDecimal(s.trim()); }
        catch (NumberFormatException e) { return BigDecimal.ZERO; }
    }
}
