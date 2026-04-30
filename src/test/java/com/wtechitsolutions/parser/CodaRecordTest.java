package com.wtechitsolutions.parser;

import com.wtechitsolutions.parser.model.CodaRecord;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class CodaRecordTest {

    private CodaRecord sampleRecord() {
        return CodaRecord.builder()
                .recordType("1")
                .bankId("310")
                .referenceNumber("REF0000001")
                .accountNumber("BE68539007547034         ")
                .currency("EUR")
                .amount(new BigDecimal("500"))
                .entryDate("290426")
                .valueDate("290426")
                .description("Test payment        ")
                .transactionCode("001")
                .sequenceNumber("0001")
                .filler("       ")
                .build();
    }

    @Test
    void toFixedWidth_produces_128_char_line() {
        String line = sampleRecord().toFixedWidth();
        assertThat(line).hasSize(128);
    }

    @Test
    void toFixedWidth_starts_with_record_type() {
        String line = sampleRecord().toFixedWidth();
        assertThat(line.charAt(0)).isEqualTo('1');
    }

    @Test
    void fromFixedWidth_roundtrip_preserves_record_type() {
        CodaRecord original = sampleRecord();
        String line = original.toFixedWidth();
        CodaRecord parsed = CodaRecord.fromFixedWidth(line);
        assertThat(parsed.getRecordType()).isEqualTo(original.getRecordType());
    }

    @Test
    void fromFixedWidth_roundtrip_preserves_currency() {
        CodaRecord original = sampleRecord();
        String line = original.toFixedWidth();
        CodaRecord parsed = CodaRecord.fromFixedWidth(line);
        assertThat(parsed.getCurrency()).isEqualTo(original.getCurrency());
    }

    @Test
    void fromFixedWidth_roundtrip_preserves_transaction_code() {
        CodaRecord original = sampleRecord();
        String line = original.toFixedWidth();
        CodaRecord parsed = CodaRecord.fromFixedWidth(line);
        assertThat(parsed.getTransactionCode()).isEqualTo(original.getTransactionCode());
    }

    @Test
    void fromFixedWidth_handles_short_input_without_exception() {
        CodaRecord result = CodaRecord.fromFixedWidth("1310");
        assertThat(result).isNotNull();
        assertThat(result.getRecordType()).isEqualTo("1");
    }

    @Test
    void fromFixedWidth_handles_null_without_exception() {
        CodaRecord result = CodaRecord.fromFixedWidth(null);
        assertThat(result).isNotNull();
    }
}
