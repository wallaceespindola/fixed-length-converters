package com.wtechitsolutions.parser;

import com.wtechitsolutions.parser.model.BindyCodaRecord;
import com.wtechitsolutions.parser.model.CodaRecord;
import com.wtechitsolutions.parser.model.SwiftMtRecord;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.dataformat.bindy.fixed.BindyFixedLengthDataFormat;
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
 * Uses annotation-driven mapping via @FixedLengthRecord and @DataField on BindyCodaRecord.
 * A minimal CamelContext is managed internally for standalone (non-Camel-Spring) use.
 */
@Component
@Slf4j
public class BindyFormatter {

    private CamelContext camelContext;
    private BindyFixedLengthDataFormat codaFormat;

    @PostConstruct
    void init() {
        try {
            camelContext = new DefaultCamelContext();
            codaFormat = new BindyFixedLengthDataFormat(BindyCodaRecord.class);
            camelContext.start();
            codaFormat.start();
            log.debug("BindyFormatter (Camel Bindy) initialised successfully");
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

    /**
     * Serialises a list of CodaRecord objects using Apache Camel Bindy.
     */
    public String formatCoda(List<CodaRecord> records) {
        StringBuilder sb = new StringBuilder();
        for (CodaRecord record : records) {
            try {
                BindyCodaRecord bindy = toBindy(record);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                Exchange exchange = new DefaultExchange(camelContext);
                codaFormat.marshal(exchange, List.of(bindy), out);
                String line = out.toString(StandardCharsets.UTF_8).replace("\r\n", "\n").replace("\r", "\n");
                // Post-process: ensure exactly 128 chars per line
                for (String l : line.split("\n")) {
                    if (l.isBlank()) continue;
                    if (l.length() < 128) l = l + " ".repeat(128 - l.length());
                    else if (l.length() > 128) l = l.substring(0, 128);
                    sb.append(l).append("\n");
                }
            } catch (Exception e) {
                log.warn("Bindy CODA format failed for recordType={}: {}", record.getRecordType(), e.getMessage());
                sb.append(record.toFixedWidth()).append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * Parses a fixed-length CODA string using Apache Camel Bindy.
     */
    @SuppressWarnings("unchecked")
    public List<CodaRecord> parseCoda(String content) {
        if (content == null || content.isBlank()) return List.of();
        try {
            // Ensure all lines are exactly 128 chars before passing to Bindy
            String paddedContent = Arrays.stream(content.split("\n"))
                    .filter(l -> !l.isBlank())
                    .map(l -> {
                        if (l.length() < 128) return l + " ".repeat(128 - l.length());
                        return l.length() > 128 ? l.substring(0, 128) : l;
                    })
                    .collect(Collectors.joining("\n")) + "\n";

            Exchange exchange = new DefaultExchange(camelContext);
            Object parsed = codaFormat.unmarshal(exchange,
                    new ByteArrayInputStream(paddedContent.getBytes(StandardCharsets.UTF_8)));

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

    /**
     * Serialises SWIFT MT940 records.
     * Camel Bindy is primarily a fixed-length formatter; SWIFT tag format is
     * generated using SwiftMtRecord's built-in serialiser for each record.
     */
    public String formatSwift(List<SwiftMtRecord> records) {
        StringBuilder sb = new StringBuilder();
        for (SwiftMtRecord record : records) {
            sb.append(record.toSwiftFormat());
            sb.append("###\n");
        }
        return sb.toString();
    }

    /**
     * Parses SWIFT MT940 content back into a list of SwiftMtRecord objects.
     */
    public List<SwiftMtRecord> parseSwift(String content) {
        return Arrays.stream(content.split("###\n"))
                .filter(s -> !s.isBlank())
                .map(this::parseSwiftSection)
                .collect(Collectors.toList());
    }

    private BindyCodaRecord toBindy(CodaRecord r) {
        BindyCodaRecord b = new BindyCodaRecord();
        b.setRecordType(r.getRecordType() != null ? r.getRecordType() : " ");
        b.setBankId(pad(r.getBankId(), 3));
        b.setReferenceNumber(pad(r.getReferenceNumber(), 10));
        b.setAccountNumber(pad(r.getAccountNumber(), 37));
        b.setCurrency(pad(r.getCurrency(), 3));
        b.setAmountStr(padAmount(r.getAmount(), 16));
        b.setEntryDate(pad(r.getEntryDate(), 6));
        b.setValueDate(pad(r.getValueDate(), 6));
        b.setDescription(pad(r.getDescription(), 32));
        b.setTransactionCode(pad(r.getTransactionCode(), 3));
        b.setSequenceNumber(padRight(r.getSequenceNumber(), 4));
        b.setFiller(pad(r.getFiller() != null ? r.getFiller() : "", 7));
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

    private SwiftMtRecord parseSwiftSection(String section) {
        SwiftMtRecord.SwiftMtRecordBuilder builder = SwiftMtRecord.builder();
        for (String line : section.split("\n")) {
            if (line.startsWith(":20:")) builder.transactionReference(line.substring(4).trim());
            else if (line.startsWith(":25:")) builder.accountIdentification(line.substring(4).trim());
            else if (line.startsWith(":28C:")) builder.statementNumber(line.substring(5).trim());
            else if (line.startsWith(":60F:")) builder.openingBalance(line.substring(5).trim());
            else if (line.startsWith(":61:")) {
                String entry = line.substring(4);
                if (entry.length() >= 10) {
                    builder.valueDate(entry.substring(0, 6));
                    builder.entryDate(entry.substring(6, 10));
                    builder.debitCreditMark(String.valueOf(entry.charAt(10)));
                    int amtEnd = entry.indexOf("NMSC");
                    builder.amount(amtEnd > 11 ? entry.substring(11, amtEnd).trim() : "0,00");
                    builder.transactionType("NMSC");
                }
            } else if (line.startsWith(":86:")) builder.information(line.substring(4).trim());
            else if (line.startsWith(":62F:")) builder.closingBalance(line.substring(5).trim());
        }
        return builder.build();
    }

    private static String pad(String value, int length) {
        if (value == null) value = "";
        if (value.length() >= length) return value.substring(0, length);
        return value + " ".repeat(length - value.length());
    }

    private static String padRight(String value, int length) {
        if (value == null) value = "";
        if (value.length() >= length) return value.substring(0, length);
        return " ".repeat(length - value.length()) + value;
    }

    private static String padAmount(BigDecimal amount, int length) {
        if (amount == null) amount = BigDecimal.ZERO;
        String s = amount.abs().toPlainString().replace(".", "");
        if (s.length() >= length) return s.substring(0, length);
        return "0".repeat(length - s.length()) + s;
    }

    private static BigDecimal parseAmount(String amountStr) {
        if (amountStr == null || amountStr.isBlank()) return BigDecimal.ZERO;
        try {
            return new BigDecimal(amountStr.trim());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private static String trim(String value) {
        return value != null ? value.trim() : "";
    }

    private static String str(Object o) {
        return o != null ? o.toString().trim() : "";
    }
}
