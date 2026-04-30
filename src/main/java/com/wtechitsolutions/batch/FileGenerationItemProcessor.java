package com.wtechitsolutions.batch;

import com.wtechitsolutions.domain.Account;
import com.wtechitsolutions.domain.AccountRepository;
import com.wtechitsolutions.domain.FileType;
import com.wtechitsolutions.domain.Library;
import com.wtechitsolutions.domain.Transaction;
import com.wtechitsolutions.strategy.FileGenerationStrategy;
import com.wtechitsolutions.strategy.StrategyResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.annotation.BeforeStep;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@StepScope
public class FileGenerationItemProcessor implements ItemProcessor<Transaction, String> {

    private static final Logger log = LoggerFactory.getLogger(FileGenerationItemProcessor.class);

    private final StrategyResolver strategyResolver;
    private final AccountRepository accountRepository;

    private List<Account> accounts;
    private FileGenerationStrategy strategy;

    public FileGenerationItemProcessor(StrategyResolver strategyResolver, AccountRepository accountRepository) {
        this.strategyResolver = strategyResolver;
        this.accountRepository = accountRepository;
    }

    @BeforeStep
    public void beforeStep(StepExecution stepExecution) {
        String fileTypeStr = stepExecution.getJobParameters().getString("fileType", "CODA");
        String libraryStr = stepExecution.getJobParameters().getString("library", "BEANIO");
        FileType fileType = FileType.valueOf(fileTypeStr);
        Library library = Library.valueOf(libraryStr);
        strategy = strategyResolver.resolve(fileType, library);
        accounts = accountRepository.findAll();
        log.debug("Processor initialized: fileType={}, library={}, accounts={}", fileType, library, accounts.size());
    }

    @Override
    public String process(Transaction item) {
        return strategy.generate(List.of(item), accounts);
    }
}
