package com.wtechitsolutions.parser;

import com.wtechitsolutions.parser.model.CodaRecord;
import com.wtechitsolutions.parser.model.SwiftMtRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.file.mapping.FieldSetMapper;
import org.springframework.batch.item.file.transform.FieldExtractor;
import org.springframework.batch.item.file.transform.FixedLengthTokenizer;
import org.springframework.batch.item.file.transform.FormatterLineAggregator;
import org.springframework.batch.item.file.transform.LineAggregator;
import org.springframework.batch.item.file.transform.Range;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Formatter using Spring Batch's native flat-file aggregator + tokenizer components in-process:
 * <ul>
 *   <li>Write path: {@link FormatterLineAggregator} (printf-style format string) joined into a single string</li>
 *   <li>Read path: {@link FixedLengthTokenizer} + {@link FieldSetMapper} applied line-by-line</li>
 * </ul>
 *
 * <p>Uses the aggregator and tokenizer directly rather than wrapping them in
 * {@code FlatFileItemWriter}/{@code FlatFileItemReader}. The reader/writer wrappers buffer
 * output for transactional, file-based chunk steps; using them inside an outer chunk's
 * per-item processor leads to flush/visibility issues when transactional and to temp-file
 * overhead with no benefit when not. The aggregator and tokenizer carry all the actual
 * Spring Batch formatting/parsing logic — they are the load-bearing components here.</p>
 *
 * <p>SWIFT delegates to {@link SwiftMtRecord#toSwiftFormat()} / {@link SwiftMtRecord#fromSwiftSection}
 * consistently with every other SWIFT strategy in the codebase.</p>
 *
 * <p>CODA field layout (128 chars total per Febelfin spec):</p>
 * <pre>
 *   pos  1    len  1  recordType
 *   pos  2    len  3  bankId
 *   pos  5    len 10  referenceNumber
 *   pos 15    len 37  accountNumber
 *   pos 52    len  3  currency
 *   pos 55    len 16  amount (zero-padded)
 *   pos 71    len  6  entryDate
 *   pos 77    len  6  valueDate
 *   pos 83    len 32  description
 *   pos 115   len  3  transactionCode
 *   pos 118   len  4  sequenceNumber
 *   pos 122   len  7  filler
 * </pre>
 *
 * @author Wallace Espindola, wallace.espindola@gmail.com
 */
@Component
public class SpringBatchFormatter {

    private static final Logger log = LoggerFactory.getLogger(SpringBatchFormatter.class);

    /**
     * printf format string whose column widths sum exactly to 128.
     * Field widths: 1+3+10+37+3+16+6+6+32+3+4+7 = 128
     * The amount column uses right-aligned {@code %16.16s} because {@link #padAmount} already
     * returns a left-zero-padded 16-char string — the right-align simply preserves it.
     */
    private static final String CODA_FORMAT =
            "%-1.1s%-3.3s%-10.10s%-37.37s%-3.3s%16.16s%-6.6s%-6.6s%-32.32s%-3.3s%4.4s%-7.7s";

    private final LineAggregator<CodaRecord> codaAggregator;
    private final FixedLengthTokenizer codaTokenizer;
    private final FieldSetMapper<CodaRecord> codaFieldSetMapper;

    public SpringBatchFormatter() {
        this.codaAggregator = buildCodaAggregator();
        this.codaTokenizer = buildCodaTokenizer();
        this.codaFieldSetMapper = buildCodaFieldSetMapper();
    }

    /**
     * Serialises a list of {@link CodaRecord} objects to a fixed-length CODA string using
     * {@link FlatFileItemWriter} with a {@link FormatterLineAggregator}.
     *
     * @param records list of CODA records to write
     * @return multi-line string; each non-blank line is exactly 128 characters
     */
    public String formatCoda(List<CodaRecord> records) {
        try {
            return records.stream()
                    .map(codaAggregator::aggregate)
                    .collect(Collectors.joining("\n")) + "\n";
        } catch (Exception e) {
            log.warn("Spring Batch CODA format failed — falling back to CodaRecord.toFixedWidth(): {}", e.getMessage());
            return records.stream()
                    .map(CodaRecord::toFixedWidth)
                    .collect(Collectors.joining("\n")) + "\n";
        }
    }

    /**
     * Parses a fixed-length CODA string back to a list of {@link CodaRecord} objects using
     * {@link FlatFileItemReader} with a {@link FixedLengthTokenizer} and {@link FieldSetMapper}.
     *
     * @param content multi-line CODA file content
     * @return list of parsed records; never {@code null}
     */
    public List<CodaRecord> parseCoda(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        try {
            return Arrays.stream(content.replace("\r\n", "\n").replace("\r", "\n").split("\n"))
                    .filter(l -> !l.isBlank())
                    .map(SpringBatchFormatter::ensureWidth)
                    .map(line -> {
                        try {
                            return codaFieldSetMapper.mapFieldSet(codaTokenizer.tokenize(line));
                        } catch (Exception e) {
                            return CodaRecord.fromFixedWidth(line);
                        }
                    })
                    .toList();
        } catch (Exception e) {
            log.warn("Spring Batch CODA parse failed — falling back to CodaRecord.fromFixedWidth(): {}", e.getMessage());
            return Arrays.stream(content.split("\n"))
                    .filter(l -> !l.isBlank())
                    .map(CodaRecord::fromFixedWidth)
                    .toList();
        }
    }

    /**
     * Serialises a list of {@link SwiftMtRecord} objects to MT940 tag format with {@code ---}
     * section delimiters. Delegates to {@link SwiftMtRecord#toSwiftFormat()} for consistency
     * with all other SWIFT strategies.
     *
     * @param records list of SWIFT MT records
     * @return concatenated MT940 content
     */
    public String formatSwift(List<SwiftMtRecord> records) {
        StringBuilder sb = new StringBuilder();
        for (SwiftMtRecord r : records) {
            sb.append(r.toSwiftFormat()).append("---\n");
        }
        return sb.toString();
    }

    /**
     * Parses MT940 content (sections separated by {@code ---}) back to a list of
     * {@link SwiftMtRecord} objects.
     *
     * @param content MT940 file content
     * @return list of parsed records; never {@code null}
     */
    public List<SwiftMtRecord> parseSwift(String content) {
        return Arrays.stream(content.split("---\n"))
                .filter(s -> !s.isBlank())
                .map(SwiftMtRecord::fromSwiftSection)
                .toList();
    }

    // ── Spring Batch component builders ──────────────────────────────────────

    /**
     * Builds a {@link FormatterLineAggregator} that applies {@link #CODA_FORMAT} via
     * {@link String#format}. Field values are extracted in column order; {@link #padAmount}
     * supplies the zero-padded 16-char amount string.
     */
    private LineAggregator<CodaRecord> buildCodaAggregator() {
        FormatterLineAggregator<CodaRecord> agg = new FormatterLineAggregator<>();
        agg.setFormat(CODA_FORMAT);
        agg.setFieldExtractor((FieldExtractor<CodaRecord>) r -> new Object[]{
                orEmpty(r.getRecordType()),
                orEmpty(r.getBankId()),
                orEmpty(r.getReferenceNumber()),
                orEmpty(r.getAccountNumber()),
                orEmpty(r.getCurrency()),
                padAmount(r.getAmount(), 16),
                orEmpty(r.getEntryDate()),
                orEmpty(r.getValueDate()),
                orEmpty(r.getDescription()),
                orEmpty(r.getTransactionCode()),
                padLeft(orEmpty(r.getSequenceNumber()), 4),
                orEmpty(r.getFiller())
        });
        return agg;
    }

    /**
     * Builds a {@link FixedLengthTokenizer} using 1-based {@link Range} positions aligned
     * to the Febelfin CODA specification (128 chars per record).
     */
    private FixedLengthTokenizer buildCodaTokenizer() {
        FixedLengthTokenizer t = new FixedLengthTokenizer();
        t.setColumns(
                new Range(1, 1),    // recordType
                new Range(2, 4),    // bankId
                new Range(5, 14),   // referenceNumber
                new Range(15, 51),  // accountNumber
                new Range(52, 54),  // currency
                new Range(55, 70),  // amountStr
                new Range(71, 76),  // entryDate
                new Range(77, 82),  // valueDate
                new Range(83, 114), // description
                new Range(115, 117),// transactionCode
                new Range(118, 121),// sequenceNumber
                new Range(122, 128) // filler
        );
        t.setNames("recordType", "bankId", "referenceNumber", "accountNumber",
                "currency", "amountStr", "entryDate", "valueDate", "description",
                "transactionCode", "sequenceNumber", "filler");
        t.setStrict(false);
        return t;
    }

    /**
     * Builds a {@link FieldSetMapper} that maps tokenised CODA fields back to a
     * {@link CodaRecord}. Amount is read as a plain integer string (no decimal separator).
     */
    private FieldSetMapper<CodaRecord> buildCodaFieldSetMapper() {
        return fs -> {
            String amountStr = fs.readString("amountStr").trim();
            BigDecimal amount;
            try {
                amount = amountStr.isBlank() ? BigDecimal.ZERO : new BigDecimal(amountStr);
            } catch (NumberFormatException e) {
                amount = BigDecimal.ZERO;
            }
            return CodaRecord.builder()
                    .recordType(fs.readString("recordType").trim())
                    .bankId(fs.readString("bankId").trim())
                    .referenceNumber(fs.readString("referenceNumber").trim())
                    .accountNumber(fs.readString("accountNumber").trim())
                    .currency(fs.readString("currency").trim())
                    .amount(amount)
                    .entryDate(fs.readString("entryDate").trim())
                    .valueDate(fs.readString("valueDate").trim())
                    .description(fs.readString("description").trim())
                    .transactionCode(fs.readString("transactionCode").trim())
                    .sequenceNumber(fs.readString("sequenceNumber").trim())
                    .filler(fs.readString("filler").trim())
                    .build();
        };
    }

    // ── static helpers ────────────────────────────────────────────────────────

    /** Pads or truncates a line to exactly 128 characters. */
    private static String ensureWidth(String line) {
        if (line.length() < 128) return line + " ".repeat(128 - line.length());
        return line.length() > 128 ? line.substring(0, 128) : line;
    }

    private static String orEmpty(String s) {
        return s != null ? s : "";
    }

    /** Left-pads {@code value} with spaces (right-aligns) to {@code length}; truncates if longer. */
    private static String padLeft(String value, int length) {
        if (value == null) value = "";
        if (value.length() >= length) return value.substring(0, length);
        return " ".repeat(length - value.length()) + value;
    }

    /**
     * Returns the absolute integer value of {@code amount} as a zero-padded string
     * of {@code length} characters. Matches the CODA spec (no decimal separator).
     */
    private static String padAmount(BigDecimal amount, int length) {
        BigDecimal a = amount != null ? amount : BigDecimal.ZERO;
        String s = a.abs().toBigInteger().toString();
        if (s.length() >= length) return s.substring(0, length);
        return "0".repeat(length - s.length()) + s;
    }
}
