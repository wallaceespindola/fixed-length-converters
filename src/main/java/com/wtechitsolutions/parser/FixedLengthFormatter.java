package com.wtechitsolutions.parser;

import com.wtechitsolutions.parser.model.CodaRecord;
import com.wtechitsolutions.parser.model.SwiftMtRecord;
import com.wtechitsolutions.parser.model.VlCodaRecord;
import name.velikodniy.vitaliy.fixedlength.FixedLength;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Formatter wrapping the vitaliy fixedlength library (0.15) for CODA and SWIFT MT serialisation.
 * Most field padding is handled by @FixedField annotations; the library (v0.15) does not support
 * custom alignment/fill-char, so amountStr (zero-pad) and sequenceNumber (right-align) are
 * pre-formatted before hand-off.
 */
@Component
public class FixedLengthFormatter {

    private static final Logger log = LoggerFactory.getLogger(FixedLengthFormatter.class);

    private final FixedLength<VlCodaRecord> codaParser;

    public FixedLengthFormatter() {
        codaParser = new FixedLength<VlCodaRecord>().registerLineType(VlCodaRecord.class);
    }

    public String formatCoda(List<CodaRecord> records) {
        List<VlCodaRecord> vlRecords = records.stream().map(this::toVl).collect(Collectors.toList());
        String formatted = codaParser.format(vlRecords);
        return Arrays.stream(formatted.split("\n"))
                .map(this::ensureWidth)
                .collect(Collectors.joining("\n")) + "\n";
    }

    public List<CodaRecord> parseCoda(String content) {
        try {
            String padded = Arrays.stream(content.split("\n"))
                    .map(this::ensureWidth)
                    .collect(Collectors.joining("\n"));
            return codaParser.parse(new java.io.StringReader(padded))
                    .stream().map(this::fromVl).collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("vitaliy fixedlength CODA parse failed: {}", e.getMessage());
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

    // ── conversion ────────────────────────────────────────────────────────────
    // @FixedField handles space-padding for all String fields.
    // v0.15 has no alignment/fillChar support, so amountStr and sequenceNumber
    // are pre-formatted — the only two fields needing non-space padding/alignment.

    private VlCodaRecord toVl(CodaRecord r) {
        VlCodaRecord vl = new VlCodaRecord();
        vl.setRecordType(orEmpty(r.getRecordType()));
        vl.setBankId(orEmpty(r.getBankId()));
        vl.setReferenceNumber(orEmpty(r.getReferenceNumber()));
        vl.setAccountNumber(orEmpty(r.getAccountNumber()));
        vl.setCurrency(orEmpty(r.getCurrency()));
        vl.setAmountStr(zeroPad(amountToStr(r.getAmount()), 16));   // zero-pad: no annotation support
        vl.setEntryDate(orEmpty(r.getEntryDate()));
        vl.setValueDate(orEmpty(r.getValueDate()));
        vl.setDescription(orEmpty(r.getDescription()));
        vl.setTransactionCode(orEmpty(r.getTransactionCode()));
        vl.setSequenceNumber(rightAlign(orEmpty(r.getSequenceNumber()), 4)); // right-align: no annotation support
        vl.setFiller(orEmpty(r.getFiller()));
        return vl;
    }

    private CodaRecord fromVl(VlCodaRecord vl) {
        return CodaRecord.builder()
                .recordType(trim(vl.getRecordType()))
                .bankId(trim(vl.getBankId()))
                .referenceNumber(trim(vl.getReferenceNumber()))
                .accountNumber(trim(vl.getAccountNumber()))
                .currency(trim(vl.getCurrency()))
                .amount(parseAmount(vl.getAmountStr()))
                .entryDate(trim(vl.getEntryDate()))
                .valueDate(trim(vl.getValueDate()))
                .description(trim(vl.getDescription()))
                .transactionCode(trim(vl.getTransactionCode()))
                .sequenceNumber(trim(vl.getSequenceNumber()))
                .filler(trim(vl.getFiller()))
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

    private static String zeroPad(String s, int len) {
        if (s.length() >= len) return s.substring(0, len);
        return "0".repeat(len - s.length()) + s;
    }

    private static String rightAlign(String s, int len) {
        if (s.length() >= len) return s.substring(0, len);
        return " ".repeat(len - s.length()) + s;
    }

    private static String trim(String s) { return s != null ? s.trim() : ""; }

    private static BigDecimal parseAmount(String s) {
        if (s == null || s.isBlank()) return BigDecimal.ZERO;
        try { return new BigDecimal(s.trim()); }
        catch (NumberFormatException e) { return BigDecimal.ZERO; }
    }
}
