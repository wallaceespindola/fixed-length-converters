package com.wtechitsolutions.parser;

import com.wtechitsolutions.parser.model.CodaRecord;
import com.wtechitsolutions.parser.model.Ff4jCodaRecord;
import com.wtechitsolutions.parser.model.VlCodaRecord;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies that the CODA column layout derived by reflection from the fixedformat4j and
 * fixedlength annotations matches the hand-written {@code Range} slicing in
 * {@link SpringBatchFormatter} — same widths, same output bytes, same round-trip.
 */
class AnnotatedLayoutTest {

    private static final List<String> FIELD_NAMES = List.of(
            "recordType", "bankId", "referenceNumber", "accountNumber", "currency", "amountStr",
            "entryDate", "valueDate", "description", "transactionCode", "sequenceNumber", "filler");

    private static final List<Integer> FIELD_LENGTHS = List.of(1, 3, 10, 37, 3, 16, 6, 6, 32, 3, 4, 7);

    private final SpringBatchFixedFormat4JFormatter ff4j = new SpringBatchFixedFormat4JFormatter();
    private final SpringBatchFixedLengthFormatter fixedLength = new SpringBatchFixedLengthFormatter();
    private final SpringBatchFormatter hardCoded = new SpringBatchFormatter();

    @Test
    void ff4j_annotations_yield_the_coda_layout() {
        assertLayout(AnnotatedLayout.fromFixedFormat4j(Ff4jCodaRecord.class));
    }

    @Test
    void fixedlength_annotations_yield_the_coda_layout() {
        assertLayout(AnnotatedLayout.fromFixedLength(VlCodaRecord.class));
    }

    @Test
    void rejects_a_class_without_layout_annotations() {
        assertThatThrownBy(() -> AnnotatedLayout.fromFixedFormat4j(String.class))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void annotated_layouts_produce_the_same_bytes_as_hand_written_ranges() {
        List<CodaRecord> records = sampleRecords();

        String expected = hardCoded.formatCoda(records);

        assertThat(ff4j.formatCoda(records)).isEqualTo(expected);
        assertThat(fixedLength.formatCoda(records)).isEqualTo(expected);
    }

    @Test
    void annotated_layouts_round_trip_records() {
        Stream.of(ff4j, fixedLength).forEach(formatter -> {
            List<CodaRecord> parsed = formatter.parseCoda(formatter.formatCoda(sampleRecords()));

            assertThat(parsed).hasSize(2);
            assertThat(parsed.get(1).recordType()).isEqualTo("1");
            assertThat(parsed.get(1).amount()).isEqualByComparingTo("500");
            assertThat(parsed.get(1).description()).isEqualTo("Test CODA payment");
            assertThat(parsed.get(1).sequenceNumber()).isEqualTo("0042");
        });
    }

    private static void assertLayout(AnnotatedLayout layout) {
        assertThat(layout.recordLength()).isEqualTo(128);
        assertThat(layout.columns()).extracting(AnnotatedLayout.Column::name)
                .containsExactlyElementsOf(FIELD_NAMES);
        assertThat(layout.columns()).extracting(AnnotatedLayout.Column::length)
                .containsExactlyElementsOf(FIELD_LENGTHS);
        assertThat(layout.columns().get(5).rightAlign()).as("amount is right-aligned").isTrue();
        assertThat(layout.columns().get(5).padChar()).as("amount is zero-padded").isEqualTo('0');
        assertThat(layout.formatString()).startsWith("%-1.1s%-3.3s");
    }

    private static List<CodaRecord> sampleRecords() {
        return List.of(
                CodaRecord.builder()
                        .recordType("0").bankId("310").referenceNumber("HDR")
                        .accountNumber("BE12345678901234").currency("EUR").amount(BigDecimal.ZERO)
                        .entryDate("010126").valueDate("010126").description("CODA HEADER")
                        .transactionCode("000").sequenceNumber("0000").filler("")
                        .build(),
                CodaRecord.builder()
                        .recordType("1").bankId("310").referenceNumber("REF0012345")
                        .accountNumber("BE12345678901234").currency("EUR").amount(new BigDecimal("500"))
                        .entryDate("010126").valueDate("010126").description("Test CODA payment")
                        .transactionCode("001").sequenceNumber("0042").filler("")
                        .build());
    }
}
