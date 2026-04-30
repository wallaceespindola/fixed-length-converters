package com.wtechitsolutions.parser.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Represents a single 128-character CODA fixed-length record per Febelfin specification.
 * Used as the shared model across all 4 parser library strategies.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodaRecord {

    /** Record type: 0=header, 1=movement, 2=detail, 8=trailer, 9=end */
    private String recordType;      // pos 1,  len 1
    private String bankId;          // pos 2,  len 3
    private String referenceNumber; // pos 5,  len 10
    private String accountNumber;   // pos 15, len 37
    private String currency;        // pos 52, len 3
    private BigDecimal amount;      // pos 55, len 16
    private String entryDate;       // pos 71, len 6  (DDMMYY)
    private String valueDate;       // pos 77, len 6  (DDMMYY)
    private String description;     // pos 83, len 32
    private String transactionCode; // pos 115, len 3
    private String sequenceNumber;  // pos 118, len 4
    private String filler;          // pos 122, len 7

    /**
     * Serialises this record to a 128-character CODA fixed-width line.
     */
    public String toFixedWidth() {
        String rec = pad(recordType, 1)
                + pad(bankId, 3)
                + pad(referenceNumber, 10)
                + pad(accountNumber, 37)
                + pad(currency, 3)
                + padAmount(amount, 16)
                + pad(entryDate, 6)
                + pad(valueDate, 6)
                + pad(description, 32)
                + pad(transactionCode, 3)
                + padRight(sequenceNumber, 4)
                + pad(filler != null ? filler : "", 7);
        // ensure exactly 128 chars
        if (rec.length() < 128) {
            rec = rec + " ".repeat(128 - rec.length());
        }
        return rec.substring(0, 128);
    }

    /**
     * Parses a 128-character CODA fixed-width line into a CodaRecord.
     */
    public static CodaRecord fromFixedWidth(String line) {
        if (line == null || line.length() < 128) {
            String padded = line != null ? line : "";
            padded = padded + " ".repeat(Math.max(0, 128 - padded.length()));
            line = padded;
        }
        String amountStr = line.substring(54, 70).trim();
        BigDecimal amount;
        try {
            amount = amountStr.isBlank() ? BigDecimal.ZERO : new BigDecimal(amountStr);
        } catch (NumberFormatException e) {
            amount = BigDecimal.ZERO;
        }
        return CodaRecord.builder()
                .recordType(line.substring(0, 1).trim())
                .bankId(line.substring(1, 4).trim())
                .referenceNumber(line.substring(4, 14).trim())
                .accountNumber(line.substring(14, 51).trim())
                .currency(line.substring(51, 54).trim())
                .amount(amount)
                .entryDate(line.substring(70, 76).trim())
                .valueDate(line.substring(76, 82).trim())
                .description(line.substring(82, 114).trim())
                .transactionCode(line.substring(114, 117).trim())
                .sequenceNumber(line.substring(117, 121).trim())
                .filler(line.substring(121, 128).trim())
                .build();
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
}
