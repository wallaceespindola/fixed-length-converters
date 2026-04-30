package com.wtechitsolutions.strategy;

import com.wtechitsolutions.domain.Account;
import com.wtechitsolutions.domain.FileType;
import com.wtechitsolutions.domain.Transaction;
import com.wtechitsolutions.domain.TransactionType;
import com.wtechitsolutions.parser.model.CodaRecord;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Shared CODA domain mapping logic reused across all 4 CODA strategy implementations.
 * Subclasses provide the library-specific serialisation/deserialisation.
 */
abstract class AbstractCodaStrategy implements FileGenerationStrategy {

    private static final DateTimeFormatter CODA_DATE = DateTimeFormatter.ofPattern("ddMMyy");

    @Override
    public final FileType getFileType() {
        return FileType.CODA;
    }

    /**
     * Converts domain objects to CodaRecord list, delegates formatting to subclass.
     */
    @Override
    public String generate(List<Transaction> transactions, List<Account> accounts) {
        Map<Long, Account> accountMap = accounts.stream()
                .collect(Collectors.toMap(Account::getId, a -> a));
        List<CodaRecord> records = new ArrayList<>();
        Account firstAccount = accounts.isEmpty() ? null : accounts.get(0);
        records.add(buildHeader(firstAccount));
        transactions.forEach(t -> records.add(buildMovement(t, accountMap.get(t.getAccountId()))));
        records.add(buildTrailer(transactions));
        return formatRecords(records);
    }

    /**
     * Deserialises file content to Transaction list, delegates parsing to subclass.
     */
    @Override
    public List<Transaction> parse(String fileContent) {
        return parseRecords(fileContent).stream()
                .filter(r -> "1".equals(r.getRecordType()))
                .map(this::toTransaction)
                .collect(Collectors.toList());
    }

    /** Subclass-specific formatting using the library's API. */
    protected abstract String formatRecords(List<CodaRecord> records);

    /** Subclass-specific parsing using the library's API. */
    protected abstract List<CodaRecord> parseRecords(String content);

    protected CodaRecord buildHeader(Account account) {
        return CodaRecord.builder()
                .recordType("0")
                .bankId(account != null && account.getBankCode() != null ? account.getBankCode() : "000")
                .referenceNumber(pad("HDR", 10))
                .accountNumber(pad(account != null ? account.getIban() : "", 37))
                .currency(account != null ? account.getCurrency() : "EUR")
                .amount(BigDecimal.ZERO)
                .entryDate(LocalDate.now().format(CODA_DATE))
                .valueDate(LocalDate.now().format(CODA_DATE))
                .description(pad("CODA HEADER", 32))
                .transactionCode("000")
                .sequenceNumber("0000")
                .filler("       ")
                .build();
    }

    protected CodaRecord buildMovement(Transaction t, Account account) {
        String ref = t.getReference() != null
                ? t.getReference().substring(0, Math.min(10, t.getReference().length()))
                : "";
        String desc = t.getDescription() != null
                ? t.getDescription().substring(0, Math.min(32, t.getDescription().length()))
                : "";
        String iban = account != null && account.getIban() != null ? account.getIban() : "";
        String bankCode = account != null && account.getBankCode() != null ? account.getBankCode() : "000";
        String currency = account != null ? account.getCurrency() : "EUR";
        return CodaRecord.builder()
                .recordType("1")
                .bankId(bankCode)
                .referenceNumber(pad(ref, 10))
                .accountNumber(pad(iban, 37))
                .currency(currency)
                .amount(t.getAmount())
                .entryDate(t.getEntryDate().format(CODA_DATE))
                .valueDate(t.getValueDate().format(CODA_DATE))
                .description(pad(desc, 32))
                .transactionCode(TransactionType.CREDIT.equals(t.getType()) ? "001" : "002")
                .sequenceNumber(String.format("%04d", t.getId() != null ? t.getId() % 9999 : 0))
                .filler("       ")
                .build();
    }

    protected CodaRecord buildTrailer(List<Transaction> transactions) {
        BigDecimal total = transactions.stream()
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return CodaRecord.builder()
                .recordType("9")
                .bankId("000")
                .referenceNumber(pad("TRAILER", 10))
                .accountNumber(pad("", 37))
                .currency("EUR")
                .amount(total)
                .entryDate(LocalDate.now().format(CODA_DATE))
                .valueDate(LocalDate.now().format(CODA_DATE))
                .description(pad("TOTAL: " + transactions.size(), 32))
                .transactionCode("999")
                .sequenceNumber(String.format("%04d", transactions.size() % 9999))
                .filler("       ")
                .build();
    }

    protected Transaction toTransaction(CodaRecord r) {
        LocalDate date;
        try {
            date = LocalDate.parse(r.getValueDate(), CODA_DATE);
        } catch (Exception e) {
            date = LocalDate.now();
        }
        return Transaction.builder()
                .reference(trim(r.getReferenceNumber()))
                .amount(r.getAmount() != null ? r.getAmount() : BigDecimal.ZERO)
                .type("001".equals(trim(r.getTransactionCode()))
                        ? TransactionType.CREDIT : TransactionType.DEBIT)
                .description(trim(r.getDescription()))
                .valueDate(date)
                .entryDate(date)
                .build();
    }

    protected static String pad(String value, int length) {
        if (value == null) value = "";
        if (value.length() >= length) return value.substring(0, length);
        return value + " ".repeat(length - value.length());
    }

    protected static String trim(String value) {
        return value != null ? value.trim() : "";
    }
}
