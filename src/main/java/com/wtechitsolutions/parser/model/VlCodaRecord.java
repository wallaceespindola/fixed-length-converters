package com.wtechitsolutions.parser.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import name.velikodniy.vitaliy.fixedlength.Align;
import name.velikodniy.vitaliy.fixedlength.annotation.FixedField;
import name.velikodniy.vitaliy.fixedlength.annotation.FixedLine;

import java.io.Serializable;

/**
 * CODA record model for the vitaliy fixedlength library.
 * Uses @FixedLine to identify the record type and @FixedField for field positions.
 * Total record length: 128 characters per Febelfin CODA specification.
 *
 * <p>Alignment is declared explicitly on every field: {@code @FixedField} defaults to
 * {@link Align#RIGHT}, whereas CODA text fields are left-aligned and only the amount and
 * sequence number are right-aligned. The annotations are the single source of truth for the
 * layout — {@link com.wtechitsolutions.parser.SpringBatchFixedLengthFormatter} derives its
 * Spring Batch tokenizer ranges and aggregator format string from them by reflection.</p>
 */
@FixedLine(startsWith = "")
@Data
@NoArgsConstructor
public class VlCodaRecord implements Serializable {

    @FixedField(offset = 1, length = 1, align = Align.LEFT)
    private String recordType;

    @FixedField(offset = 2, length = 3, align = Align.LEFT)
    private String bankId;

    @FixedField(offset = 5, length = 10, align = Align.LEFT)
    private String referenceNumber;

    @FixedField(offset = 15, length = 37, align = Align.LEFT)
    private String accountNumber;

    @FixedField(offset = 52, length = 3, align = Align.LEFT)
    private String currency;

    /** Amount stored as String (16 chars, zero-padded right-aligned integer). */
    @FixedField(offset = 55, length = 16, align = Align.RIGHT, padding = '0')
    private String amountStr;

    @FixedField(offset = 71, length = 6, align = Align.LEFT)
    private String entryDate;

    @FixedField(offset = 77, length = 6, align = Align.LEFT)
    private String valueDate;

    @FixedField(offset = 83, length = 32, align = Align.LEFT)
    private String description;

    @FixedField(offset = 115, length = 3, align = Align.LEFT)
    private String transactionCode;

    @FixedField(offset = 118, length = 4, align = Align.RIGHT)
    private String sequenceNumber;

    @FixedField(offset = 122, length = 7, align = Align.LEFT)
    private String filler;
}
