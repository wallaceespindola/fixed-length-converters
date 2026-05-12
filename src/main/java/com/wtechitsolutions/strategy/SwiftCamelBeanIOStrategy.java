package com.wtechitsolutions.strategy;

import com.wtechitsolutions.domain.Library;
import com.wtechitsolutions.parser.CamelBeanIOFormatter;
import com.wtechitsolutions.parser.model.SwiftMtRecord;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * SWIFT MT940 file generation strategy backed by Apache Camel BeanIO (4.20.0).
 * SWIFT serialisation delegates to {@link CamelBeanIOFormatter#formatSwift} /
 * {@link CamelBeanIOFormatter#parseSwift}, which use the same tag-format as
 * every other SWIFT strategy in the codebase (no XML mapping needed for SWIFT).
 */
@Service
public class SwiftCamelBeanIOStrategy extends AbstractSwiftStrategy {

    private final CamelBeanIOFormatter formatter;

    public SwiftCamelBeanIOStrategy(CamelBeanIOFormatter formatter) {
        this.formatter = formatter;
    }

    @Override
    public Library getLibrary() { return Library.CAMEL_BEANIO; }

    @Override
    protected String formatRecords(List<SwiftMtRecord> records) {
        return formatter.formatSwift(records);
    }

    @Override
    protected List<SwiftMtRecord> parseRecords(String content) {
        return formatter.parseSwift(content);
    }
}
