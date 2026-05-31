package com.wtechitsolutions.domain;

/**
 * Load profile for domain data generation. Controls the size of the generated dataset.
 */
public enum LoadProfile {
    /** Light dataset: 10 accounts, 100 transactions (10/account), 5 statements. */
    LOW(10, 10, 5),

    /** Medium dataset: 100 accounts, 1000 transactions (10/account), 50 statements. */
    MEDIUM(100, 10, 50),

    /** Heavy dataset: 1000 accounts, 10000 transactions (10/account), 500 statements. */
    HIGH(1000, 10, 500);

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
