package com.wtechitsolutions.parser.model;

import lombok.Builder;

import java.math.BigDecimal;

/**
 * Pure data record for a single 128-character CODA fixed-length entry per Febelfin spec.
 * Serialisation and deserialisation are handled entirely by the annotation-based formatter
 * libraries (BeanIO, fixedformat4j, fixedlength, Camel Bindy, Spring Batch).
 *
 * Record types: 0=header, 1=movement, 2=detail, 8=trailer, 9=end
 */
@Builder
public record CodaRecord(
        String recordType,       // pos  1, len  1
        String bankId,           // pos  2, len  3
        String referenceNumber,  // pos  5, len 10
        String accountNumber,    // pos 15, len 37
        String currency,         // pos 52, len  3
        BigDecimal amount,       // pos 55, len 16
        String entryDate,        // pos 71, len  6  (DDMMYY)
        String valueDate,        // pos 77, len  6  (DDMMYY)
        String description,      // pos 83, len 32
        String transactionCode,  // pos 115, len  3
        String sequenceNumber,   // pos 118, len  4
        String filler            // pos 122, len  7
) {}
