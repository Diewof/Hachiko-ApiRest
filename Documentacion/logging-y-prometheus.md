# Logging Estructurado y Prometheus — Hachiko Portal

## Contexto

El backend de Hachiko Portal (Spring Boot 4.0.5, Java 21) no contaba con logging estructurado ni con métricas expuestas. Esta documentación describe la configuración básica añadida para cubrir:

- **Logging estructurado** con Logback + `logstash-logback-encoder` (equivalente a Winston en Node.js)
- **Métricas HTTP, JVM y pool de BD** expuestas en `/actuator/prometheus` via Micrometer
- **Trazabilidad por request** mediante `correlationId` en MDC y header de respuesta

---

## Archivos modificados

### `pom.xml`

Se añadieron tres dependencias al bloque `<dependencies>`:

```xml
<!-- Logging estructurado JSON -->
<dependency>
  <groupId>net.logstash.logback</groupId>
  <artifactId>logstash-logback-encoder</artifactId>
  <version>8.0</version>
</dependency>

<!-- Actuator: expone endpoints de métricas y salud -->
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>

<!-- Prometheus registry via Micrometer -->
<dependency>
  <groupId>io.micrometer</groupId>
  <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

---

### `src/main/resources/application.yaml`

Se añadieron dos secciones nuevas:

```yaml
# ─── Actuator + Prometheus ────────────────────────────────────────────────────
management:
  endpoints:
    web:
      base-path: /actuator
      exposure:
        include: health, prometheus, info
  endpoint:
    health:
      show-details: never
  metrics:
    tags:
      application: ${spring.application.name}
      env: ${spring.profiles.active:default}

# ─── Logging ──────────────────────────────────────────────────────────────────
logging:
  level:
    root: INFO
    "[com.hachiko.portal]": DEBUG
```

**Notas:**
- `show-details: never` en el actuator health evita duplicar la lógica ya existente en `/api/health/details` (admin).
- Los tags `application` y `env` se propagan automáticamente a todas las métricas de Micrometer.
- El nivel `DEBUG` para `com.hachiko.portal` solo aplica en perfil `local`; en `prod` el `logback-spring.xml` lo sobreescribe a `INFO`.

---

### `src/main/java/com/hachiko/portal/config/SecurityConfig.java`

Se añadieron tres reglas al bloque `authorizeHttpRequests`, antes de las rutas existentes:

```java
// Actuator: health público, prometheus y resto solo ADMIN
.requestMatchers("/actuator/health").permitAll()
.requestMatchers("/actuator/prometheus", "/actuator/**").hasRole("ADMIN")
```

**Motivo:** `/actuator/prometheus` expone datos internos de la JVM y del pool de BD; restringirlo a `ROLE_ADMIN` evita filtrar información sensible.

---

## Archivos creados

### `src/main/resources/logback-spring.xml`

Configura el formato de logs según el perfil activo de Spring:

```xml
<configuration>

  <!-- Perfil local/dev: texto plano con colores -->
  <springProfile name="local,default">
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
      <encoder>
        <pattern>%d{HH:mm:ss.SSS} [%thread] %highlight(%-5level) %cyan(%logger{36}) - %msg%n</pattern>
      </encoder>
    </appender>
    <logger name="com.hachiko.portal" level="DEBUG"/>
    <root level="INFO"><appender-ref ref="CONSOLE"/></root>
  </springProfile>

  <!-- Perfil prod: JSON estructurado (ingestable por Loki / ELK) -->
  <springProfile name="prod">
    <appender name="JSON_CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
      <encoder class="net.logstash.logback.encoder.LogstashEncoder">
        <includeContext>false</includeContext>
        <timestampPattern>yyyy-MM-dd'T'HH:mm:ss.SSSZZ</timestampPattern>
      </encoder>
    </appender>
    <logger name="com.hachiko.portal" level="INFO"/>
    <root level="INFO"><appender-ref ref="JSON_CONSOLE"/></root>
  </springProfile>

</configuration>
```

| Perfil | Formato | Nivel app |
|---|---|---|
| `local` / `default` | Texto plano con colores | DEBUG |
| `prod` | JSON estructurado | INFO |

El `correlationId` cargado en MDC por `RequestLoggingFilter` se incluye automáticamente en cada línea JSON cuando se usa `LogstashEncoder`.

---

### `src/main/java/com/hachiko/portal/filter/RequestLoggingFilter.java`

`OncePerRequestFilter` que se ejecuta con máxima prioridad en cada request:

```java
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String correlationId = UUID.randomUUID().toString();
        long startTime = System.currentTimeMillis();

        MDC.put("correlationId", correlationId);
        response.setHeader("X-Correlation-Id", correlationId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            log.info("method={} uri={} status={} duration={}ms correlationId={}",
                    request.getMethod(), request.getRequestURI(),
                    response.getStatus(), duration, correlationId);
            MDC.clear();
        }
    }
}
```

**Responsabilidades:**
- Genera un `UUID` único por request como `correlationId`.
- Lo inyecta en el MDC de SLF4J para que aparezca en todos los logs de esa thread.
- Lo devuelve al cliente como header `X-Correlation-Id`.
- Emite una línea de log con método, URI, status HTTP y duración al finalizar el request.
- Limpia el MDC con `MDC.clear()` al terminar para evitar filtraciones entre requests.

---

### `src/main/java/com/hachiko/portal/config/MetricsConfig.java`

Punto central para registrar métricas custom futuras. Actualmente añade el tag `app` a todas las métricas:

```java
@Configuration
public class MetricsConfig {

    private final MeterRegistry meterRegistry;

    @Value("${spring.application.name:hachiko-portal}")
    private String appName;

    public MetricsConfig(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    void init() {
        meterRegistry.config().commonTags("app", appName);
    }
}
```

**Nota:** `MeterRegistryCustomizer` fue eliminado en Spring Boot 4.x. La configuración de tags programática se hace directamente sobre el bean `MeterRegistry` via `@PostConstruct`. Los tags `application` y `env` se configuran además desde `application.yaml` bajo `management.metrics.tags`.

---

## Separación de responsabilidades

| Capa | Archivo | Responsabilidad |
|---|---|---|
| Filter | `RequestLoggingFilter` | Log por request + correlationId en MDC |
| Config / XML | `logback-spring.xml` | Formato de salida de logs por perfil |
| Config / Java | `MetricsConfig` | Tags globales y métricas custom futuras |
| Config / Java | `SecurityConfig` | Control de acceso a endpoints de actuator |
| Config / YAML | `application.yaml` | Exposición de endpoints y tags automáticos |

---

## Verificación

### Health
```
GET http://localhost:8080/api/health
→ 200 {"status":"UP"}
→ Header en respuesta: X-Correlation-Id: <uuid>
```

### Logging
En la consola de la app, tras cualquier request, aparece:
```
HH:mm:ss.SSS [thread] INFO  c.h.p.filter.RequestLoggingFilter - method=GET uri=/api/health status=200 duration=5ms correlationId=<uuid>
```

### Prometheus
```
GET http://localhost:8080/actuator/prometheus
Authorization: Bearer <token-ADMIN>
→ 200 texto con métricas
→ Sin token o sin rol ADMIN → 403
```

Para activar logs JSON:
```
./mvnw spring-boot:run -Dspring-boot.run.profiles=prod
```
