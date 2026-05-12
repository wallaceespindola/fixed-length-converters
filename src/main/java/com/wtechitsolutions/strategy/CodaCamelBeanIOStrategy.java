package com.wtechitsolutions.strategy;

import com.wtechitsolutions.domain.Library;
import com.wtechitsolutions.parser.CamelBeanIOFormatter;
import com.wtechitsolutions.parser.model.CodaRecord;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * CODA file generation strategy backed by Apache Camel BeanIO (4.20.0).
 * Delegates fixed-length serialisation/deserialisation to {@link CamelBeanIOFormatter}
 * which uses a classpath BeanIO XML mapping (beanio/coda-mapping.xml).
 */
@Service
public class CodaCamelBeanIOStrategy extends AbstractCodaStrategy {

    private final CamelBeanIOFormatter formatter;

    public CodaCamelBeanIOStrategy(CamelBeanIOFormatter formatter) {
        this.formatter = formatter;
    }

    @Override
    public Library getLibrary() { return Library.CAMEL_BEANIO; }

    @Override
    protected String formatRecords(List<CodaRecord> records) {
        return formatter.formatCoda(records);
    }

    @Override
    protected List<CodaRecord> parseRecords(String content) {
        return formatter.parseCoda(content);
    }
}
