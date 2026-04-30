package com.wtechitsolutions.strategy;

import com.wtechitsolutions.domain.Library;
import com.wtechitsolutions.parser.BindyFormatter;
import com.wtechitsolutions.parser.model.SwiftMtRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SwiftBindyStrategy extends AbstractSwiftStrategy {

    private final BindyFormatter formatter;

    @Override
    public Library getLibrary() { return Library.BINDY; }

    @Override
    protected String formatRecords(List<SwiftMtRecord> records) {
        return formatter.formatSwift(records);
    }

    @Override
    protected List<SwiftMtRecord> parseRecords(String content) {
        return formatter.parseSwift(content);
    }
}
