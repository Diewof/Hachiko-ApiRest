package com.hachiko.portal.controller;

import com.hachiko.portal.dto.health.HealthDetailResponse;
import com.hachiko.portal.dto.health.HealthResponse;
import com.hachiko.portal.service.IHealthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador REST para monitoreo del estado del sistema.
 *
 * Rutas:
 *   GET /api/health         → público — estado general (UP / DOWN)
 *   GET /api/health/details → solo ADMIN — estado por componente
 *
 * Retorna HTTP 200 cuando el sistema está UP y HTTP 503 cuando está DOWN,
 * para que balanceadores de carga puedan interpretar el estado automáticamente.
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final IHealthService healthService;

    public HealthController(IHealthService healthService) {
        this.healthService = healthService;
    }

    /**
     * Estado general del sistema. Público, sin token.
     *
     * GET /api/health
     */
    @GetMapping
    public ResponseEntity<HealthResponse> health() {
        HealthResponse response = healthService.getBasicHealth();
        HttpStatus httpStatus = "UP".equals(response.getStatus())
                ? HttpStatus.OK
                : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(httpStatus).body(response);
    }

    /**
     * Estado detallado por componente. Requiere rol ADMIN.
     * La autorización se controla en SecurityConfig (.hasRole("ADMIN")).
     *
     * GET /api/health/details
     */
    @GetMapping("/details")
    public ResponseEntity<HealthDetailResponse> details() {
        HealthDetailResponse response = healthService.getDetailedHealth();
        HttpStatus httpStatus = "UP".equals(response.getStatus())
                ? HttpStatus.OK
                : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(httpStatus).body(response);
    }
}
