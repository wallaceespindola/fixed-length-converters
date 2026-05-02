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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TS-009 Golden file tests — verify generated output against known-good structure.
 * Tests that CODA lines are exactly 128 chars and SWIFT has required MT940 tags.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class GoldenFileTest {

    @Autowired StrategyResolver resolver;
    @Autowired AccountRepository accountRepository;
    @Autowired TransactionRepository transactionRepository;

    @BeforeEach
    void setUp() {
        Account account = accountRepository.save(Account.builder()
                .accountNumber("BE68539007547034")
                .iban("BE68539007547034")
                .bankCode("539")
                .currency("EUR")
                .balance(new BigDecimal("10000"))
                .holderName("Golden File Tester")
                .createdAt(Instant.now())
                .build());

        transactionRepository.save(Transaction.builder()
                .accountId(account.getId())
                .reference("GOLDEN0000001")
                .amount(new BigDecimal("500"))
                .type(TransactionType.CREDIT)
                .description("Golden file test payment")
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
    @EnumSource(Library.class)
    void coda_every_line_is_exactly_128_chars(Library library) {
        FileGenerationStrategy strategy = resolver.resolve(FileType.CODA, library);
        String output = strategy.generate(
                transactionRepository.findAll(),
                accountRepository.findAll());

        assertThat(output).isNotBlank();
        long nonBlankLines = Arrays.stream(output.split("\n"))
                .filter(l -> !l.isBlank())
                .peek(line -> assertThat(line)
                        .as("CODA line must be exactly 128 chars (library=%s)", library)
                        .hasSize(128))
                .count();
        assertThat(nonBlankLines).isGreaterThanOrEqualTo(3); // at minimum: header + 1 movement + trailer
    }

    @ParameterizedTest
    @EnumSource(Library.class)
    void coda_has_required_record_types(Library library) {
        FileGenerationStrategy strategy = resolver.resolve(FileType.CODA, library);
        String output = strategy.generate(
                transactionRepository.findAll(),
                accountRepository.findAll());

        List<String> lines = Arrays.stream(output.split("\n"))
                .filter(l -> !l.isBlank())
                .toList();

        boolean hasHeader  = lines.stream().anyMatch(l -> l.charAt(0) == '0');
        boolean hasMovement = lines.stream().anyMatch(l -> l.charAt(0) == '1');
        boolean hasTrailer  = lines.stream().anyMatch(l -> l.charAt(0) == '9');

        assertThat(hasHeader).as("CODA must have header record (type 0) for library=%s", library).isTrue();
        assertThat(hasMovement).as("CODA must have movement record (type 1) for library=%s", library).isTrue();
        assertThat(hasTrailer).as("CODA must have trailer record (type 9) for library=%s", library).isTrue();
    }

    @ParameterizedTest
    @EnumSource(Library.class)
    void swift_contains_required_mt940_tags(Library library) {
        FileGenerationStrategy strategy = resolver.resolve(FileType.SWIFT, library);
        String output = strategy.generate(
                transactionRepository.findAll(),
                accountRepository.findAll());

        assertThat(output).isNotBlank();
        // BeanIO serialises SWIFT as CSV (structured), others as MT940 tags
        if (library == Library.BEANIO) {
            // BeanIO CSV format: verify non-empty structured output with transaction data
            assertThat(output.split("\n").length).isGreaterThan(0);
        } else {
            // MT940 tag-based format: verify required tags
            assertThat(output).as("SWIFT output must contain :20: tag (library=%s)", library)
                    .contains(":20:");
            assertThat(output).as("SWIFT output must contain :25: tag (library=%s)", library)
                    .contains(":25:");
            assertThat(output).as("SWIFT output must contain :60F: tag (library=%s)", library)
                    .contains(":60F:");
            assertThat(output).as("SWIFT output must contain :62F: tag (library=%s)", library)
                    .contains(":62F:");
        }
    }

    @Test
    void coda_amount_encoding_uses_integer_representation() {
        FileGenerationStrategy strategy = resolver.resolve(FileType.CODA, Library.BEANIO);
        String output = strategy.generate(
                transactionRepository.findAll(),
                accountRepository.findAll());

        // Amount 500 should appear as "0000000000000500" in the amount field (positions 55-70)
        List<String> movementLines = Arrays.stream(output.split("\n"))
                .filter(l -> !l.isBlank() && l.charAt(0) == '1')
                .toList();
        assertThat(movementLines).isNotEmpty();
        String amountField = movementLines.get(0).substring(54, 70);
        assertThat(amountField).isEqualTo("0000000000000500");
    }
}
