package com.hachiko.portal.service.impl;

import com.hachiko.portal.domain.Perro;
import com.hachiko.portal.domain.Propietario;
import com.hachiko.portal.domain.Raza;
import com.hachiko.portal.domain.enums.Genero;
import com.hachiko.portal.dto.mascota.CreateMascotaRequest;
import com.hachiko.portal.dto.mascota.MascotaDTO;
import com.hachiko.portal.dto.mascota.UpdateMascotaRequest;
import com.hachiko.portal.exception.AccessDeniedException;
import com.hachiko.portal.exception.ResourceNotFoundException;
import com.hachiko.portal.exception.ValidationException;
import com.hachiko.portal.repository.IPerroRepository;
import com.hachiko.portal.repository.IPropietarioRepository;
import com.hachiko.portal.repository.IRazaRepository;
import com.hachiko.portal.service.IMascotaService;
import com.hachiko.portal.service.validation.MascotaValidator;
import com.hachiko.portal.service.validation.ValidationResult;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.hachiko.portal.config.CacheConfig.CACHE_MASCOTAS;

import java.util.List;

/**
 * Implementación del servicio de gestión de mascotas.
 * Módulo: Mascota.
 *
 * Migra las operaciones de MascotaModel.php:
 * lista por propietario, obtener por ID, crear, actualizar y eliminar.
 * Todos los métodos de escritura verifican propiedad antes de operar.
 */
@Service
public class MascotaServiceImpl implements IMascotaService {

    private final IPerroRepository perroRepository;
    private final IPropietarioRepository propietarioRepository;
    private final IRazaRepository razaRepository;
    private final MascotaValidator mascotaValidator;

    public MascotaServiceImpl(IPerroRepository perroRepository,
                              IPropietarioRepository propietarioRepository,
                              IRazaRepository razaRepository,
                              MascotaValidator mascotaValidator) {
        this.perroRepository = perroRepository;
        this.propietarioRepository = propietarioRepository;
        this.razaRepository = razaRepository;
        this.mascotaValidator = mascotaValidator;
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CACHE_MASCOTAS, key = "#propietarioId")
    public List<MascotaDTO> listByPropietario(Integer propietarioId) {
        return perroRepository
                .findByPropietario_PropietarioIdOrderByNombreAsc(propietarioId)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public MascotaDTO getById(Integer perroId, Integer propietarioId) {
        Perro perro = perroRepository.findById(perroId)
                .orElseThrow(() -> new ResourceNotFoundException("Mascota", perroId));

        if (!perroRepository.existsByPerroIdAndPropietario_PropietarioId(perroId, propietarioId)) {
            throw new AccessDeniedException();
        }

        return toDTO(perro);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CACHE_MASCOTAS, key = "#request.propietarioId")
    public MascotaDTO create(CreateMascotaRequest request) {
        // Verificar que el propietario exista.
        Propietario propietario = propietarioRepository.findById(request.getPropietarioId())
                .orElseThrow(() -> new ResourceNotFoundException("Propietario", request.getPropietarioId()));

        // Verificar que la raza exista.
        Raza raza = razaRepository.findById(request.getRazaId())
                .orElseThrow(() -> new ResourceNotFoundException("Raza", request.getRazaId()));

        // Validar campos de la mascota.
        ValidationResult validation = mascotaValidator.validateMascota(
                request.getNombre(),
                request.getFechaNacimiento(),
                request.getPeso(),
                request.getGenero(),
                request.getRazaId()
        );
        if (!validation.isValid()) {
            throw new ValidationException(validation.getErrors());
        }

        // Construir y persistir el Perro.
        Perro perro = Perro.builder()
                .nombre(request.getNombre())
                .fechaNacimiento(request.getFechaNacimiento())
                .peso(request.getPeso())
                .genero(Genero.valueOf(request.getGenero().trim().toUpperCase()))
                .esterilizado(request.getEsterilizado() != null ? request.getEsterilizado() : false)
                .propietario(propietario)
                .raza(raza)
                .build();

        Perro saved = perroRepository.save(perro);
        return toDTO(saved);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CACHE_MASCOTAS, key = "#request.propietarioId")
    public MascotaDTO update(UpdateMascotaRequest request) {
        Perro perro = perroRepository.findById(request.getPerroId())
                .orElseThrow(() -> new ResourceNotFoundException("Mascota", request.getPerroId()));

        // Verificar propiedad.
        if (!perroRepository.existsByPerroIdAndPropietario_PropietarioId(
                request.getPerroId(), request.getPropietarioId())) {
            throw new AccessDeniedException();
        }

        // Verificar que la raza exista.
        Raza raza = razaRepository.findById(request.getRazaId())
                .orElseThrow(() -> new ResourceNotFoundException("Raza", request.getRazaId()));

        // Validar campos.
        ValidationResult validation = mascotaValidator.validateMascota(
                request.getNombre(),
                request.getFechaNacimiento(),
                request.getPeso(),
                request.getGenero(),
                request.getRazaId()
        );
        if (!validation.isValid()) {
            throw new ValidationException(validation.getErrors());
        }

        // Actualizar campos.
        perro.setNombre(request.getNombre());
        perro.setFechaNacimiento(request.getFechaNacimiento());
        perro.setPeso(request.getPeso());
        perro.setGenero(Genero.valueOf(request.getGenero().trim().toUpperCase()));
        perro.setEsterilizado(request.getEsterilizado() != null ? request.getEsterilizado() : false);
        perro.setRaza(raza);

        Perro saved = perroRepository.save(perro);
        return toDTO(saved);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CACHE_MASCOTAS, key = "#propietarioId")
    public void delete(Integer perroId, Integer propietarioId) {
        if (!perroRepository.existsById(perroId)) {
            throw new ResourceNotFoundException("Mascota", perroId);
        }

        if (!perroRepository.existsByPerroIdAndPropietario_PropietarioId(perroId, propietarioId)) {
            throw new AccessDeniedException();
        }

        perroRepository.deleteById(perroId);
    }

    // -------------------------------------------------------------------------
    // Mapeo entidad → DTO
    // Llamar dentro de @Transactional activo (accede raza y propietario LAZY).
    // -------------------------------------------------------------------------

    private MascotaDTO toDTO(Perro p) {
        Integer razaId = null;
        String nombreRaza = null;
        if (p.getRaza() != null) {
            razaId = p.getRaza().getRazaId();
            nombreRaza = p.getRaza().getNombreRaza();
        }

        Integer propietarioId = null;
        if (p.getPropietario() != null) {
            propietarioId = p.getPropietario().getPropietarioId();
        }

        return MascotaDTO.builder()
                .perroId(p.getPerroId())
                .nombre(p.getNombre())
                .fechaNacimiento(p.getFechaNacimiento())
                .peso(p.getPeso())
                .genero(p.getGenero() != null ? p.getGenero().name() : null)
                .esterilizado(p.getEsterilizado())
                .razaId(razaId)
                .nombreRaza(nombreRaza)
                .propietarioId(propietarioId)
                .build();
    }
}
