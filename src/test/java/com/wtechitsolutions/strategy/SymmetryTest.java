package com.wtechitsolutions.strategy;

import com.wtechitsolutions.domain.Account;
import com.wtechitsolutions.domain.AccountRepository;
import com.wtechitsolutions.domain.FileType;
import com.wtechitsolutions.domain.Library;
import com.wtechitsolutions.domain.Transaction;
import com.wtechitsolutions.domain.TransactionRepository;
import com.wtechitsolutions.domain.TransactionType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Round-trip symmetry tests: Domain → generate file → parse file → verify domain fields preserved.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SymmetryTest {

    @Autowired
    StrategyResolver resolver;

    @Autowired
    AccountRepository accountRepository;

    @Autowired
    TransactionRepository transactionRepository;

    private Transaction savedTransaction;

    @BeforeEach
    void setUp() {
        Account account = accountRepository.save(Account.builder()
                .accountNumber("BE11223344556677")
                .iban("BE11223344556677")
                .bankCode("430")
                .currency("EUR")
                .balance(new BigDecimal("5000"))
                .holderName("Symmetry Tester")
                .createdAt(Instant.now())
                .build());

        savedTransaction = transactionRepository.save(Transaction.builder()
                .accountId(account.getId())
                .reference("SYMREF0000001")
                .amount(new BigDecimal("750"))
                .type(TransactionType.CREDIT)
                .description("Symmetry test payment")
                .valueDate(LocalDate.of(2026, 4, 29))
                .entryDate(LocalDate.of(2026, 4, 29))
                .createdAt(Instant.now())
                .build());
    }

    @AfterEach
    void tearDown() {
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
    }

    @ParameterizedTest
    @MethodSource("allCombinations")
    void round_trip_preserves_amount_and_type(FileType fileType, Library library) {
        FileGenerationStrategy strategy = resolver.resolve(fileType, library);
        List<Transaction> transactions = transactionRepository.findAll();
        List<Account> accounts = accountRepository.findAll();

        String generated = strategy.generate(transactions, accounts);
        List<Transaction> parsed = strategy.parse(generated);

        assertThat(parsed)
                .as("Parsed transactions should not be empty for %s/%s", fileType, library)
                .isNotEmpty();

        Transaction first = parsed.get(0);
        assertThat(first.getAmount())
                .as("Amount should be preserved in %s/%s round-trip", fileType, library)
                .isEqualByComparingTo(savedTransaction.getAmount());
        assertThat(first.getType())
                .as("Transaction type should be preserved in %s/%s round-trip", fileType, library)
                .isEqualTo(savedTransaction.getType());
    }

    static Stream<Arguments> allCombinations() {
        return Arrays.stream(FileType.values())
                .flatMap(ft -> Arrays.stream(Library.values())
                        .map(lib -> Arguments.of(ft, lib)));
    }
}
