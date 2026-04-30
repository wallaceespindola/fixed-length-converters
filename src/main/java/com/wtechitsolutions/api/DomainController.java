package com.wtechitsolutions.api;

import com.wtechitsolutions.api.dto.GenerateDomainResponse;
import com.wtechitsolutions.domain.DomainDataGenerator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/domain")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Domain", description = "Banking domain data generation")
public class DomainController {

    private final DomainDataGenerator generator;

    @PostMapping("/generate")
    @Operation(summary = "Generate sample banking domain data and persist to H2")
    public ResponseEntity<GenerateDomainResponse> generate() {
        log.info("Generating banking domain data");
        DomainDataGenerator.GenerationResult result = generator.generate();
        return ResponseEntity.ok(new GenerateDomainResponse(
                result.operationId(),
                result.accounts(),
                result.transactions(),
                Instant.now()));
    }
}
