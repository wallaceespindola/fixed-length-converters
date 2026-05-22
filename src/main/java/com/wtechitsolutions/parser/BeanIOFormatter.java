package com.wtechitsolutions.parser;

import com.wtechitsolutions.parser.model.BeanIoCodaRecord;
import com.wtechitsolutions.parser.model.CodaRecord;
import com.wtechitsolutions.parser.model.SwiftMtRecord;
import org.beanio.BeanReader;
import org.beanio.BeanWriter;
import org.beanio.StreamFactory;
import org.beanio.builder.StreamBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Formatter wrapping BeanIO (3.2.1) for CODA and SWIFT MT serialisation.
 * Uses annotation-based stream config on {@link BeanIoCodaRecord} — no programmatic
 * FieldBuilder required. Field layout is declared via @Record/@Field annotations
 * with 1-based positions, padding and alignment fully annotation-driven.
 */
@Component
public class BeanIOFormatter {

    private static final Logger log = LoggerFactory.getLogger(BeanIOFormatter.class);

    private final StreamFactory factory;

    public BeanIOFormatter() {
        factory = StreamFactory.newInstance();
        factory.define(new StreamBuilder("coda")
                .format("fixedlength")
                .addRecord(BeanIoCodaRecord.class));
    }

    public String formatCoda(List<CodaRecord> records) {
        StringWriter sw = new StringWriter();
        BeanWriter writer = factory.createWriter("coda", sw);
        for (CodaRecord record : records) {
            writer.write(toBeanIo(record));
        }
        writer.flush();
        writer.close();
        return sw.toString();
    }

    public List<CodaRecord> parseCoda(String content) {
        List<CodaRecord> result = new ArrayList<>();
        BeanReader reader = factory.createReader("coda", new StringReader(ensureWidth(content)));
        try {
            Object record;
            while ((record = reader.read()) != null) {
                if (record instanceof BeanIoCodaRecord bcr) {
                    result.add(fromBeanIo(bcr));
                }
            }
        } catch (Exception e) {
            log.warn("BeanIO CODA parse warning: {}", e.getMessage());
        } finally {
            reader.close();
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

    // ── conversion: raw values only; @Field annotation config handles all padding ──

    private BeanIoCodaRecord toBeanIo(CodaRecord r) {
        BeanIoCodaRecord b = new BeanIoCodaRecord();
        b.setRecordType(orEmpty(r.recordType()));
        b.setBankId(orEmpty(r.bankId()));
        b.setReferenceNumber(orEmpty(r.referenceNumber()));
        b.setAccountNumber(orEmpty(r.accountNumber()));
        b.setCurrency(orEmpty(r.currency()));
        b.setAmountStr(amountToStr(r.amount()));
        b.setEntryDate(orEmpty(r.entryDate()));
        b.setValueDate(orEmpty(r.valueDate()));
        b.setDescription(orEmpty(r.description()));
        b.setTransactionCode(orEmpty(r.transactionCode()));
        b.setSequenceNumber(orEmpty(r.sequenceNumber()));
        b.setFiller(orEmpty(r.filler()));
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

    // ── shared read-path helpers ──────────────────────────────────────────────

    /** Ensures every non-blank line is exactly 128 chars (defensive for the read path). */
    private static String ensureWidth(String content) {
        if (content == null || content.isBlank()) return content;
        StringBuilder sb = new StringBuilder();
        for (String line : content.split("\n")) {
            if (line.isBlank()) continue;
            if (line.length() < 128) sb.append(line).append(" ".repeat(128 - line.length()));
            else sb.append(line, 0, 128);
            sb.append("\n");
        }
        return sb.toString();
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
