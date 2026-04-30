package com.wtechitsolutions.parser.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.beanio.annotation.Field;
import org.beanio.annotation.Record;

/**
 * SWIFT MT940 record model for BeanIO — annotation-driven, no XML required.
 * Serialised as CSV (comma-delimited) since SWIFT MT940 is tag-based, not fixed-width.
 * Each instance represents one complete transaction entry.
 */
@Record
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BeanIoSwiftRecord {

    @Field(at = 0)
    private String transactionReference;

    @Field(at = 1)
    private String accountIdentification;

    @Field(at = 2)
    private String statementNumber;

    @Field(at = 3)
    private String openingBalance;

    @Field(at = 4)
    private String valueDate;

    @Field(at = 5)
    private String entryDate;

    @Field(at = 6)
    private String debitCreditMark;

    @Field(at = 7)
    private String amount;

    @Field(at = 8)
    private String transactionType;

    @Field(at = 9)
    private String customerReference;

    @Field(at = 10)
    private String information;

    @Field(at = 11)
    private String closingBalance;
}
