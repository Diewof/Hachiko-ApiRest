package com.hachiko.portal.service.impl;

import com.hachiko.portal.dto.health.ComponentStatus;
import com.hachiko.portal.dto.health.HealthDetailResponse;
import com.hachiko.portal.dto.health.HealthResponse;
import com.hachiko.portal.service.IHealthService;
import com.hachiko.portal.service.ITokenBlacklistService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.lang.management.ManagementFactory;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementación del servicio de health check.
 *
 * Verifica tres componentes:
 *  - database       : ejecuta SELECT 1 con timeout de 2 segundos
 *  - tokenBlacklist : llama isBlacklisted con una sonda ficticia
 *  - emailProvider  : lee la propiedad email.provider (no hace red)
 *
 * Si la base de datos está DOWN, el status general es DOWN.
 * Los demás componentes son informativos y no afectan el status general.
 */
@Service
public class HealthServiceImpl implements IHealthService {

    private static final String STATUS_UP   = "UP";
    private static final String STATUS_DOWN = "DOWN";

    private final DataSource dataSource;
    private final ITokenBlacklistService tokenBlacklistService;
    private final String emailProvider;
    private final String appVersion;

    public HealthServiceImpl(DataSource dataSource,
                             ITokenBlacklistService tokenBlacklistService,
                             @Value("${email.provider}") String emailProvider,
                             @Value("${app.version:0.0.1-SNAPSHOT}") String appVersion) {
        this.dataSource            = dataSource;
        this.tokenBlacklistService = tokenBlacklistService;
        this.emailProvider         = emailProvider;
        this.appVersion            = appVersion;
    }

    @Override
    public HealthResponse getBasicHealth() {
        ComponentStatus db = checkDatabase();
        String status = STATUS_DOWN.equals(db.getStatus()) ? STATUS_DOWN : STATUS_UP;
        return HealthResponse.builder()
                .status(status)
                .timestamp(Instant.now())
                .build();
    }

    @Override
    public HealthDetailResponse getDetailedHealth() {
        List<ComponentStatus> components = new ArrayList<>();
        components.add(checkDatabase());
        components.add(checkTokenBlacklist());
        components.add(checkEmailProvider());

        boolean anyDown = components.stream()
                .anyMatch(c -> STATUS_DOWN.equals(c.getStatus()));
        String status = anyDown ? STATUS_DOWN : STATUS_UP;

        long uptimeSeconds = ManagementFactory.getRuntimeMXBean().getUptime() / 1000;

        return HealthDetailResponse.builder()
                .status(status)
                .timestamp(Instant.now())
                .version(appVersion)
                .uptimeSeconds(uptimeSeconds)
                .components(components)
                .build();
    }

    private ComponentStatus checkDatabase() {
        long start = System.currentTimeMillis();
        try (Connection conn = dataSource.getConnection();
             Statement stmt  = conn.createStatement()) {
            stmt.setQueryTimeout(2);
            stmt.execute("SELECT 1");
            return ComponentStatus.builder()
                    .name("database")
                    .status(STATUS_UP)
                    .message("Conexión activa")
                    .responseTimeMs(System.currentTimeMillis() - start)
                    .build();
        } catch (Exception e) {
            return ComponentStatus.builder()
                    .name("database")
                    .status(STATUS_DOWN)
                    .message("Error de conexión: " + e.getMessage())
                    .responseTimeMs(System.currentTimeMillis() - start)
                    .build();
        }
    }

    private ComponentStatus checkTokenBlacklist() {
        long start = System.currentTimeMillis();
        try {
            tokenBlacklistService.isBlacklisted("health-check-probe");
            return ComponentStatus.builder()
                    .name("tokenBlacklist")
                    .status(STATUS_UP)
                    .message("Servicio activo")
                    .responseTimeMs(System.currentTimeMillis() - start)
                    .build();
        } catch (Exception e) {
            return ComponentStatus.builder()
                    .name("tokenBlacklist")
                    .status(STATUS_DOWN)
                    .message("Error: " + e.getMessage())
                    .responseTimeMs(System.currentTimeMillis() - start)
                    .build();
        }
    }

    private ComponentStatus checkEmailProvider() {
        long start = System.currentTimeMillis();
        String message = "stub".equals(emailProvider)
                ? "Modo stub activo"
                : "Proveedor: " + emailProvider;
        return ComponentStatus.builder()
                .name("emailProvider")
                .status(STATUS_UP)
                .message(message)
                .responseTimeMs(System.currentTimeMillis() - start)
                .build();
    }
}
