package com.hachiko.portal.dto.health;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Respuesta del endpoint público GET /api/health.
 * Solo expone el estado general del sistema.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HealthResponse {

    /** "UP" si el sistema está operativo, "DOWN" si hay un fallo crítico. */
    private String status;
    private Instant timestamp;
}
