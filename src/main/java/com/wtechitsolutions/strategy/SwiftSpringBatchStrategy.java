package com.wtechitsolutions.strategy;

import com.wtechitsolutions.domain.Library;
import com.wtechitsolutions.parser.SpringBatchFormatter;
import com.wtechitsolutions.parser.model.SwiftMtRecord;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * SWIFT MT940 file generation strategy backed by Spring Batch's native flat-file infrastructure.
 * Delegates to {@link SpringBatchFormatter} which uses {@link SwiftMtRecord#toSwiftFormat()} for
 * serialisation and {@link SwiftMtRecord#fromSwiftSection} for parsing — consistent with all
 * other SWIFT strategies in the codebase.
 *
 * @author Wallace Espindola, wallace.espindola@gmail.com
 */
@Service
public class SwiftSpringBatchStrategy extends AbstractSwiftStrategy {

    private final SpringBatchFormatter formatter;

    public SwiftSpringBatchStrategy(SpringBatchFormatter formatter) {
        this.formatter = formatter;
    }

    @Override
    public Library getLibrary() {
        return Library.SPRING_BATCH;
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
