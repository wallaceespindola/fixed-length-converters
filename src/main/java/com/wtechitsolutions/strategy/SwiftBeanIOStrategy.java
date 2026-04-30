package com.wtechitsolutions.strategy;

import com.wtechitsolutions.domain.Library;
import com.wtechitsolutions.parser.BeanIOFormatter;
import com.wtechitsolutions.parser.model.SwiftMtRecord;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SwiftBeanIOStrategy extends AbstractSwiftStrategy {

    private final BeanIOFormatter formatter;

    public SwiftBeanIOStrategy(BeanIOFormatter formatter) {
        this.formatter = formatter;
    }

    @Override
    public Library getLibrary() { return Library.BEANIO; }

    @Override
    protected String formatRecords(List<SwiftMtRecord> records) {
        return formatter.formatSwift(records);
    }

    @Override
    protected List<SwiftMtRecord> parseRecords(String content) {
        return formatter.parseSwift(content);
    }
}
