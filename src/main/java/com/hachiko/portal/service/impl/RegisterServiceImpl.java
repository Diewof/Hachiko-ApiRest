package com.hachiko.portal.service.impl;

import com.hachiko.portal.domain.Usuario;
import com.hachiko.portal.domain.enums.UserRole;
import com.hachiko.portal.dto.auth.RegisterRequest;
import com.hachiko.portal.dto.usuario.UsuarioDTO;
import com.hachiko.portal.exception.ValidationException;
import com.hachiko.portal.repository.IUsuarioRepository;
import com.hachiko.portal.service.IEmailService;
import com.hachiko.portal.service.IPasswordService;
import com.hachiko.portal.service.IRegisterService;
import com.hachiko.portal.service.validation.ValidationResult;
import com.hachiko.portal.service.validation.UserValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.hachiko.portal.config.CacheConfig.CACHE_ADMIN_USUARIOS;

import java.time.LocalDateTime;

/**
 * Implementación del servicio de registro de usuarios.
 * Módulo: Autenticación.
 *
 * Migra el flujo de register() de authmodel.php:
 * validación, hash de contraseña, persistencia y envío de email de bienvenida.
 */
@Service
public class RegisterServiceImpl implements IRegisterService {

    private static final Logger log = LoggerFactory.getLogger(RegisterServiceImpl.class);

    private final IUsuarioRepository usuarioRepository;
    private final UserValidator userValidator;
    private final IPasswordService passwordService;
    private final IEmailService emailService;

    public RegisterServiceImpl(IUsuarioRepository usuarioRepository,
                               UserValidator userValidator,
                               IPasswordService passwordService,
                               IEmailService emailService) {
        this.usuarioRepository = usuarioRepository;
        this.userValidator = userValidator;
        this.passwordService = passwordService;
        this.emailService = emailService;
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CACHE_ADMIN_USUARIOS, key = "'all'")
    public UsuarioDTO register(RegisterRequest request) {
        String email = request.getEmail();
        String password = request.getPassword();
        String confirmPassword = request.getConfirmPassword();

        // Paso 1: Validar formato de email y disponibilidad + longitud de contraseña.
        ValidationResult validation = userValidator.validateNewUser(email, password);
        if (!validation.isValid()) {
            throw new ValidationException(validation.getErrors());
        }

        // Paso 2: Verificar que las contraseñas coinciden.
        if (!password.equals(confirmPassword)) {
            throw new ValidationException("Las contraseñas no coinciden.");
        }

        // Paso 3: Hashear contraseña.
        String hashedPassword = passwordService.encode(password);

        // Paso 4: Construir y persistir Usuario con rol USER.
        Usuario usuario = Usuario.builder()
                .email(email)
                .password(hashedPassword)
                .role(UserRole.USER)
                .createdAt(LocalDateTime.now())
                .build();
        Usuario saved = usuarioRepository.save(usuario);

        // Paso 5: Enviar email de bienvenida (fallo no cancela el registro).
        try {
            emailService.send(
                    email,
                    "Bienvenido a Hachiko",
                    "Hola, tu cuenta ha sido creada exitosamente. " +
                    "Por favor completa tu perfil para comenzar a usar la plataforma."
            );
        } catch (Exception e) {
            log.warn("[REGISTER] Email de bienvenida no enviado a '{}': {}", email, e.getMessage());
        }

        // Paso 6: Retornar DTO del usuario creado.
        return toDTO(saved);
    }

    private UsuarioDTO toDTO(Usuario u) {
        return UsuarioDTO.builder()
                .id(u.getId())
                .email(u.getEmail())
                .role(u.getRole().name())
                .createdAt(u.getCreatedAt())
                .lastLogin(u.getLastLogin())
                .build();
    }
}
