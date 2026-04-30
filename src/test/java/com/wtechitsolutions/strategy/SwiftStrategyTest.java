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
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SwiftStrategyTest {

    @Autowired
    StrategyResolver resolver;

    @Autowired
    AccountRepository accountRepository;

    @Autowired
    TransactionRepository transactionRepository;

    @BeforeEach
    void setUp() {
        Account account = accountRepository.save(Account.builder()
                .accountNumber("BE98765432109876")
                .iban("BE98765432109876")
                .bankCode("320")
                .currency("EUR")
                .balance(new BigDecimal("25000.00"))
                .holderName("SWIFT Test Holder")
                .createdAt(Instant.now())
                .build());

        transactionRepository.save(Transaction.builder()
                .accountId(account.getId())
                .reference("SWIFTREF00001")
                .amount(new BigDecimal("1000"))
                .type(TransactionType.CREDIT)
                .description("SWIFT test payment")
                .valueDate(LocalDate.now())
                .entryDate(LocalDate.now())
                .createdAt(Instant.now())
                .build());
    }

    @AfterEach
    void tearDown() {
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
    }

    @ParameterizedTest
    @MethodSource("swiftLibraries")
    void swift_output_is_not_blank(Library library) {
        FileGenerationStrategy strategy = resolver.resolve(FileType.SWIFT, library);
        String output = strategy.generate(
                transactionRepository.findAll(),
                accountRepository.findAll());
        assertThat(output).isNotBlank();
    }

    @ParameterizedTest
    @MethodSource("swiftLibraries")
    void swift_output_has_minimum_content(Library library) {
        FileGenerationStrategy strategy = resolver.resolve(FileType.SWIFT, library);
        String output = strategy.generate(
                transactionRepository.findAll(),
                accountRepository.findAll());
        // All strategies produce some meaningful content
        assertThat(output.length()).isGreaterThan(20);
    }

    @ParameterizedTest
    @MethodSource("swiftMt940Libraries")
    void swift_mt940_output_contains_required_tags(Library library) {
        FileGenerationStrategy strategy = resolver.resolve(FileType.SWIFT, library);
        String output = strategy.generate(
                transactionRepository.findAll(),
                accountRepository.findAll());
        // MT940 strategies (non-BeanIO) produce tag-based content
        assertThat(output).contains(":20:", ":25:", ":28C:", ":60F:", ":62F:");
    }

    static Stream<Arguments> swiftLibraries() {
        return Arrays.stream(Library.values()).map(Arguments::of);
    }

    static Stream<Arguments> swiftMt940Libraries() {
        // BeanIO serialises as CSV; the others produce MT940 tags
        return Stream.of(Library.FIXEDFORMAT4J, Library.FIXEDLENGTH, Library.BINDY)
                .map(Arguments::of);
    }
}
