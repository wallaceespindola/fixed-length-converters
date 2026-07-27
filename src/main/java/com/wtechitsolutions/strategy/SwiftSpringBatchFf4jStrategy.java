package com.wtechitsolutions.strategy;

import com.wtechitsolutions.domain.Library;
import com.wtechitsolutions.parser.SpringBatchFf4jFormatter;
import com.wtechitsolutions.parser.model.SwiftMtRecord;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * SWIFT MT940 strategy for the Spring Batch + fixedformat4j annotation-driven formatter.
 * SWIFT is tag-based, so serialisation delegates to {@link SwiftMtRecord#toSwiftFormat()} —
 * consistent with every other SWIFT strategy.
 *
 * @author Wallace Espindola, wallace.espindola@gmail.com
 */
@Service
public class SwiftSpringBatchFf4jStrategy extends AbstractSwiftStrategy {

    private final SpringBatchFf4jFormatter formatter;

    public SwiftSpringBatchFf4jStrategy(SpringBatchFf4jFormatter formatter) {
        this.formatter = formatter;
    }

    @Override
    public Library getLibrary() {
        return Library.SPRING_BATCH_FF4J;
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
