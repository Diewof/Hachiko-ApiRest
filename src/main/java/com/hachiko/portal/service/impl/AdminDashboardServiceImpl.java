package com.hachiko.portal.service.impl;

import com.hachiko.portal.dto.admin.ActividadRecienteDTO;
import com.hachiko.portal.dto.admin.DashboardStatsDTO;
import com.hachiko.portal.repository.ILoginAttemptRepository;
import com.hachiko.portal.repository.IUsuarioRepository;
import com.hachiko.portal.service.IAdminDashboardService;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.hachiko.portal.config.CacheConfig.CACHE_ADMIN_STATS;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Implementación del servicio de estadísticas del panel de administración.
 * Módulo: Administración.
 *
 * Principio SRP: responsabilidad única — estadísticas y actividad reciente.
 * La gestión de usuarios (listar, cambiar rol, eliminar) se movió a AdminUserServiceImpl.
 */
@Service
public class AdminDashboardServiceImpl implements IAdminDashboardService {

    private static final int LOCK_WINDOW_MINUTES = 15;
    private static final long BLOCKED_THRESHOLD = 3L;
    private static final int ACTIVITY_LIMIT = 10;
    private static final int ACTIVITY_PER_TYPE = 5;

    private final IUsuarioRepository usuarioRepository;
    private final ILoginAttemptRepository loginAttemptRepository;

    public AdminDashboardServiceImpl(IUsuarioRepository usuarioRepository,
                                     ILoginAttemptRepository loginAttemptRepository) {
        this.usuarioRepository = usuarioRepository;
        this.loginAttemptRepository = loginAttemptRepository;
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CACHE_ADMIN_STATS, key = "'global'")
    public DashboardStatsDTO getDashboardStats() {
        LocalDateTime startOfToday = LocalDate.now().atStartOfDay();
        LocalDateTime startOfTomorrow = startOfToday.plusDays(1);
        LocalDateTime since24h = LocalDateTime.now().minusHours(24);
        LocalDateTime sinceWindow = LocalDateTime.now().minusMinutes(LOCK_WINDOW_MINUTES);

        // Estadísticas de conteo.
        long totalUsuarios = usuarioRepository.count();
        long loginHoy = usuarioRepository.countByLastLoginBetween(startOfToday, startOfTomorrow);
        long intentosFallidosHoy = loginAttemptRepository.countByAttemptTimeBetween(startOfToday, startOfTomorrow);
        long cuentasBloqueadas = loginAttemptRepository
                .findBlockedEmails(sinceWindow, BLOCKED_THRESHOLD).size();

        // Construir feed de actividad reciente mezclando 3 fuentes.
        List<ActividadRecienteDTO> actividad = new ArrayList<>();

        // Tipo LOGIN: usuarios con login reciente.
        usuarioRepository.findRecentLogins(since24h, PageRequest.of(0, ACTIVITY_PER_TYPE))
                .forEach(u -> actividad.add(ActividadRecienteDTO.builder()
                        .tipo("LOGIN")
                        .email(u.getEmail())
                        .momento(u.getLastLogin())
                        .descripcion("Inicio de sesión exitoso")
                        .build()));

        // Tipo REGISTRO: usuarios registrados recientemente.
        usuarioRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(since24h, startOfTomorrow)
                .stream()
                .limit(ACTIVITY_PER_TYPE)
                .forEach(u -> actividad.add(ActividadRecienteDTO.builder()
                        .tipo("REGISTRO")
                        .email(u.getEmail())
                        .momento(u.getCreatedAt())
                        .descripcion("Nuevo usuario registrado")
                        .build()));

        // Tipo INTENTO_FALLIDO: intentos de login fallidos recientes.
        loginAttemptRepository.findRecentAttempts(since24h, PageRequest.of(0, ACTIVITY_PER_TYPE))
                .forEach(a -> actividad.add(ActividadRecienteDTO.builder()
                        .tipo("INTENTO_FALLIDO")
                        .email(a.getEmail())
                        .momento(a.getAttemptTime())
                        .descripcion("Intento de login fallido desde " + a.getIpAddress())
                        .build()));

        // Mezclar, ordenar por momento DESC y tomar los 10 más recientes.
        List<ActividadRecienteDTO> top10 = actividad.stream()
                .filter(a -> a.getMomento() != null)
                .sorted(Comparator.comparing(ActividadRecienteDTO::getMomento).reversed())
                .limit(ACTIVITY_LIMIT)
                .toList();

        return DashboardStatsDTO.builder()
                .totalUsuarios(totalUsuarios)
                .loginHoy(loginHoy)
                .intentosFallidosHoy(intentosFallidosHoy)
                .cuentasBloqueadas(cuentasBloqueadas)
                .actividadReciente(top10)
                .build();
    }
}
