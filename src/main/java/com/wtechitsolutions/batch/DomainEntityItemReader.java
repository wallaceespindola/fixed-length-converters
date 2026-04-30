package com.wtechitsolutions.batch;

import com.wtechitsolutions.domain.Transaction;
import com.wtechitsolutions.domain.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.annotation.BeforeStep;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemReader;
import org.springframework.stereotype.Component;

import java.util.Iterator;

@Component
@StepScope
@RequiredArgsConstructor
public class DomainEntityItemReader implements ItemReader<Transaction> {

    private final TransactionRepository transactionRepository;
    private Iterator<Transaction> iterator;

    @BeforeStep
    public void beforeStep(StepExecution stepExecution) {
        iterator = transactionRepository.findAll().iterator();
    }

    @Override
    public Transaction read() {
        return iterator != null && iterator.hasNext() ? iterator.next() : null;
    }
}
