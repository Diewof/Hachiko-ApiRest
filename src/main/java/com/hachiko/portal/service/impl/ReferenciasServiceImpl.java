package com.hachiko.portal.service.impl;

import com.hachiko.portal.dto.referencia.CiudadDTO;
import com.hachiko.portal.dto.referencia.DepartamentoDTO;
import com.hachiko.portal.dto.referencia.PaisDTO;
import com.hachiko.portal.dto.referencia.PlanDTO;
import com.hachiko.portal.dto.referencia.RazaDTO;
import com.hachiko.portal.repository.ICiudadRepository;
import com.hachiko.portal.repository.IDepartamentoRepository;
import com.hachiko.portal.repository.IPaisRepository;
import com.hachiko.portal.repository.IPlanRepository;
import com.hachiko.portal.repository.IRazaRepository;
import com.hachiko.portal.service.IReferenciasService;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.hachiko.portal.config.CacheConfig.*;

import java.util.List;

/**
 * Implementación del servicio de datos de referencia.
 * Módulo: Ubicación, Mascota, Propietario.
 *
 * Migra getPaises(), getDepartamentos(), getCiudades(), getPlanes() y getRazas()
 * de PropietarioModel.php y MascotaModel.php.
 *
 * Todos los métodos son solo lectura. Candidato a caché en producción.
 */
@Service
@Transactional(readOnly = true)
public class ReferenciasServiceImpl implements IReferenciasService {

    private final IPaisRepository paisRepository;
    private final IDepartamentoRepository departamentoRepository;
    private final ICiudadRepository ciudadRepository;
    private final IRazaRepository razaRepository;
    private final IPlanRepository planRepository;

    public ReferenciasServiceImpl(IPaisRepository paisRepository,
                                  IDepartamentoRepository departamentoRepository,
                                  ICiudadRepository ciudadRepository,
                                  IRazaRepository razaRepository,
                                  IPlanRepository planRepository) {
        this.paisRepository = paisRepository;
        this.departamentoRepository = departamentoRepository;
        this.ciudadRepository = ciudadRepository;
        this.razaRepository = razaRepository;
        this.planRepository = planRepository;
    }

    @Override
    @Cacheable(cacheNames = CACHE_PAISES, key = "'all'")
    public List<PaisDTO> getPaises() {
        return paisRepository.findAllByOrderByNombreAsc()
                .stream()
                .map(p -> PaisDTO.builder()
                        .paisId(p.getPaisId())
                        .nombre(p.getNombre())
                        .build())
                .toList();
    }

    @Override
    @Cacheable(cacheNames = CACHE_DEPARTAMENTOS, key = "#paisId")
    public List<DepartamentoDTO> getDepartamentosByPais(Integer paisId) {
        return departamentoRepository.findByPais_PaisIdOrderByNombreAsc(paisId)
                .stream()
                .map(d -> DepartamentoDTO.builder()
                        .departamentoId(d.getDepartamentoId())
                        .nombre(d.getNombre())
                        .paisId(d.getPais() != null ? d.getPais().getPaisId() : null)
                        .build())
                .toList();
    }

    @Override
    @Cacheable(cacheNames = CACHE_CIUDADES, key = "#departamentoId")
    public List<CiudadDTO> getCiudadesByDepartamento(Integer departamentoId) {
        return ciudadRepository.findByDepartamento_DepartamentoIdOrderByNombreAsc(departamentoId)
                .stream()
                .map(c -> CiudadDTO.builder()
                        .ciudadId(c.getCiudadId())
                        .nombre(c.getNombre())
                        .departamentoId(c.getDepartamento() != null ? c.getDepartamento().getDepartamentoId() : null)
                        .build())
                .toList();
    }

    @Override
    @Cacheable(cacheNames = CACHE_RAZAS, key = "'all'")
    public List<RazaDTO> getRazas() {
        return razaRepository.findAllByOrderByNombreRazaAsc()
                .stream()
                .map(r -> RazaDTO.builder()
                        .razaId(r.getRazaId())
                        .nombreRaza(r.getNombreRaza())
                        .build())
                .toList();
    }

    @Override
    @Cacheable(cacheNames = CACHE_PLANES, key = "'all'")
    public List<PlanDTO> getPlanes() {
        return planRepository.findAllByOrderByCostoAsc()
                .stream()
                .map(p -> PlanDTO.builder()
                        .planId(p.getPlanId())
                        .nombrePlan(p.getNombrePlan())
                        .descripcion(p.getDescripcion())
                        .costo(p.getCosto())
                        .build())
                .toList();
    }
}
