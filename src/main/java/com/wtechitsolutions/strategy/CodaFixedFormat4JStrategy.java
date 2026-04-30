package com.wtechitsolutions.strategy;

import com.wtechitsolutions.domain.Library;
import com.wtechitsolutions.parser.FixedFormat4JFormatter;
import com.wtechitsolutions.parser.model.CodaRecord;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CodaFixedFormat4JStrategy extends AbstractCodaStrategy {

    private final FixedFormat4JFormatter formatter;

    public CodaFixedFormat4JStrategy(FixedFormat4JFormatter formatter) {
        this.formatter = formatter;
    }

    @Override
    public Library getLibrary() { return Library.FIXEDFORMAT4J; }

    @Override
    protected String formatRecords(List<CodaRecord> records) {
        return formatter.formatCoda(records);
    }

    @Override
    protected List<CodaRecord> parseRecords(String content) {
        return formatter.parseCoda(content);
    }
}
