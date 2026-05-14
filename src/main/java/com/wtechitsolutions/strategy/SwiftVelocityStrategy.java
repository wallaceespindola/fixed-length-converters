package com.wtechitsolutions.strategy;

import com.wtechitsolutions.domain.Library;
import com.wtechitsolutions.parser.VelocityFormatter;
import com.wtechitsolutions.parser.model.SwiftMtRecord;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * SWIFT MT940 file generation strategy backed by Apache Velocity 2.4.1 template engine.
 * Delegates tag-format serialisation to {@link VelocityFormatter} which merges
 * {@link SwiftMtRecord} fields into {@code velocity/swift-record.vm}. Parse path
 * delegates to {@link SwiftMtRecord#fromSwiftSection} since Velocity is one-way.
 */
@Service
public class SwiftVelocityStrategy extends AbstractSwiftStrategy {

    private final VelocityFormatter formatter;

    public SwiftVelocityStrategy(VelocityFormatter formatter) {
        this.formatter = formatter;
    }

    @Override
    public Library getLibrary() { return Library.VELOCITY; }

    @Override
    protected String formatRecords(List<SwiftMtRecord> records) {
        return formatter.formatSwift(records);
    }

    @Override
    protected List<SwiftMtRecord> parseRecords(String content) {
        return formatter.parseSwift(content);
    }
}
