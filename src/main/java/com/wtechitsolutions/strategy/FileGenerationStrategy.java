package com.wtechitsolutions.strategy;

import com.wtechitsolutions.domain.Account;
import com.wtechitsolutions.domain.FileType;
import com.wtechitsolutions.domain.Library;
import com.wtechitsolutions.domain.Transaction;

import java.util.List;

/**
 * Strategy interface for banking file generation and parsing.
 * Each combination of FileType × Library has its own implementation.
 */
public interface FileGenerationStrategy {

    /**
     * Generates a banking file string from the given transactions and accounts.
     *
     * @param transactions the domain transactions to format
     * @param accounts     the accounts providing context (IBAN, bank code, etc.)
     * @return the generated file content as a string
     */
    String generate(List<Transaction> transactions, List<Account> accounts);

    /**
     * Parses a banking file string back into a list of Transaction domain objects.
     *
     * @param fileContent the file content to parse
     * @return parsed Transaction objects (without persistence IDs)
     */
    List<Transaction> parse(String fileContent);

    /**
     * Returns the file format this strategy handles.
     */
    FileType getFileType();

    /**
     * Returns the formatter library this strategy uses.
     */
    Library getLibrary();

    /**
     * Returns the unique resolution key for this strategy: "FILETYPE_LIBRARY".
     */
    default String strategyKey() {
        return getFileType().name() + "_" + getLibrary().name();
    }
}
