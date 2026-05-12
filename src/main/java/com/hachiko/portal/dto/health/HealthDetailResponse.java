package com.hachiko.portal.dto.health;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

/**
 * Respuesta del endpoint protegido GET /api/health/details (solo ADMIN).
 * Incluye el estado individual de cada componente del sistema.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HealthDetailResponse {

    private String status;
    private Instant timestamp;
    private String version;
    private long uptimeSeconds;
    private List<ComponentStatus> components;
}
