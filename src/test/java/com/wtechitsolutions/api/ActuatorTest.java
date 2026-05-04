package com.wtechitsolutions.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ActuatorTest {

    @Autowired
    TestRestTemplate restTemplate;

    @Test
    @SuppressWarnings("unchecked")
    void health_endpoint_returns_up() {
        ResponseEntity<Map<String, Object>> response =
                restTemplate.getForEntity("/actuator/health", (Class<Map<String, Object>>) (Class<?>) Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).containsKey("status");
        assertThat(response.getBody().get("status")).isEqualTo("UP");
    }

    @Test
    @SuppressWarnings("unchecked")
    void info_endpoint_returns_200() {
        ResponseEntity<Map<String, Object>> response =
                restTemplate.getForEntity("/actuator/info", (Class<Map<String, Object>>) (Class<?>) Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    void metrics_endpoint_is_not_exposed() {
        // Actuator security: only health and info are exposed
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/metrics", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
