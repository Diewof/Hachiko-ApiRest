package com.hachiko.portal.service.impl;

import com.hachiko.portal.domain.Usuario;
import com.hachiko.portal.domain.enums.UserRole;
import com.hachiko.portal.dto.admin.UserDetailDTO;
import com.hachiko.portal.dto.propietario.PropietarioDTO;
import com.hachiko.portal.dto.usuario.UsuarioDTO;
import com.hachiko.portal.exception.ResourceNotFoundException;
import com.hachiko.portal.exception.ValidationException;
import com.hachiko.portal.repository.IPerroRepository;
import com.hachiko.portal.repository.IPropietarioRepository;
import com.hachiko.portal.repository.IUsuarioRepository;
import com.hachiko.portal.service.IAdminUserService;
import com.hachiko.portal.service.IPropietarioService;
import com.hachiko.portal.service.validation.UserValidator;
import com.hachiko.portal.service.validation.ValidationResult;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.hachiko.portal.config.CacheConfig.CACHE_ADMIN_USUARIOS;

import java.util.List;

/**
 * Implementación del servicio de gestión de usuarios desde el panel de administración.
 * Módulo: Administración.
 *
 * Principio SRP: responsabilidad única — operaciones sobre usuarios.
 * Las estadísticas del dashboard pertenecen a AdminDashboardServiceImpl.
 * Principio DIP: AdminController depende de IAdminUserService, no de esta clase.
 */
@Service
public class AdminUserServiceImpl implements IAdminUserService {

    private final IUsuarioRepository usuarioRepository;
    private final IPropietarioRepository propietarioRepository;
    private final IPropietarioService propietarioService;
    private final IPerroRepository perroRepository;
    private final UserValidator userValidator;

    public AdminUserServiceImpl(IUsuarioRepository usuarioRepository,
                                IPropietarioRepository propietarioRepository,
                                IPropietarioService propietarioService,
                                IPerroRepository perroRepository,
                                UserValidator userValidator) {
        this.usuarioRepository = usuarioRepository;
        this.propietarioRepository = propietarioRepository;
        this.propietarioService = propietarioService;
        this.perroRepository = perroRepository;
        this.userValidator = userValidator;
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CACHE_ADMIN_USUARIOS, key = "'all'")
    public List<UsuarioDTO> getAllUsers() {
        return usuarioRepository.findAll()
                .stream()
                .map(this::toUsuarioDTO)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetailDTO getUserDetail(Integer userId) {
        Usuario usuario = usuarioRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", userId));

        PropietarioDTO propietarioDTO = null;
        long cantidadMascotas = 0;

        // Guard condition: verificar existencia antes de consultar (más preciso que try/catch)
        if (propietarioRepository.existsByUsuario_Id(userId)) {
            try {
                propietarioDTO = propietarioService.getByUserId(userId);
                if (propietarioDTO != null && propietarioDTO.getPropietarioId() != null) {
                    cantidadMascotas = perroRepository.countByPropietario_PropietarioId(
                            propietarioDTO.getPropietarioId());
                }
            } catch (ResourceNotFoundException ignored) {
                // No tiene perfil — propietarioDTO se queda null
            }
        }

        return UserDetailDTO.builder()
                .usuario(toUsuarioDTO(usuario))
                .propietario(propietarioDTO)
                .cantidadMascotas(cantidadMascotas)
                .build();
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CACHE_ADMIN_USUARIOS, key = "'all'")
    public void updateUserRole(Integer userId, String role) {
        ValidationResult validation = userValidator.validateRole(role);
        if (!validation.isValid()) {
            throw new ValidationException(validation.getErrors());
        }

        if (!usuarioRepository.existsById(userId)) {
            throw new ResourceNotFoundException("Usuario", userId);
        }

        usuarioRepository.updateRole(userId, UserRole.valueOf(role.toUpperCase()));
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CACHE_ADMIN_USUARIOS, key = "'all'")
    public void deleteUser(Integer userId) {
        if (!usuarioRepository.existsById(userId)) {
            throw new ResourceNotFoundException("Usuario", userId);
        }
        usuarioRepository.deleteById(userId);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private UsuarioDTO toUsuarioDTO(Usuario u) {
        return UsuarioDTO.builder()
                .id(u.getId())
                .email(u.getEmail())
                .role(u.getRole() != null ? u.getRole().name() : null)
                .createdAt(u.getCreatedAt())
                .lastLogin(u.getLastLogin())
                .build();
    }
}
