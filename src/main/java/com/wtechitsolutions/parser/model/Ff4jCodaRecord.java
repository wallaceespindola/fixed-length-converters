package com.wtechitsolutions.parser.model;

import com.ancientprogramming.fixedformat4j.annotation.Align;
import com.ancientprogramming.fixedformat4j.annotation.Field;
import com.ancientprogramming.fixedformat4j.annotation.Record;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * CODA record model for the fixedformat4j library.
 * Fields use offset (1-based) and length annotations.
 * Total record length: 128 characters per Febelfin CODA specification.
 */
@Record(length = 128)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Ff4jCodaRecord {

    @Field(offset = 1, length = 1)
    private String recordType;

    @Field(offset = 2, length = 3)
    private String bankId;

    @Field(offset = 5, length = 10)
    private String referenceNumber;

    @Field(offset = 15, length = 37)
    private String accountNumber;

    @Field(offset = 52, length = 3)
    private String currency;

    /** Amount stored as String (16 chars, zero-padded right-aligned integer). */
    @Field(offset = 55, length = 16, align = Align.RIGHT, paddingChar = '0')
    private String amountStr;

    @Field(offset = 71, length = 6)
    private String entryDate;

    @Field(offset = 77, length = 6)
    private String valueDate;

    @Field(offset = 83, length = 32)
    private String description;

    @Field(offset = 115, length = 3)
    private String transactionCode;

    @Field(offset = 118, length = 4, align = Align.RIGHT)
    private String sequenceNumber;

    @Field(offset = 122, length = 7)
    private String filler;
}
