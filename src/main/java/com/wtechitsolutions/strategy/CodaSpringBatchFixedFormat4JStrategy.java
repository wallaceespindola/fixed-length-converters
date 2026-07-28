package com.wtechitsolutions.strategy;

import com.wtechitsolutions.domain.Library;
import com.wtechitsolutions.parser.SpringBatchFixedFormat4JFormatter;
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
public class CodaSpringBatchFixedFormat4JStrategy extends AbstractCodaStrategy {

    private final SpringBatchFixedFormat4JFormatter formatter;

    public CodaSpringBatchFixedFormat4JStrategy(SpringBatchFixedFormat4JFormatter formatter) {
        this.formatter = formatter;
    }

    @Override
    public Library getLibrary() {
        return Library.SPRING_BATCH_FIXFORMAT4J;
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
