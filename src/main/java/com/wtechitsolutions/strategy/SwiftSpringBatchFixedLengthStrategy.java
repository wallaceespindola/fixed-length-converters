package com.wtechitsolutions.strategy;

import com.wtechitsolutions.domain.Library;
import com.wtechitsolutions.parser.SpringBatchFixedLengthFormatter;
import com.wtechitsolutions.parser.model.SwiftMtRecord;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * SWIFT MT940 strategy for the Spring Batch + fixedlength annotation-driven formatter.
 * SWIFT is tag-based, so serialisation delegates to {@link SwiftMtRecord#toSwiftFormat()} —
 * consistent with every other SWIFT strategy.
 *
 * @author Wallace Espindola, wallace.espindola@gmail.com
 */
@Service
public class SwiftSpringBatchFixedLengthStrategy extends AbstractSwiftStrategy {

    private final SpringBatchFixedLengthFormatter formatter;

    public SwiftSpringBatchFixedLengthStrategy(SpringBatchFixedLengthFormatter formatter) {
        this.formatter = formatter;
    }

    @Override
    public Library getLibrary() {
        return Library.SPRING_BATCH_FIXEDLENGTH;
    }

    @Override
    protected String formatRecords(List<SwiftMtRecord> records) {
        return formatter.formatSwift(records);
    }

    @Override
    protected List<SwiftMtRecord> parseRecords(String content) {
        return formatter.parseSwift(content);
    }
}
