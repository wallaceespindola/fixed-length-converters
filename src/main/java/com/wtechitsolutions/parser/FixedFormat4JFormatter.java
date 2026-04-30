package com.wtechitsolutions.parser;

import com.ancientprogramming.fixedformat4j.format.FixedFormatManager;
import com.ancientprogramming.fixedformat4j.format.impl.FixedFormatManagerImpl;
import com.wtechitsolutions.parser.model.CodaRecord;
import com.wtechitsolutions.parser.model.Ff4jCodaRecord;
import com.wtechitsolutions.parser.model.SwiftMtRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Formatter wrapping fixedformat4j (1.7.0) for CODA and SWIFT MT record serialisation.
 * Uses annotation-driven mapping via @Record and @Field on Ff4jCodaRecord.
 */
@Component
@Slf4j
public class FixedFormat4JFormatter {

    private final FixedFormatManager manager = new FixedFormatManagerImpl();

    /**
     * Serialises a list of CodaRecord objects to a fixed-length CODA string.
     */
    public String formatCoda(List<CodaRecord> records) {
        StringBuilder sb = new StringBuilder();
        for (CodaRecord record : records) {
            try {
                Ff4jCodaRecord ff4j = toFf4j(record);
                String line = manager.export(ff4j);
                // ensure exactly 128 chars
                if (line.length() < 128) {
                    line = line + " ".repeat(128 - line.length());
                } else if (line.length() > 128) {
                    line = line.substring(0, 128);
                }
                sb.append(line).append("\n");
            } catch (Exception e) {
                log.warn("fixedformat4j export failed for record type={}: {}", record.getRecordType(), e.getMessage());
                // Fall back to CodaRecord's built-in serialiser
                sb.append(record.toFixedWidth()).append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * Parses a fixed-length CODA string back into a list of CodaRecord objects.
     */
    public List<CodaRecord> parseCoda(String content) {
        List<CodaRecord> result = new ArrayList<>();
        String[] lines = content.split("\n");
        for (String line : lines) {
            if (line.isBlank()) continue;
            // Pad or truncate to exactly 128 chars
            if (line.length() < 128) {
                line = line + " ".repeat(128 - line.length());
            } else if (line.length() > 128) {
                line = line.substring(0, 128);
            }
            try {
                Ff4jCodaRecord ff4j = manager.load(Ff4jCodaRecord.class, line);
                result.add(fromFf4j(ff4j));
            } catch (Exception e) {
                log.warn("fixedformat4j parse failed for line: {}", e.getMessage());
                result.add(CodaRecord.fromFixedWidth(line));
            }
        }
        return result;
    }

    /**
     * Serialises a list of SwiftMtRecord objects to SWIFT MT940 tag format.
     * fixedformat4j is not native to tag-based formats; each record is formatted
     * using SwiftMtRecord's built-in toSwiftFormat() after library-level processing.
     */
    public String formatSwift(List<SwiftMtRecord> records) {
        StringBuilder sb = new StringBuilder();
        for (SwiftMtRecord record : records) {
            sb.append(record.toSwiftFormat());
            sb.append("---\n");
        }
        return sb.toString();
    }

    /**
     * Parses SWIFT MT940 content back into a list of SwiftMtRecord objects.
     */
    public List<SwiftMtRecord> parseSwift(String content) {
        List<SwiftMtRecord> result = new ArrayList<>();
        String[] sections = content.split("---\n");
        for (String section : sections) {
            if (section.isBlank()) continue;
            result.add(parseSwiftSection(section));
        }
        return result;
    }

    private Ff4jCodaRecord toFf4j(CodaRecord r) {
        Ff4jCodaRecord ff4j = new Ff4jCodaRecord();
        ff4j.setRecordType(r.getRecordType() != null ? r.getRecordType() : " ");
        ff4j.setBankId(pad(r.getBankId(), 3));
        ff4j.setReferenceNumber(pad(r.getReferenceNumber(), 10));
        ff4j.setAccountNumber(pad(r.getAccountNumber(), 37));
        ff4j.setCurrency(pad(r.getCurrency(), 3));
        ff4j.setAmountStr(padAmount(r.getAmount(), 16));
        ff4j.setEntryDate(pad(r.getEntryDate(), 6));
        ff4j.setValueDate(pad(r.getValueDate(), 6));
        ff4j.setDescription(pad(r.getDescription(), 32));
        ff4j.setTransactionCode(pad(r.getTransactionCode(), 3));
        ff4j.setSequenceNumber(padRight(r.getSequenceNumber(), 4));
        ff4j.setFiller(pad(r.getFiller() != null ? r.getFiller() : "", 7));
        return ff4j;
    }

    private CodaRecord fromFf4j(Ff4jCodaRecord ff4j) {
        BigDecimal amount = parseAmount(ff4j.getAmountStr());
        return CodaRecord.builder()
                .recordType(trim(ff4j.getRecordType()))
                .bankId(trim(ff4j.getBankId()))
                .referenceNumber(trim(ff4j.getReferenceNumber()))
                .accountNumber(trim(ff4j.getAccountNumber()))
                .currency(trim(ff4j.getCurrency()))
                .amount(amount)
                .entryDate(trim(ff4j.getEntryDate()))
                .valueDate(trim(ff4j.getValueDate()))
                .description(trim(ff4j.getDescription()))
                .transactionCode(trim(ff4j.getTransactionCode()))
                .sequenceNumber(trim(ff4j.getSequenceNumber()))
                .filler(trim(ff4j.getFiller()))
                .build();
    }

    private SwiftMtRecord parseSwiftSection(String section) {
        SwiftMtRecord.SwiftMtRecordBuilder builder = SwiftMtRecord.builder();
        Arrays.stream(section.split("\n")).forEach(line -> {
            if (line.startsWith(":20:")) builder.transactionReference(line.substring(4).trim());
            else if (line.startsWith(":25:")) builder.accountIdentification(line.substring(4).trim());
            else if (line.startsWith(":28C:")) builder.statementNumber(line.substring(5).trim());
            else if (line.startsWith(":60F:")) builder.openingBalance(line.substring(5).trim());
            else if (line.startsWith(":61:")) {
                String entry = line.substring(4);
                if (entry.length() >= 10) {
                    builder.valueDate(entry.substring(0, 6));
                    builder.entryDate(entry.substring(6, 10));
                    builder.debitCreditMark(entry.length() > 10 ? String.valueOf(entry.charAt(10)) : "C");
                    int amtEnd = entry.indexOf("NMSC");
                    builder.amount(amtEnd > 11 ? entry.substring(11, amtEnd).trim() : "0,00");
                    builder.transactionType("NMSC");
                    builder.customerReference(amtEnd >= 0 && entry.length() > amtEnd + 4
                            ? entry.substring(amtEnd + 4).trim() : "NONREF");
                }
            } else if (line.startsWith(":86:")) builder.information(line.substring(4).trim());
            else if (line.startsWith(":62F:")) builder.closingBalance(line.substring(5).trim());
        });
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
