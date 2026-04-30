package com.wtechitsolutions.strategy;

import com.wtechitsolutions.domain.Account;
import com.wtechitsolutions.domain.FileType;
import com.wtechitsolutions.domain.Transaction;
import com.wtechitsolutions.domain.TransactionType;
import com.wtechitsolutions.parser.model.SwiftMtRecord;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Shared SWIFT MT940 domain mapping logic reused across all 4 SWIFT strategy implementations.
 */
abstract class AbstractSwiftStrategy implements FileGenerationStrategy {

    private static final DateTimeFormatter SWIFT_DATE = DateTimeFormatter.ofPattern("yyMMdd");
    private static final DateTimeFormatter SWIFT_ENTRY_DATE = DateTimeFormatter.ofPattern("MMdd");

    @Override
    public final FileType getFileType() {
        return FileType.SWIFT;
    }

    @Override
    public String generate(List<Transaction> transactions, List<Account> accounts) {
        Account account = accounts.isEmpty() ? null : accounts.get(0);
        List<SwiftMtRecord> records = buildSwiftRecords(transactions, account);
        return formatRecords(records);
    }

    @Override
    public List<Transaction> parse(String fileContent) {
        return parseRecords(fileContent).stream()
                .filter(r -> r.getAmount() != null && !r.getAmount().isBlank())
                .map(this::toTransaction)
                .collect(Collectors.toList());
    }

    protected abstract String formatRecords(List<SwiftMtRecord> records);

    protected abstract List<SwiftMtRecord> parseRecords(String content);

    private List<SwiftMtRecord> buildSwiftRecords(List<Transaction> transactions, Account account) {
        List<SwiftMtRecord> records = new ArrayList<>();
        String statementRef = "STMT" + (System.currentTimeMillis() % 1000000);
        String accountId = account != null ? account.getIban() + "/EUR" : "UNKNOWN/EUR";
        String openingBal = buildBalance(account != null ? account.getBalance() : BigDecimal.ZERO, true);
        BigDecimal closingBalance = computeClosingBalance(account, transactions);
        String closingBal = buildBalance(closingBalance, true);

        for (int i = 0; i < transactions.size(); i++) {
            Transaction t = transactions.get(i);
            SwiftMtRecord record = SwiftMtRecord.builder()
                    .transactionReference(truncate(statementRef, 16))
                    .accountIdentification(truncate(accountId, 35))
                    .statementNumber(String.format("%05d/%03d", i + 1, 1))
                    .openingBalance(openingBal)
                    .valueDate(t.getValueDate().format(SWIFT_DATE))
                    .entryDate(t.getEntryDate().format(SWIFT_ENTRY_DATE))
                    .debitCreditMark(TransactionType.CREDIT.equals(t.getType()) ? "C" : "D")
                    .amount(formatAmount(t.getAmount()))
                    .transactionType("NMSC")
                    .customerReference(truncate(
                            t.getReference() != null ? t.getReference() : "NONREF", 16))
                    .information(truncate(
                            t.getDescription() != null ? t.getDescription() : "", 65))
                    .closingBalance(closingBal)
                    .build();
            records.add(record);
        }
        return records;
    }

    protected Transaction toTransaction(SwiftMtRecord r) {
        LocalDate valueDate;
        try {
            String yd = r.getValueDate();
            valueDate = yd != null && yd.length() == 6
                    ? LocalDate.parse("20" + yd, DateTimeFormatter.ofPattern("yyyyMMdd"))
                    : LocalDate.now();
        } catch (Exception e) {
            valueDate = LocalDate.now();
        }

        BigDecimal amount;
        try {
            amount = r.getAmount() != null
                    ? new BigDecimal(r.getAmount().replace(",", "."))
                    : BigDecimal.ZERO;
        } catch (NumberFormatException e) {
            amount = BigDecimal.ZERO;
        }

        TransactionType type = "D".equals(r.getDebitCreditMark())
                ? TransactionType.DEBIT : TransactionType.CREDIT;

        return Transaction.builder()
                .reference(r.getCustomerReference() != null ? r.getCustomerReference().trim() : "")
                .amount(amount)
                .type(type)
                .description(r.getInformation() != null ? r.getInformation().trim() : "")
                .valueDate(valueDate)
                .entryDate(valueDate)
                .build();
    }

    private String buildBalance(BigDecimal amount, boolean credit) {
        return (credit ? "C" : "D")
                + LocalDate.now().format(SWIFT_DATE)
                + "EUR"
                + formatAmount(amount != null ? amount : BigDecimal.ZERO);
    }

    private BigDecimal computeClosingBalance(Account account, List<Transaction> transactions) {
        BigDecimal base = account != null && account.getBalance() != null
                ? account.getBalance() : BigDecimal.ZERO;
        return transactions.stream()
                .map(t -> TransactionType.CREDIT.equals(t.getType())
                        ? t.getAmount() : t.getAmount().negate())
                .reduce(base, BigDecimal::add);
    }

    protected static String formatAmount(BigDecimal amount) {
        if (amount == null) return "0,00";
        return amount.abs().toPlainString().replace(".", ",");
    }

    protected static String truncate(String value, int maxLen) {
        if (value == null) return "";
        return value.length() > maxLen ? value.substring(0, maxLen) : value;
    }
}
