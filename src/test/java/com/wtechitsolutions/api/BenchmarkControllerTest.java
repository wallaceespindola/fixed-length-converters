package com.wtechitsolutions.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BenchmarkControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void exportHtml_returns_200_with_text_html_content() throws Exception {
        mockMvc.perform(get("/api/benchmark/export/html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"));
    }

    @Test
    void exportHtml_contains_html_table_structure() throws Exception {
        mockMvc.perform(get("/api/benchmark/export/html"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<table")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Benchmark Report")));
    }
}
