package com.wtechitsolutions.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("dev")
public class OpenApiConfig {

    @Bean
    public OpenAPI bankingPlatformOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Banking Fixed-Length File Generator & Parser Validation Platform")
                        .description("""
                                Enterprise banking file experimentation platform.
                                Generates and parses CODA and SWIFT MT files using 4 parser libraries
                                via the Strategy Pattern and Spring Batch.
                                """)
                        .version("3.0.0")
                        .contact(new Contact()
                                .name("Wallace Espindola")
                                .email("wallace.espindola@gmail.com")
                                .url("https://www.linkedin.com/in/wallaceespindola/")));
    }
}
