package com.wtechitsolutions.parser;

import com.ancientprogramming.fixedformat4j.format.FixedFormatManager;
import com.ancientprogramming.fixedformat4j.format.impl.FixedFormatManagerImpl;
import com.wtechitsolutions.parser.model.CodaRecord;
import com.wtechitsolutions.parser.model.Ff4jCodaRecord;
import com.wtechitsolutions.parser.model.SwiftMtRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Formatter wrapping fixedformat4j (1.8.1) for CODA and SWIFT MT record serialisation.
 * Field layout, padding and alignment are declared via @Record/@Field annotations on
 * Ff4jCodaRecord — no manual string padding in conversion methods.
 */
@Component
public class FixedFormat4JFormatter {

    private static final Logger log = LoggerFactory.getLogger(FixedFormat4JFormatter.class);

    private final FixedFormatManager manager = new FixedFormatManagerImpl();

    public String formatCoda(List<CodaRecord> records) {
        StringBuilder sb = new StringBuilder();
        for (CodaRecord record : records) {
            try {
                sb.append(manager.export(toFf4j(record))).append("\n");
            } catch (Exception e) {
                log.warn("fixedformat4j export failed for record type={}: {}", record.getRecordType(), e.getMessage());
                sb.append(record.toFixedWidth()).append("\n");
            }
        }
        return sb.toString();
    }

    public List<CodaRecord> parseCoda(String content) {
        List<CodaRecord> result = new ArrayList<>();
        for (String line : content.split("\n")) {
            if (line.isBlank()) continue;
            String padded = ensureWidth(line);
            try {
                result.add(fromFf4j(manager.load(Ff4jCodaRecord.class, padded)));
            } catch (Exception e) {
                log.warn("fixedformat4j parse failed for line: {}", e.getMessage());
                result.add(CodaRecord.fromFixedWidth(padded));
            }
        }
        return result;
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

    // ── conversion: raw values only; @Field annotations handle all padding ────

    private Ff4jCodaRecord toFf4j(CodaRecord r) {
        Ff4jCodaRecord ff4j = new Ff4jCodaRecord();
        ff4j.setRecordType(orEmpty(r.getRecordType()));
        ff4j.setBankId(orEmpty(r.getBankId()));
        ff4j.setReferenceNumber(orEmpty(r.getReferenceNumber()));
        ff4j.setAccountNumber(orEmpty(r.getAccountNumber()));
        ff4j.setCurrency(orEmpty(r.getCurrency()));
        ff4j.setAmountStr(amountToStr(r.getAmount()));
        ff4j.setEntryDate(orEmpty(r.getEntryDate()));
        ff4j.setValueDate(orEmpty(r.getValueDate()));
        ff4j.setDescription(orEmpty(r.getDescription()));
        ff4j.setTransactionCode(orEmpty(r.getTransactionCode()));
        ff4j.setSequenceNumber(orEmpty(r.getSequenceNumber()));
        ff4j.setFiller(orEmpty(r.getFiller()));
        return ff4j;
    }

    private CodaRecord fromFf4j(Ff4jCodaRecord ff4j) {
        return CodaRecord.builder()
                .recordType(trim(ff4j.getRecordType()))
                .bankId(trim(ff4j.getBankId()))
                .referenceNumber(trim(ff4j.getReferenceNumber()))
                .accountNumber(trim(ff4j.getAccountNumber()))
                .currency(trim(ff4j.getCurrency()))
                .amount(parseAmount(ff4j.getAmountStr()))
                .entryDate(trim(ff4j.getEntryDate()))
                .valueDate(trim(ff4j.getValueDate()))
                .description(trim(ff4j.getDescription()))
                .transactionCode(trim(ff4j.getTransactionCode()))
                .sequenceNumber(trim(ff4j.getSequenceNumber()))
                .filler(trim(ff4j.getFiller()))
                .build();
    }

    // ── shared read-path helpers ──────────────────────────────────────────────

    private static String ensureWidth(String line) {
        if (line.length() < 128) return line + " ".repeat(128 - line.length());
        return line.length() > 128 ? line.substring(0, 128) : line;
    }

    private static String orEmpty(String s) { return s != null ? s : ""; }

    private static String amountToStr(BigDecimal a) {
        return (a != null ? a : BigDecimal.ZERO).abs().toBigInteger().toString();
    }

    private static String trim(String s) { return s != null ? s.trim() : ""; }

    private static BigDecimal parseAmount(String s) {
        if (s == null || s.isBlank()) return BigDecimal.ZERO;
        try { return new BigDecimal(s.trim()); }
        catch (NumberFormatException e) { return BigDecimal.ZERO; }
    }
}
