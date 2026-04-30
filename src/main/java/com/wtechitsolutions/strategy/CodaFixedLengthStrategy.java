package com.wtechitsolutions.strategy;

import com.wtechitsolutions.domain.Library;
import com.wtechitsolutions.parser.FixedLengthFormatter;
import com.wtechitsolutions.parser.model.CodaRecord;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CodaFixedLengthStrategy extends AbstractCodaStrategy {

    private final FixedLengthFormatter formatter;

    public CodaFixedLengthStrategy(FixedLengthFormatter formatter) {
        this.formatter = formatter;
    }

    @Override
    public Library getLibrary() { return Library.FIXEDLENGTH; }

    @Override
    protected String formatRecords(List<CodaRecord> records) {
        return formatter.formatCoda(records);
    }

    @Override
    protected List<CodaRecord> parseRecords(String content) {
        return formatter.parseCoda(content);
    }
}
