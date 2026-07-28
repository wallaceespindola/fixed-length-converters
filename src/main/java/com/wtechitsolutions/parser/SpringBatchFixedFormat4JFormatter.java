package com.wtechitsolutions.parser;

import com.wtechitsolutions.parser.model.Ff4jCodaRecord;
import org.springframework.stereotype.Component;

/**
 * Hybrid formatter: Spring Batch flat-file components driven by fixedformat4j
 * {@code @Record}/{@code @Field} annotations on {@link Ff4jCodaRecord}.
 *
 * <p>Offsets, widths, alignment and padding characters are read from the model at construction
 * time — the tokenizer ranges and the {@code FormatterLineAggregator} format string are generated,
 * not hand-written, so a layout change on the model propagates to both read and write paths.</p>
 *
 * @author Wallace Espindola, wallace.espindola@gmail.com
 */
@Component
public class SpringBatchFixedFormat4JFormatter extends AnnotatedSpringBatchFormatter {

    public SpringBatchFixedFormat4JFormatter() {
        super(AnnotatedLayout.fromFixedFormat4j(Ff4jCodaRecord.class));
    }
}
