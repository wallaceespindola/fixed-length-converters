package com.wtechitsolutions.parser.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.beanio.annotation.Field;
import org.beanio.annotation.Record;
import org.beanio.builder.Align;

/**
 * CODA record model for the BeanIO library using annotations.
 * Mapped using BeanIO @Record and @Field(at=X, length=Y) — no XML required.
 * Total record length: 128 characters per Febelfin CODA specification.
 *
 * Field positions are 0-based in BeanIO (consistent with FieldBuilder.at()).
 */
@Record
@Data
@NoArgsConstructor
public class BeanIoCodaRecord {

    @Field(at = 0, length = 1, trim = true)
    private String recordType;

    @Field(at = 1, length = 3, trim = true)
    private String bankId;

    @Field(at = 4, length = 10, trim = true)
    private String referenceNumber;

    @Field(at = 14, length = 37, trim = true)
    private String accountNumber;

    @Field(at = 51, length = 3, trim = true)
    private String currency;

    /** Amount as zero-padded 16-char string (implied decimal handling in formatter). */
    @Field(at = 54, length = 16, padding = '0', align = Align.RIGHT, trim = true)
    private String amountStr;

    @Field(at = 70, length = 6, trim = true)
    private String entryDate;

    @Field(at = 76, length = 6, trim = true)
    private String valueDate;

    @Field(at = 82, length = 32, trim = true)
    private String description;

    @Field(at = 114, length = 3, trim = true)
    private String transactionCode;

    @Field(at = 117, length = 4, align = Align.RIGHT, trim = true)
    private String sequenceNumber;

    @Field(at = 121, length = 7, trim = true)
    private String filler;
}
