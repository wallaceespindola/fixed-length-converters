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
class CodaStrategyTest {

    @Autowired
    StrategyResolver resolver;

    @Autowired
    AccountRepository accountRepository;

    @Autowired
    TransactionRepository transactionRepository;

    @BeforeEach
    void setUp() {
        Account account = accountRepository.save(Account.builder()
                .accountNumber("BE12345678901234")
                .iban("BE12345678901234")
                .bankCode("310")
                .currency("EUR")
                .balance(new BigDecimal("10000.00"))
                .holderName("Test Holder")
                .createdAt(Instant.now())
                .build());

        transactionRepository.save(Transaction.builder()
                .accountId(account.getId())
                .reference("REF001234567")
                .amount(new BigDecimal("500"))
                .type(TransactionType.CREDIT)
                .description("Test CODA payment")
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
    @MethodSource("codaLibraries")
    void coda_output_is_not_blank(Library library) {
        FileGenerationStrategy strategy = resolver.resolve(FileType.CODA, library);
        String output = strategy.generate(
                transactionRepository.findAll(),
                accountRepository.findAll());
        assertThat(output).isNotBlank();
    }

    @ParameterizedTest
    @MethodSource("codaLibraries")
    void coda_output_lines_are_128_chars(Library library) {
        FileGenerationStrategy strategy = resolver.resolve(FileType.CODA, library);
        String output = strategy.generate(
                transactionRepository.findAll(),
                accountRepository.findAll());

        Arrays.stream(output.split("\n"))
                .filter(l -> !l.isBlank())
                .forEach(line -> assertThat(line)
                        .as("CODA line should be 128 chars for library %s", library)
                        .hasSize(128));
    }

    @ParameterizedTest
    @MethodSource("codaLibraries")
    void coda_output_contains_header_and_trailer(Library library) {
        FileGenerationStrategy strategy = resolver.resolve(FileType.CODA, library);
        String output = strategy.generate(
                transactionRepository.findAll(),
                accountRepository.findAll());

        // Header starts with '0', trailer with '9'
        boolean hasHeader = Arrays.stream(output.split("\n"))
                .filter(l -> !l.isBlank())
                .anyMatch(l -> l.charAt(0) == '0');
        boolean hasTrailer = Arrays.stream(output.split("\n"))
                .filter(l -> !l.isBlank())
                .anyMatch(l -> l.charAt(0) == '9');
        assertThat(hasHeader).as("Should have CODA header record").isTrue();
        assertThat(hasTrailer).as("Should have CODA trailer record").isTrue();
    }

    static Stream<Arguments> codaLibraries() {
        return Arrays.stream(Library.values()).map(Arguments::of);
    }
}
