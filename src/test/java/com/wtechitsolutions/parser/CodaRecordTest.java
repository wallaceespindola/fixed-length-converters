package com.wtechitsolutions.parser;

import com.wtechitsolutions.parser.model.CodaRecord;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies fixed-length CODA roundtrip (format → parse) using the annotation-based
 * fixedformat4j formatter — no manual substring parsing.
 */
class CodaRecordTest {

    private final FixedFormat4JFormatter formatter = new FixedFormat4JFormatter();

    private CodaRecord sample() {
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
    void formatCoda_produces_128_char_lines() {
        String output = formatter.formatCoda(List.of(sample()));
        assertThat(output.lines().filter(l -> !l.isBlank()))
                .allSatisfy(line -> assertThat(line).hasSize(128));
    }

    @Test
    void formatCoda_first_char_is_record_type() {
        String line = formatter.formatCoda(List.of(sample())).lines()
                .filter(l -> !l.isBlank()).findFirst().orElseThrow();
        assertThat(line.charAt(0)).isEqualTo('1');
    }

    @Test
    void roundtrip_preserves_record_type() {
        CodaRecord parsed = roundtrip(sample());
        assertThat(parsed.recordType()).isEqualTo(sample().recordType());
    }

    @Test
    void roundtrip_preserves_currency() {
        assertThat(roundtrip(sample()).currency()).isEqualTo(sample().currency());
    }

    @Test
    void roundtrip_preserves_transaction_code() {
        assertThat(roundtrip(sample()).transactionCode()).isEqualTo(sample().transactionCode());
    }

    @Test
    void roundtrip_preserves_bank_id() {
        assertThat(roundtrip(sample()).bankId()).isEqualTo(sample().bankId());
    }

    @Test
    void parseCoda_empty_content_returns_empty_list() {
        assertThat(formatter.parseCoda("")).isEmpty();
        assertThat(formatter.parseCoda(null)).isEmpty();
    }

    private CodaRecord roundtrip(CodaRecord record) {
        String formatted = formatter.formatCoda(List.of(record));
        return formatter.parseCoda(formatted).get(0);
    }
}
