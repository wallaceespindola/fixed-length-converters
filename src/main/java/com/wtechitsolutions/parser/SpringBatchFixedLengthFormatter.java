package com.wtechitsolutions.parser;

import com.wtechitsolutions.parser.model.VlCodaRecord;
import org.springframework.stereotype.Component;

/**
 * Hybrid formatter: Spring Batch flat-file components driven by vitaliy fixedlength
 * {@code @FixedLine}/{@code @FixedField} annotations on {@link VlCodaRecord}.
 *
 * <p>Offsets, widths, alignment and padding characters are read from the model at construction
 * time — the tokenizer ranges and the {@code FormatterLineAggregator} format string are generated,
 * not hand-written, so a layout change on the model propagates to both read and write paths.</p>
 *
 * @author Wallace Espindola, wallace.espindola@gmail.com
 */
@Component
public class SpringBatchFixedLengthFormatter extends AnnotatedSpringBatchFormatter {

    public SpringBatchFixedLengthFormatter() {
        super(AnnotatedLayout.fromFixedLength(VlCodaRecord.class));
    }
}
