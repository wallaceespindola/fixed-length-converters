package com.wtechitsolutions.parser.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import name.velikodniy.vitaliy.fixedlength.annotation.FixedField;
import name.velikodniy.vitaliy.fixedlength.annotation.FixedLine;

import java.io.Serializable;

/**
 * CODA record model for the vitaliy fixedlength library.
 * Uses @FixedLine to identify the record type and @FixedField for field positions.
 * Total record length: 128 characters per Febelfin CODA specification.
 */
@FixedLine(startsWith = "")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VlCodaRecord implements Serializable {

    @FixedField(offset = 1, length = 1)
    private String recordType;

    @FixedField(offset = 2, length = 3)
    private String bankId;

    @FixedField(offset = 5, length = 10)
    private String referenceNumber;

    @FixedField(offset = 15, length = 37)
    private String accountNumber;

    @FixedField(offset = 52, length = 3)
    private String currency;

    /** Amount stored as String (16 chars, zero-padded right-aligned integer — padded by formatter). */
    @FixedField(offset = 55, length = 16)
    private String amountStr;

    @FixedField(offset = 71, length = 6)
    private String entryDate;

    @FixedField(offset = 77, length = 6)
    private String valueDate;

    @FixedField(offset = 83, length = 32)
    private String description;

    @FixedField(offset = 115, length = 3)
    private String transactionCode;

    @FixedField(offset = 118, length = 4)
    private String sequenceNumber;

    @FixedField(offset = 122, length = 7)
    private String filler;
}
