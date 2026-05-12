package com.wtechitsolutions.parser.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Arrays;

/**
 * Represents a single SWIFT MT940/MT942 transaction entry.
 * Contains fields mapping to the standard SWIFT field tags.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SwiftMtRecord {

    /** :20: Transaction Reference Number, max 16 chars */
    private String transactionReference;

    /** :25: Account Identification (IBAN/currency), max 35 chars */
    private String accountIdentification;

    /** :28C: Statement Number / Sequence Number e.g. "00001/001" */
    private String statementNumber;

    /** :60F: Opening Balance e.g. "C260429EUR1234567,89" */
    private String openingBalance;

    /** :61: Value date YYMMDD */
    private String valueDate;

    /** :61: Entry date MMDD */
    private String entryDate;

    /** :61: Debit/Credit mark — "C" or "D" */
    private String debitCreditMark;

    /** :61: Amount with comma as decimal separator */
    private String amount;

    /** :61: Swift transaction type code, e.g. "NMSC", "NTRN" */
    private String transactionType;

    /** :61: Customer reference, max 16 chars */
    private String customerReference;

    /** :86: Additional information / narrative, max 6×65 chars */
    private String information;

    /** :62F: Closing Balance e.g. "C260429EUR1234567,89" */
    private String closingBalance;

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
        SwiftMtRecord r = new SwiftMtRecord();
        for (String line : section.split("\n")) {
            if (line.startsWith(":20:"))       r.setTransactionReference(line.substring(4).trim());
            else if (line.startsWith(":25:"))  r.setAccountIdentification(line.substring(4).trim());
            else if (line.startsWith(":28C:")) r.setStatementNumber(line.substring(5).trim());
            else if (line.startsWith(":60F:")) r.setOpeningBalance(line.substring(5).trim());
            else if (line.startsWith(":61:")) {
                String entry = line.substring(4);
                if (entry.length() >= 10) {
                    r.setValueDate(entry.substring(0, 6));
                    r.setEntryDate(entry.substring(6, 10));
                    r.setDebitCreditMark(entry.length() > 10 ? String.valueOf(entry.charAt(10)) : "C");
                    int amtEnd = entry.indexOf("NMSC");
                    r.setAmount(amtEnd > 11 ? entry.substring(11, amtEnd).trim() : "0,00");
                    r.setTransactionType("NMSC");
                    r.setCustomerReference(amtEnd >= 0 && entry.length() > amtEnd + 4
                            ? entry.substring(amtEnd + 4).trim() : "NONREF");
                }
            }
            else if (line.startsWith(":86:"))  r.setInformation(line.substring(4).trim());
            else if (line.startsWith(":62F:")) r.setClosingBalance(line.substring(5).trim());
        }
        return r;
    }

    private static String truncate(String value, int maxLen) {
        if (value == null) return "";
        return value.length() > maxLen ? value.substring(0, maxLen) : value;
    }
}
