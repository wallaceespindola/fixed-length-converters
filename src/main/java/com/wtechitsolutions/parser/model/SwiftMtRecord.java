package com.wtechitsolutions.parser.model;

import lombok.Builder;

/**
 * Represents a single SWIFT MT940/MT942 transaction entry.
 * Contains fields mapping to the standard SWIFT field tags.
 */
@Builder
public record SwiftMtRecord(
        /** :20: Transaction Reference Number, max 16 chars */
        String transactionReference,

        /** :25: Account Identification (IBAN/currency), max 35 chars */
        String accountIdentification,

        /** :28C: Statement Number / Sequence Number e.g. "00001/001" */
        String statementNumber,

        /** :60F: Opening Balance e.g. "C260429EUR1234567,89" */
        String openingBalance,

        /** :61: Value date YYMMDD */
        String valueDate,

        /** :61: Entry date MMDD */
        String entryDate,

        /** :61: Debit/Credit mark — "C" or "D" */
        String debitCreditMark,

        /** :61: Amount with comma as decimal separator */
        String amount,

        /** :61: Swift transaction type code, e.g. "NMSC", "NTRN" */
        String transactionType,

        /** :61: Customer reference, max 16 chars */
        String customerReference,

        /** :86: Additional information / narrative, max 6×65 chars */
        String information,

        /** :62F: Closing Balance e.g. "C260429EUR1234567,89" */
        String closingBalance
) {

    /**
     * Serialises this record to SWIFT MT940 tag format.
     */
    public String toSwiftFormat() {
        StringBuilder sb = new StringBuilder();
        if (transactionReference != null && !transactionReference.isBlank()) {
            sb.append(":20:").append(truncate(transactionReference, 16)).append("\n");
        }
        if (accountIdentification != null && !accountIdentification.isBlank()) {
            sb.append(":25:").append(truncate(accountIdentification, 35)).append("\n");
        }
        if (statementNumber != null && !statementNumber.isBlank()) {
            sb.append(":28C:").append(statementNumber).append("\n");
        }
        if (openingBalance != null && !openingBalance.isBlank()) {
            sb.append(":60F:").append(openingBalance).append("\n");
        }
        // :61: entry line
        if (valueDate != null || amount != null) {
            sb.append(":61:")
              .append(valueDate != null ? valueDate : "000000")
              .append(entryDate != null ? entryDate : "0000")
              .append(debitCreditMark != null ? debitCreditMark : "C")
              .append(amount != null ? amount : "0,00")
              .append(transactionType != null ? transactionType : "NMSC")
              .append(customerReference != null ? truncate(customerReference, 16) : "NONREF")
              .append("\n");
        }
        if (information != null && !information.isBlank()) {
            sb.append(":86:").append(truncate(information, 65)).append("\n");
        }
        if (closingBalance != null && !closingBalance.isBlank()) {
            sb.append(":62F:").append(closingBalance).append("\n");
        }
        return sb.toString();
    }

    /**
     * Parses a single SWIFT MT940 tag-block (one section between record delimiters)
     * into a SwiftMtRecord. Centralised here to avoid duplication across all formatters.
     */
    public static SwiftMtRecord fromSwiftSection(String section) {
        SwiftMtRecordBuilder b = SwiftMtRecord.builder();
        for (String line : section.split("\n")) {
            if (line.startsWith(":20:"))       b.transactionReference(line.substring(4).trim());
            else if (line.startsWith(":25:"))  b.accountIdentification(line.substring(4).trim());
            else if (line.startsWith(":28C:")) b.statementNumber(line.substring(5).trim());
            else if (line.startsWith(":60F:")) b.openingBalance(line.substring(5).trim());
            else if (line.startsWith(":61:")) {
                String entry = line.substring(4);
                if (entry.length() >= 10) {
                    b.valueDate(entry.substring(0, 6));
                    b.entryDate(entry.substring(6, 10));
                    b.debitCreditMark(entry.length() > 10 ? String.valueOf(entry.charAt(10)) : "C");
                    int amtEnd = entry.indexOf("NMSC");
                    b.amount(amtEnd > 11 ? entry.substring(11, amtEnd).trim() : "0,00");
                    b.transactionType("NMSC");
                    b.customerReference(amtEnd >= 0 && entry.length() > amtEnd + 4
                            ? entry.substring(amtEnd + 4).trim() : "NONREF");
                }
            }
            else if (line.startsWith(":86:"))  b.information(line.substring(4).trim());
            else if (line.startsWith(":62F:")) b.closingBalance(line.substring(5).trim());
        }
        return b.build();
    }

    private static String truncate(String value, int maxLen) {
        if (value == null) return "";
        return value.length() > maxLen ? value.substring(0, maxLen) : value;
    }
}
