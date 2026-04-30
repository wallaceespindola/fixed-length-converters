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
 * Uses annotation-driven mapping via @FixedLine and @FixedField on VlCodaRecord.
 */
@Component
public class FixedLengthFormatter {

    private static final Logger log = LoggerFactory.getLogger(FixedLengthFormatter.class);

    private final FixedLength<VlCodaRecord> codaParser;

    public FixedLengthFormatter() {
        codaParser = new FixedLength<VlCodaRecord>()
                .registerLineType(VlCodaRecord.class);
    }

    /**
     * Serialises a list of CodaRecord objects using the vitaliy fixedlength library.
     */
    public String formatCoda(List<CodaRecord> records) {
        List<VlCodaRecord> vlRecords = records.stream().map(this::toVl).collect(Collectors.toList());
        String formatted = codaParser.format(vlRecords);
        // Post-process: ensure every line is exactly 128 chars
        return Arrays.stream(formatted.split("\n"))
                .map(line -> {
                    if (line.length() < 128) return line + " ".repeat(128 - line.length());
                    return line.length() > 128 ? line.substring(0, 128) : line;
                })
                .collect(Collectors.joining("\n")) + "\n";
    }

    /**
     * Parses a fixed-length CODA string using the vitaliy fixedlength library.
     */
    public List<CodaRecord> parseCoda(String content) {
        try {
            // vitaliy library reads line by line
            String paddedContent = Arrays.stream(content.split("\n"))
                    .map(line -> {
                        if (line.length() < 128) return line + " ".repeat(128 - line.length());
                        return line.length() > 128 ? line.substring(0, 128) : line;
                    })
                    .collect(Collectors.joining("\n"));

            List<VlCodaRecord> vlRecords = codaParser.parse(
                    new java.io.StringReader(paddedContent));
            return vlRecords.stream().map(this::fromVl).collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("vitaliy fixedlength CODA parse failed: {}", e.getMessage());
            return Arrays.stream(content.split("\n"))
                    .filter(l -> !l.isBlank())
                    .map(CodaRecord::fromFixedWidth)
                    .collect(Collectors.toList());
        }
    }

    /**
     * Serialises SWIFT MT940 records. Since MT940 is tag-based (not fixed-width),
     * each record's built-in toSwiftFormat() is used after vitaliy processes the data.
     */
    public String formatSwift(List<SwiftMtRecord> records) {
        StringBuilder sb = new StringBuilder();
        for (SwiftMtRecord record : records) {
            sb.append(record.toSwiftFormat());
            sb.append("===\n");
        }
        return sb.toString();
    }

    /**
     * Parses SWIFT MT940 content back into a list of SwiftMtRecord objects.
     */
    public List<SwiftMtRecord> parseSwift(String content) {
        return Arrays.stream(content.split("===\n"))
                .filter(s -> !s.isBlank())
                .map(this::parseSwiftSection)
                .collect(Collectors.toList());
    }

    private VlCodaRecord toVl(CodaRecord r) {
        VlCodaRecord vl = new VlCodaRecord();
        vl.setRecordType(r.getRecordType() != null ? r.getRecordType() : " ");
        vl.setBankId(pad(r.getBankId(), 3));
        vl.setReferenceNumber(pad(r.getReferenceNumber(), 10));
        vl.setAccountNumber(pad(r.getAccountNumber(), 37));
        vl.setCurrency(pad(r.getCurrency(), 3));
        vl.setAmountStr(padAmount(r.getAmount(), 16));
        vl.setEntryDate(pad(r.getEntryDate(), 6));
        vl.setValueDate(pad(r.getValueDate(), 6));
        vl.setDescription(pad(r.getDescription(), 32));
        vl.setTransactionCode(pad(r.getTransactionCode(), 3));
        vl.setSequenceNumber(padRight(r.getSequenceNumber(), 4));
        vl.setFiller(pad(r.getFiller() != null ? r.getFiller() : "", 7));
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
        String s = amount.abs().toBigInteger().toString();
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
}
