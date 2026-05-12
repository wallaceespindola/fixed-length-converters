package com.wtechitsolutions.domain;

/**
 * Load profile for domain data generation. Controls the size of the generated dataset.
 */
public enum LoadProfile {
    /** Light dataset: 20 accounts, 200 transactions (10/account), 10 statements. */
    LOW(20, 10, 10),

    /** Heavy dataset: 200 accounts, 2000 transactions (10/account), 100 statements. */
    HIGH(200, 10, 100);

    private final int accountCount;
    private final int transactionsPerAccount;
    private final int statementCount;

    LoadProfile(int accountCount, int transactionsPerAccount, int statementCount) {
        this.accountCount = accountCount;
        this.transactionsPerAccount = transactionsPerAccount;
        this.statementCount = statementCount;
    }

    public int accountCount() { return accountCount; }
    public int transactionsPerAccount() { return transactionsPerAccount; }
    public int statementCount() { return statementCount; }
}
