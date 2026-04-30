package com.wtechitsolutions.domain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

@Service
public class DomainDataGenerator {

    private static final Logger log = LoggerFactory.getLogger(DomainDataGenerator.class);

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final BankingStatementRepository statementRepository;

    private final AtomicLong operationIdCounter = new AtomicLong(1000);

    public DomainDataGenerator(AccountRepository accountRepository,
                                TransactionRepository transactionRepository,
                                BankingStatementRepository statementRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.statementRepository = statementRepository;
    }

    @Transactional
    public GenerationResult generate() {
        long operationId = operationIdCounter.incrementAndGet();
        log.info("Generating domain data for operationId={}", operationId);

        List<Account> accounts = IntStream.range(0, 20)
                .mapToObj(i -> buildAccount(i, operationId))
                .map(accountRepository::save)
                .toList();

        List<Transaction> transactions = accounts.stream()
                .flatMap(a -> IntStream.range(0, 10).mapToObj(i -> buildTransaction(a, i)))
                .map(transactionRepository::save)
                .toList();

        List<BankingStatement> statements = accounts.stream()
                .limit(10)
                .map(a -> buildStatement(a, operationId))
                .map(statementRepository::save)
                .toList();

        log.info("Generated {} accounts, {} transactions, {} statements for operationId={}",
                accounts.size(), transactions.size(), statements.size(), operationId);

        return new GenerationResult(operationId, accounts.size(), transactions.size(), statements.size());
    }

    private Account buildAccount(int idx, long opId) {
        String accountNum = String.format("BE%018d", opId * 100L + idx);
        return Account.builder()
                .accountNumber(accountNum)
                .iban(accountNum)
                .bankCode(String.format("%03d", 300 + idx % 50))
                .currency("EUR")
                .balance(BigDecimal.valueOf(10000L + (long) idx * 1000L))
                .holderName("Account Holder " + idx)
                .createdAt(Instant.now())
                .build();
    }

    private Transaction buildTransaction(Account account, int idx) {
        boolean isCredit = idx % 2 == 0;
        String description = "Transaction " + idx + " for " + account.getAccountNumber();
        return Transaction.builder()
                .accountId(account.getId())
                .reference(String.format("REF%012d", account.getId() * 100L + idx))
                .amount(BigDecimal.valueOf(100L + (long) idx * 50L))
                .type(isCredit ? TransactionType.CREDIT : TransactionType.DEBIT)
                .description(description.length() > 128 ? description.substring(0, 128) : description)
                .valueDate(LocalDate.now().minusDays(idx))
                .entryDate(LocalDate.now().minusDays(idx))
                .createdAt(Instant.now())
                .build();
    }

    private BankingStatement buildStatement(Account account, long opId) {
        return BankingStatement.builder()
                .accountId(account.getId())
                .openingBalance(account.getBalance().subtract(BigDecimal.valueOf(5000)))
                .closingBalance(account.getBalance())
                .statementDate(LocalDate.now())
                .sequenceNumber((int) (opId % 999 + 1))
                .createdAt(Instant.now())
                .build();
    }

    public record GenerationResult(long operationId, int accounts, int transactions, int statements) {}
}
