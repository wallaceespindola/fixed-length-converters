package com.wtechitsolutions.api;

import com.wtechitsolutions.api.dto.GenerateDomainResponse;
import com.wtechitsolutions.domain.DomainDataGenerator;
import com.wtechitsolutions.domain.LoadProfile;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/domain")
@Tag(name = "Domain", description = "Banking domain data generation")
public class DomainController {

    private static final Logger log = LoggerFactory.getLogger(DomainController.class);

    private final DomainDataGenerator generator;

    public DomainController(DomainDataGenerator generator) {
        this.generator = generator;
    }

    @PostMapping("/generate")
    @Operation(summary = "Generate sample banking domain data and persist to H2",
            description = "Pass loadProfile=LOW (20 accounts/200 txns) or HIGH (200 accounts/2000 txns). Defaults to LOW.")
    public ResponseEntity<GenerateDomainResponse> generate(
            @RequestParam(name = "loadProfile", required = false, defaultValue = "LOW") LoadProfile loadProfile) {
        log.info("Generating banking domain data, profile={}", loadProfile);
        DomainDataGenerator.GenerationResult result = generator.generate(loadProfile);
        return ResponseEntity.ok(new GenerateDomainResponse(
                result.operationId(),
                result.accounts(),
                result.transactions(),
                Instant.now()));
    }
}
