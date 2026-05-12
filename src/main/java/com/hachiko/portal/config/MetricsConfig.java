package com.hachiko.portal.config;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Punto central para registrar métricas custom (Counters, Timers, Gauges).
 * Los tags comunes (application, env) se configuran en application.yaml
 * bajo management.metrics.tags para no duplicar lógica aquí.
 */
@Configuration
public class MetricsConfig {

    private final MeterRegistry meterRegistry;

    @Value("${spring.application.name:hachiko-portal}")
    private String appName;

    public MetricsConfig(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    void init() {
        meterRegistry.config().commonTags("app", appName);
    }
}
