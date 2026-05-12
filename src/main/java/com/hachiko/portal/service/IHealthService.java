package com.hachiko.portal.service;

import com.hachiko.portal.dto.health.HealthDetailResponse;
import com.hachiko.portal.dto.health.HealthResponse;

/**
 * Contrato para los checks de salud del sistema.
 *
 * Principio DIP: HealthController depende de esta interfaz, no de la implementación.
 * Principio SRP: responsabilidad única — verificar disponibilidad de componentes.
 */
public interface IHealthService {

    /**
     * Estado general del sistema (solo base de datos).
     * Usado por el endpoint público GET /api/health.
     *
     * @return HealthResponse con status UP o DOWN y timestamp actual
     */
    HealthResponse getBasicHealth();

    /**
     * Estado detallado por componente: base de datos, token blacklist y email provider.
     * Usado por el endpoint protegido GET /api/health/details (solo ADMIN).
     *
     * @return HealthDetailResponse con status, versión, uptime y lista de componentes
     */
    HealthDetailResponse getDetailedHealth();
}
