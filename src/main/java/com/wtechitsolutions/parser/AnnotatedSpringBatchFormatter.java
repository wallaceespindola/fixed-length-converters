package com.wtechitsolutions.parser;

import com.wtechitsolutions.parser.AnnotatedLayout.Column;
import com.wtechitsolutions.parser.model.CodaRecord;
import com.wtechitsolutions.parser.model.SwiftMtRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.file.mapping.FieldSetMapper;
import org.springframework.batch.item.file.transform.FieldExtractor;
import org.springframework.batch.item.file.transform.FixedLengthTokenizer;
import org.springframework.batch.item.file.transform.FormatterLineAggregator;
import org.springframework.batch.item.file.transform.LineAggregator;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Spring Batch flat-file formatter whose column layout is derived from model annotations
 * rather than hand-written {@link org.springframework.batch.item.file.transform.Range} literals.
 *
 * <p>Same Spring Batch machinery as {@link SpringBatchFormatter} — {@link FormatterLineAggregator}
 * on the write path, {@link FixedLengthTokenizer} + {@link FieldSetMapper} on the read path — but
 * the ranges, widths, alignment and padding characters are read once from an annotated model
 * ({@code Ff4jCodaRecord} or {@code VlCodaRecord}) via {@link AnnotatedLayout}. Subclasses pick
 * the annotation dialect; there is no second copy of the CODA slicing to keep in sync.</p>
 *
 * <p>SWIFT follows every other strategy: {@link SwiftMtRecord#toSwiftFormat()} /
 * {@link SwiftMtRecord#fromSwiftSection} with {@code ---} section delimiters.</p>
 *
 * @author Wallace Espindola, wallace.espindola@gmail.com
 */
public abstract class AnnotatedSpringBatchFormatter {

    private static final Logger log = LoggerFactory.getLogger(AnnotatedSpringBatchFormatter.class);

    /** CODA record width per the Febelfin specification. */
    private static final int CODA_LENGTH = 128;

    private final AnnotatedLayout layout;
    private final LineAggregator<CodaRecord> aggregator;
    private final FixedLengthTokenizer tokenizer;
    private final FieldSetMapper<CodaRecord> fieldSetMapper;

    protected AnnotatedSpringBatchFormatter(AnnotatedLayout layout) {
        if (layout.recordLength() != CODA_LENGTH) {
            throw new IllegalStateException("Annotated CODA layout is " + layout.recordLength()
                    + " chars, expected " + CODA_LENGTH);
        }
        this.layout = layout;
        this.aggregator = buildAggregator(layout);
        this.tokenizer = layout.tokenizer();
        this.fieldSetMapper = AnnotatedSpringBatchFormatter::mapRecord;
    }

    /**
     * Serialises CODA records with the annotation-derived format string.
     *
     * @return multi-line string; each non-blank line is exactly 128 characters
     */
    public String formatCoda(List<CodaRecord> records) {
        try {
            return records.stream()
                    .map(aggregator::aggregate)
                    .collect(Collectors.joining("\n")) + "\n";
        } catch (Exception e) {
            log.warn("Annotated Spring Batch CODA format failed: {}", e.getMessage());
            return "";
        }
    }

    /**
     * Parses CODA content with the annotation-derived tokenizer. Unparseable lines are skipped.
     */
    public List<CodaRecord> parseCoda(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        return Arrays.stream(content.replace("\r\n", "\n").replace("\r", "\n").split("\n"))
                .filter(l -> !l.isBlank())
                .map(AnnotatedSpringBatchFormatter::ensureWidth)
                .map(this::tokenizeLine)
                .filter(r -> r != null)
                .toList();
    }

    public String formatSwift(List<SwiftMtRecord> records) {
        StringBuilder sb = new StringBuilder();
        for (SwiftMtRecord r : records) {
            sb.append(r.toSwiftFormat()).append("---\n");
        }
        return sb.toString();
    }

    public List<SwiftMtRecord> parseSwift(String content) {
        return Arrays.stream(content.split("---\n"))
                .filter(s -> !s.isBlank())
                .map(SwiftMtRecord::fromSwiftSection)
                .toList();
    }

    /** Exposed for diagnostics and tests: the layout read off the annotated model. */
    public AnnotatedLayout layout() {
        return layout;
    }

    // ── internals ─────────────────────────────────────────────────────────────

    private CodaRecord tokenizeLine(String line) {
        try {
            return fieldSetMapper.mapFieldSet(tokenizer.tokenize(line));
        } catch (Exception e) {
            log.warn("Annotated Spring Batch CODA tokenize failed for line, skipping: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Builds the aggregator from the layout: printf handles width and alignment; columns whose
     * annotation declares a non-space padding char (e.g. the zero-padded amount) are pre-filled,
     * since printf can only pad with spaces.
     */
    private static LineAggregator<CodaRecord> buildAggregator(AnnotatedLayout layout) {
        FormatterLineAggregator<CodaRecord> agg = new FormatterLineAggregator<>();
        agg.setFormat(layout.formatString());
        List<Column> columns = layout.columns();
        agg.setFieldExtractor((FieldExtractor<CodaRecord>) r -> columns.stream()
                .map(c -> pad(valueOf(r, c), c))
                .toArray());
        return agg;
    }

    /** Maps a CodaRecord component to its column, keyed by the annotated field name. */
    private static String valueOf(CodaRecord r, Column c) {
        return switch (c.name()) {
            case "recordType" -> orEmpty(r.recordType());
            case "bankId" -> orEmpty(r.bankId());
            case "referenceNumber" -> orEmpty(r.referenceNumber());
            case "accountNumber" -> orEmpty(r.accountNumber());
            case "currency" -> orEmpty(r.currency());
            case "amountStr" -> amountToStr(r.amount());
            case "entryDate" -> orEmpty(r.entryDate());
            case "valueDate" -> orEmpty(r.valueDate());
            case "description" -> orEmpty(r.description());
            case "transactionCode" -> orEmpty(r.transactionCode());
            case "sequenceNumber" -> orEmpty(r.sequenceNumber());
            case "filler" -> orEmpty(r.filler());
            default -> throw new IllegalStateException("Unmapped CODA column: " + c.name());
        };
    }

    private static CodaRecord mapRecord(org.springframework.batch.item.file.transform.FieldSet fs) {
        return CodaRecord.builder()
                .recordType(fs.readString("recordType").trim())
                .bankId(fs.readString("bankId").trim())
                .referenceNumber(fs.readString("referenceNumber").trim())
                .accountNumber(fs.readString("accountNumber").trim())
                .currency(fs.readString("currency").trim())
                .amount(parseAmount(fs.readString("amountStr")))
                .entryDate(fs.readString("entryDate").trim())
                .valueDate(fs.readString("valueDate").trim())
                .description(fs.readString("description").trim())
                .transactionCode(fs.readString("transactionCode").trim())
                .sequenceNumber(fs.readString("sequenceNumber").trim())
                .filler(fs.readString("filler").trim())
                .build();
    }

    /** Applies the annotation's padding char; space padding is left to the printf format. */
    private static String pad(String value, Column c) {
        if (c.padChar() == ' ' || value.length() >= c.length()) {
            return value;
        }
        String fill = String.valueOf(c.padChar()).repeat(c.length() - value.length());
        return c.rightAlign() ? fill + value : value + fill;
    }

    private static String ensureWidth(String line) {
        if (line.length() < CODA_LENGTH) return line + " ".repeat(CODA_LENGTH - line.length());
        return line.length() > CODA_LENGTH ? line.substring(0, CODA_LENGTH) : line;
    }

    private static String orEmpty(String s) {
        return s != null ? s : "";
    }

    private static String amountToStr(BigDecimal a) {
        return (a != null ? a : BigDecimal.ZERO).abs().toBigInteger().toString();
    }

    private static BigDecimal parseAmount(String s) {
        if (s == null || s.isBlank()) return BigDecimal.ZERO;
        try {
            return new BigDecimal(s.trim());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }
}
