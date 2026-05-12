package com.wtechitsolutions.strategy;

import com.wtechitsolutions.domain.FileType;
import com.wtechitsolutions.domain.Library;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class StrategyResolverTest {

    @Autowired
    StrategyResolver resolver;

    @ParameterizedTest
    @MethodSource("allCombinations")
    void resolves_all_registered_strategies(FileType fileType, Library library) {
        FileGenerationStrategy strategy = resolver.resolve(fileType, library);

        assertThat(strategy).isNotNull();
        assertThat(strategy.getFileType()).isEqualTo(fileType);
        assertThat(strategy.getLibrary()).isEqualTo(library);
    }

    @Test
    void strategy_key_matches_filetype_library() {
        FileGenerationStrategy strategy = resolver.resolve(FileType.CODA, Library.BEANIO);
        assertThat(strategy.strategyKey()).isEqualTo("CODA_BEANIO");
    }

    @Test
    void throws_for_null_inputs() {
        assertThatThrownBy(() -> resolver.resolve(null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throws_for_null_file_type() {
        assertThatThrownBy(() -> resolver.resolve(null, Library.BEANIO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throws_for_null_library() {
        assertThatThrownBy(() -> resolver.resolve(FileType.CODA, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    static Stream<Arguments> allCombinations() {
        return Arrays.stream(FileType.values())
                .flatMap(ft -> Arrays.stream(Library.values())
                        .map(lib -> Arguments.of(ft, lib)));
    }
}
