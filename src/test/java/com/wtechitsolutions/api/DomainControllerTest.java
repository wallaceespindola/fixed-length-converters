package com.wtechitsolutions.api;

import com.wtechitsolutions.batch.BatchJobService;
import com.wtechitsolutions.batch.OutputCleaner;
import com.wtechitsolutions.benchmark.BenchmarkService;
import com.wtechitsolutions.domain.DomainDataGenerator;
import com.wtechitsolutions.domain.LoadProfile;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DomainController.class)
class DomainControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    DomainDataGenerator generator;

    @MockitoBean
    BenchmarkService benchmarkService;

    @MockitoBean
    OutputCleaner outputCleaner;

    @MockitoBean
    BatchJobService batchJobService;

    @Test
    void generate_returns_200_with_expected_fields() throws Exception {
        when(generator.generate(LoadProfile.LOW))
                .thenReturn(new DomainDataGenerator.GenerationResult(1001L, 20, 200, 10));

        mockMvc.perform(post("/api/domain/generate")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operationId").value(1001))
                .andExpect(jsonPath("$.accountsGenerated").value(20))
                .andExpect(jsonPath("$.transactionsGenerated").value(200))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void generate_calls_domain_generator() throws Exception {
        when(generator.generate(LoadProfile.LOW))
                .thenReturn(new DomainDataGenerator.GenerationResult(2002L, 20, 200, 10));

        mockMvc.perform(post("/api/domain/generate")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operationId").value(2002));
    }

    @Test
    void generate_high_load_passes_profile_through() throws Exception {
        when(generator.generate(LoadProfile.HIGH))
                .thenReturn(new DomainDataGenerator.GenerationResult(3003L, 200, 2000, 100));

        mockMvc.perform(post("/api/domain/generate?loadProfile=HIGH")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operationId").value(3003))
                .andExpect(jsonPath("$.accountsGenerated").value(200))
                .andExpect(jsonPath("$.transactionsGenerated").value(2000));
    }

    @Test
    void reset_deletes_domain_data_and_benchmark_results() throws Exception {
        when(generator.reset()).thenReturn(new DomainDataGenerator.ResetResult(10, 100, 5));
        when(benchmarkService.deleteAll()).thenReturn(18L);
        when(outputCleaner.clean()).thenReturn(7L);
        when(batchJobService.clearJobMetadata()).thenReturn(36L);

        mockMvc.perform(delete("/api/domain/reset"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountsDeleted").value(10))
                .andExpect(jsonPath("$.transactionsDeleted").value(100))
                .andExpect(jsonPath("$.statementsDeleted").value(5))
                .andExpect(jsonPath("$.benchmarkResultsDeleted").value(18))
                .andExpect(jsonPath("$.jobExecutionsDeleted").value(36))
                .andExpect(jsonPath("$.filesDeleted").value(7))
                .andExpect(jsonPath("$.timestamp").exists());

        verify(generator).reset();
        verify(benchmarkService).deleteAll();
        verify(outputCleaner).clean();
        verify(batchJobService).clearJobMetadata();
    }

    @Test
    void reset_on_an_empty_database_reports_zero() throws Exception {
        when(generator.reset()).thenReturn(new DomainDataGenerator.ResetResult(0, 0, 0));
        when(benchmarkService.deleteAll()).thenReturn(0L);

        mockMvc.perform(delete("/api/domain/reset"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountsDeleted").value(0))
                .andExpect(jsonPath("$.benchmarkResultsDeleted").value(0));
    }
}
