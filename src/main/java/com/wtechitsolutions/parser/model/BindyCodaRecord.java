package com.wtechitsolutions.parser.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.camel.dataformat.bindy.annotation.DataField;
import org.apache.camel.dataformat.bindy.annotation.FixedLengthRecord;

/**
 * CODA record model for the Apache Camel Bindy library.
 * Uses @FixedLengthRecord for the record length and @DataField for field positions.
 * Total record length: 128 characters per Febelfin CODA specification.
 */
@FixedLengthRecord(length = 128, ignoreMissingChars = true, ignoreTrailingChars = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BindyCodaRecord {

    @DataField(pos = 1, length = 1)
    private String recordType;

    @DataField(pos = 2, length = 3)
    private String bankId;

    @DataField(pos = 5, length = 10)
    private String referenceNumber;

    @DataField(pos = 15, length = 37, align = "L")
    private String accountNumber;

    @DataField(pos = 52, length = 3, align = "L")
    private String currency;

    /** Amount stored as String (16 chars, zero-padded right-aligned integer). */
    @DataField(pos = 55, length = 16, align = "R", paddingChar = '0', trim = true)
    private String amountStr;

    @DataField(pos = 71, length = 6, align = "L", trim = true)
    private String entryDate;

    @DataField(pos = 77, length = 6, align = "L", trim = true)
    private String valueDate;

    @DataField(pos = 83, length = 32, align = "L", trim = true)
    private String description;

    @DataField(pos = 115, length = 3, align = "L", trim = true)
    private String transactionCode;

    @DataField(pos = 118, length = 4, align = "R", trim = true)
    private String sequenceNumber;

    @DataField(pos = 122, length = 7)
    private String filler;
}
