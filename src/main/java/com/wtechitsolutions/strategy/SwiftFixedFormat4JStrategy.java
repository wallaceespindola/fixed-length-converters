package com.wtechitsolutions.strategy;

import com.wtechitsolutions.domain.Library;
import com.wtechitsolutions.parser.FixedFormat4JFormatter;
import com.wtechitsolutions.parser.model.SwiftMtRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SwiftFixedFormat4JStrategy extends AbstractSwiftStrategy {

    private final FixedFormat4JFormatter formatter;

    @Override
    public Library getLibrary() { return Library.FIXEDFORMAT4J; }

    @Override
    protected String formatRecords(List<SwiftMtRecord> records) {
        return formatter.formatSwift(records);
    }

    @Override
    protected List<SwiftMtRecord> parseRecords(String content) {
        return formatter.parseSwift(content);
    }
}
