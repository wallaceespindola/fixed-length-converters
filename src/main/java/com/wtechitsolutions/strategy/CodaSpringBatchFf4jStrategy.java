package com.wtechitsolutions.strategy;

import com.wtechitsolutions.domain.Library;
import com.wtechitsolutions.parser.SpringBatchFf4jFormatter;
import com.wtechitsolutions.parser.model.CodaRecord;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * CODA strategy combining Spring Batch flat-file components with a fixedformat4j
 * annotation-derived layout. Unlike {@link CodaSpringBatchStrategy}, the tokenizer ranges and
 * aggregator format string are generated from {@code Ff4jCodaRecord}'s {@code @Field}
 * annotations instead of being restated as {@code Range} literals.
 *
 * @author Wallace Espindola, wallace.espindola@gmail.com
 */
@Service
public class CodaSpringBatchFf4jStrategy extends AbstractCodaStrategy {

    private final SpringBatchFf4jFormatter formatter;

    public CodaSpringBatchFf4jStrategy(SpringBatchFf4jFormatter formatter) {
        this.formatter = formatter;
    }

    @Override
    public Library getLibrary() {
        return Library.SPRING_BATCH_FF4J;
    }

    @Override
    protected String formatRecords(List<CodaRecord> records) {
        return formatter.formatCoda(records);
    }

    @Override
    protected List<CodaRecord> parseRecords(String content) {
        return formatter.parseCoda(content);
    }
}
