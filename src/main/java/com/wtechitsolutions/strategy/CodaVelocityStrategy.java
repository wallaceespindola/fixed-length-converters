package com.wtechitsolutions.strategy;

import com.wtechitsolutions.domain.Library;
import com.wtechitsolutions.parser.VelocityFormatter;
import com.wtechitsolutions.parser.model.CodaRecord;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * CODA file generation strategy backed by Apache Velocity 2.4.1 template engine.
 * Delegates fixed-length serialisation to {@link VelocityFormatter} which merges
 * pre-padded field maps into {@code velocity/coda-record.vm}. Parse path delegates
 * to {@link CodaRecord#fromFixedWidth} since Velocity is a one-way template engine.
 */
@Service
public class CodaVelocityStrategy extends AbstractCodaStrategy {

    private final VelocityFormatter formatter;

    public CodaVelocityStrategy(VelocityFormatter formatter) {
        this.formatter = formatter;
    }

    @Override
    public Library getLibrary() { return Library.VELOCITY; }

    @Override
    protected String formatRecords(List<CodaRecord> records) {
        return formatter.formatCoda(records);
    }

    @Override
    protected List<CodaRecord> parseRecords(String content) {
        return formatter.parseCoda(content);
    }
}
