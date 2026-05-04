package com.wtechitsolutions.api;

import com.wtechitsolutions.domain.DomainDataGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DomainController.class)
class DomainControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    DomainDataGenerator generator;

    @Test
    void generate_returns_200_with_expected_fields() throws Exception {
        when(generator.generate())
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
        when(generator.generate())
                .thenReturn(new DomainDataGenerator.GenerationResult(2002L, 20, 200, 10));

        mockMvc.perform(post("/api/domain/generate")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operationId").value(2002));
    }
}
