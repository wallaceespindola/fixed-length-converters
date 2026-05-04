package com.wtechitsolutions.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wtechitsolutions.batch.BatchJobService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BatchController.class)
class BatchControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    BatchJobService batchJobService;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void generate_returns_200_with_job_execution_id() throws Exception {
        when(batchJobService.launch(any(), any()))
                .thenReturn(new BatchJobService.BatchJobResult(42L, "COMPLETED", "file content", "CODA_BEANIO.txt"));

        mockMvc.perform(post("/api/batch/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileType\":\"CODA\",\"library\":\"BEANIO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobExecutionId").value(42))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.fileType").value("CODA"))
                .andExpect(jsonPath("$.library").value("BEANIO"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void history_returns_empty_array_when_no_jobs() throws Exception {
        when(batchJobService.getHistory()).thenReturn(List.of());

        mockMvc.perform(get("/api/batch/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void generate_with_swift_library_bindy() throws Exception {
        when(batchJobService.launch(any(), any()))
                .thenReturn(new BatchJobService.BatchJobResult(99L, "COMPLETED", "swift content", "SWIFT_BINDY.txt"));

        mockMvc.perform(post("/api/batch/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileType\":\"SWIFT\",\"library\":\"BINDY\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobExecutionId").value(99))
                .andExpect(jsonPath("$.fileName").value("SWIFT_BINDY.txt"));
    }
}
