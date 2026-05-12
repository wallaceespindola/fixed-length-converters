package com.wtechitsolutions.strategy;

import com.wtechitsolutions.domain.Library;
import com.wtechitsolutions.parser.SpringBatchFormatter;
import com.wtechitsolutions.parser.model.CodaRecord;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * CODA file generation strategy backed by Spring Batch's native flat-file infrastructure.
 * Uses {@link SpringBatchFormatter} which delegates to {@link org.springframework.batch.item.file.FlatFileItemWriter}
 * with a {@link org.springframework.batch.item.file.transform.FormatterLineAggregator} for writing,
 * and {@link org.springframework.batch.item.file.FlatFileItemReader} with
 * {@link org.springframework.batch.item.file.transform.FixedLengthTokenizer} for parsing.
 *
 * @author Wallace Espindola, wallace.espindola@gmail.com
 */
@Service
public class CodaSpringBatchStrategy extends AbstractCodaStrategy {

    private final SpringBatchFormatter formatter;

    public CodaSpringBatchStrategy(SpringBatchFormatter formatter) {
        this.formatter = formatter;
    }

    @Override
    public Library getLibrary() {
        return Library.SPRING_BATCH;
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
