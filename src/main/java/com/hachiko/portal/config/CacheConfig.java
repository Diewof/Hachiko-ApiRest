package com.hachiko.portal.config;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hachiko.portal.dto.admin.DashboardStatsDTO;
import com.hachiko.portal.dto.mascota.MascotaDTO;
import com.hachiko.portal.dto.referencia.CiudadDTO;
import com.hachiko.portal.dto.referencia.DepartamentoDTO;
import com.hachiko.portal.dto.referencia.PaisDTO;
import com.hachiko.portal.dto.referencia.PlanDTO;
import com.hachiko.portal.dto.referencia.RazaDTO;
import com.hachiko.portal.dto.usuario.UsuarioDTO;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.SimpleCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    public static final String CACHE_PAISES         = "referencia-paises";
    public static final String CACHE_DEPARTAMENTOS  = "referencia-departamentos";
    public static final String CACHE_CIUDADES       = "referencia-ciudades";
    public static final String CACHE_RAZAS          = "referencia-razas";
    public static final String CACHE_PLANES         = "referencia-planes";
    public static final String CACHE_ADMIN_STATS    = "admin-stats";
    public static final String CACHE_ADMIN_USUARIOS = "admin-usuarios";
    public static final String CACHE_MASCOTAS       = "mascotas";

    private final RedisConnectionFactory redisConnectionFactory;

    public CacheConfig(RedisConnectionFactory redisConnectionFactory) {
        this.redisConnectionFactory = redisConnectionFactory;
    }

    @Bean
    @Override
    public CacheManager cacheManager() {
        ObjectMapper mapper = buildRedisObjectMapper();

        RedisCacheConfiguration base = buildBaseConfig(mapper, Object.class);

        Map<String, RedisCacheConfiguration> perCache = new HashMap<>();
        perCache.put(CACHE_PAISES,         listConfig(mapper, PaisDTO.class,        Duration.ofHours(24)));
        perCache.put(CACHE_DEPARTAMENTOS,  listConfig(mapper, DepartamentoDTO.class, Duration.ofHours(24)));
        perCache.put(CACHE_CIUDADES,       listConfig(mapper, CiudadDTO.class,       Duration.ofHours(24)));
        perCache.put(CACHE_RAZAS,          listConfig(mapper, RazaDTO.class,         Duration.ofHours(24)));
        perCache.put(CACHE_PLANES,         listConfig(mapper, PlanDTO.class,         Duration.ofHours(24)));
        perCache.put(CACHE_ADMIN_STATS,    objectConfig(mapper, DashboardStatsDTO.class, Duration.ofSeconds(60)));
        perCache.put(CACHE_ADMIN_USUARIOS, listConfig(mapper, UsuarioDTO.class,      Duration.ofSeconds(30)));
        perCache.put(CACHE_MASCOTAS,       listConfig(mapper, MascotaDTO.class,      Duration.ofMinutes(5)));

        return RedisCacheManager.builder(redisConnectionFactory)
            .cacheDefaults(base.entryTtl(Duration.ofMinutes(10)))
            .withInitialCacheConfigurations(perCache)
            .build();
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new SimpleCacheErrorHandler();
    }

    private ObjectMapper buildRedisObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    private RedisCacheConfiguration listConfig(ObjectMapper mapper, Class<?> elementType, Duration ttl) {
        JavaType type = mapper.getTypeFactory().constructCollectionType(ArrayList.class, elementType);
        return buildBaseConfig(mapper, type).entryTtl(ttl);
    }

    private RedisCacheConfiguration objectConfig(ObjectMapper mapper, Class<?> type, Duration ttl) {
        return buildBaseConfig(mapper, mapper.getTypeFactory().constructType(type)).entryTtl(ttl);
    }

    private RedisCacheConfiguration buildBaseConfig(ObjectMapper mapper, Class<?> type) {
        return buildBaseConfig(mapper, mapper.getTypeFactory().constructType(type));
    }

    private RedisCacheConfiguration buildBaseConfig(ObjectMapper mapper, JavaType javaType) {
        RedisSerializer<Object> json = new RedisSerializer<>() {
            @Override
            public byte[] serialize(Object value) throws SerializationException {
                if (value == null) return null;
                try {
                    return mapper.writeValueAsBytes(value);
                } catch (Exception e) {
                    throw new SerializationException("Error serializando valor para Redis", e);
                }
            }

            @Override
            public Object deserialize(byte[] bytes) throws SerializationException {
                if (bytes == null || bytes.length == 0) return null;
                try {
                    return mapper.readValue(bytes, javaType);
                } catch (Exception e) {
                    throw new SerializationException("Error deserializando valor de Redis", e);
                }
            }
        };

        return RedisCacheConfiguration.defaultCacheConfig()
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(json))
            .disableCachingNullValues();
    }
}
