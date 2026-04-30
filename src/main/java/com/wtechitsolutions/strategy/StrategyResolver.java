package com.wtechitsolutions.strategy;

import com.wtechitsolutions.domain.FileType;
import com.wtechitsolutions.domain.Library;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Resolves the correct FileGenerationStrategy for a given FileType × Library combination.
 * Spring injects all strategy beans; this service maps them by their strategyKey().
 */
@Service
@Slf4j
public class StrategyResolver {

    private final Map<String, FileGenerationStrategy> strategies;

    public StrategyResolver(List<FileGenerationStrategy> strategyList) {
        this.strategies = strategyList.stream()
                .collect(Collectors.toMap(FileGenerationStrategy::strategyKey, s -> s));
        log.info("Registered {} file generation strategies: {}", strategies.size(), strategies.keySet());
    }

    /**
     * Resolves the strategy for the given file type and library.
     *
     * @throws IllegalArgumentException if no strategy is registered for the combination
     */
    public FileGenerationStrategy resolve(FileType fileType, Library library) {
        if (fileType == null || library == null) {
            throw new IllegalArgumentException("fileType and library must not be null");
        }
        String key = fileType.name() + "_" + library.name();
        FileGenerationStrategy strategy = strategies.get(key);
        if (strategy == null) {
            throw new IllegalArgumentException(
                    "No strategy registered for key: " + key + ". Available: " + strategies.keySet());
        }
        return strategy;
    }
}
