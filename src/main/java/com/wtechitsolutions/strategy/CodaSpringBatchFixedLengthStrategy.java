package com.wtechitsolutions.strategy;

import com.wtechitsolutions.domain.Library;
import com.wtechitsolutions.parser.SpringBatchFixedLengthFormatter;
import com.wtechitsolutions.parser.model.CodaRecord;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * CODA strategy combining Spring Batch flat-file components with a vitaliy fixedlength
 * annotation-derived layout. The tokenizer ranges and aggregator format string are generated
 * from {@code VlCodaRecord}'s {@code @FixedField} annotations instead of being restated as
 * {@code Range} literals.
 *
 * @author Wallace Espindola, wallace.espindola@gmail.com
 */
@Service
public class CodaSpringBatchFixedLengthStrategy extends AbstractCodaStrategy {

    private final SpringBatchFixedLengthFormatter formatter;

    public CodaSpringBatchFixedLengthStrategy(SpringBatchFixedLengthFormatter formatter) {
        this.formatter = formatter;
    }

    @Override
    public Library getLibrary() {
        return Library.SPRING_BATCH_FIXEDLENGTH;
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
