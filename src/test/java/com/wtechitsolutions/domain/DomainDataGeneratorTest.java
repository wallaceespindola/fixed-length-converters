package com.wtechitsolutions.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DomainDataGeneratorTest {

    @Mock
    AccountRepository accountRepository;

    @Mock
    TransactionRepository transactionRepository;

    @Mock
    BankingStatementRepository statementRepository;

    @InjectMocks
    DomainDataGenerator generator;

    @Test
    void generate_returns_correct_counts() {
        AtomicLong idCounter = new AtomicLong(1L);
        when(accountRepository.save(any())).thenAnswer(inv -> {
            Account a = inv.getArgument(0);
            ReflectionTestUtils.setField(a, "id", idCounter.getAndIncrement());
            return a;
        });
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(statementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DomainDataGenerator.GenerationResult result = generator.generate();

        assertThat(result.accounts()).isEqualTo(20);
        assertThat(result.transactions()).isEqualTo(200);
        assertThat(result.statements()).isEqualTo(10);
        assertThat(result.operationId()).isPositive();
    }

    @Test
    void generate_increments_operationId_each_call() {
        AtomicLong idCounter = new AtomicLong(100L);
        when(accountRepository.save(any())).thenAnswer(inv -> {
            Account a = inv.getArgument(0);
            ReflectionTestUtils.setField(a, "id", idCounter.getAndIncrement());
            return a;
        });
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(statementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        long id1 = generator.generate().operationId();
        long id2 = generator.generate().operationId();

        assertThat(id2).isGreaterThan(id1);
    }
}
