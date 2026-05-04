package com.wtechitsolutions.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Exposes application version and artifact info via /actuator/health as the "version" component.
 * Version is injected from application.yml info.app.version, which is populated by Maven
 * resource filtering (@project.version@ → pom.xml version at build time).
 */
@Component
public class VersionHealthIndicator implements HealthIndicator {

    @Value("${info.app.version:unknown}")
    private String appVersion;

    @Value("${info.app.name:unknown}")
    private String appName;

    @Value("${info.app.description:}")
    private String appDescription;

    @Override
    public Health health() {
        return Health.up()
                .withDetail("version", appVersion)
                .withDetail("artifact", appName)
                .withDetail("description", appDescription)
                .build();
    }
}
