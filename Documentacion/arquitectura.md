# Arquitectura del Sistema Hachiko

## Visión general

Hachiko Portal es una aplicación web de tres capas: un backend REST en Spring Boot, una base de datos PostgreSQL y un frontend SPA en React. La comunicación entre frontend y backend es exclusivamente por HTTP/JSON con autenticación JWT stateless.

```
┌─────────────────────────────┐
│   Frontend (React + Vite)   │
│   SPA en http://localhost:5173  │
└──────────────┬──────────────┘
               │ HTTP/JSON + Bearer JWT
┌──────────────▼──────────────┐
│  Backend (Spring Boot 4)    │
│  API REST en :8080          │
│                             │
│  Controllers                │
│      ↓                      │
│  Services + Validators      │
│      ↓                      │
│  Repositories (Spring Data) │
└──────────────┬──────────────┘
               │ JDBC
┌──────────────▼──────────────┐
│  PostgreSQL (BD: collar)    │
└─────────────────────────────┘
```

---

## Backend

### Capas y responsabilidades

| Paquete | Responsabilidad |
|---|---|
| `controller` | Recibe peticiones HTTP, extrae parámetros, delega al servicio y devuelve `ResponseEntity`. Sin lógica de negocio. |
| `service` (interfaces + impl) | Toda la lógica de negocio. Cada operación de dominio tiene su propia interfaz (ILoginService, IMascotaService, etc.). |
| `service/validation` | Validaciones de dominio reutilizables (UserValidator, MascotaValidator, PropietarioValidator). |
| `repository` | Interfaces Spring Data JPA. Nombres prefijados con `I` (IUsuarioRepository, IPerroRepository, etc.). |
| `domain` | Entidades JPA que mapean la base de datos. Sin lógica de negocio. |
| `dto` | Contratos de entrada (`*Request`) y salida (`*DTO`, `*Response`). Desacoplan la API del modelo de dominio. |
| `exception` | Jerarquía de excepciones de dominio extendidas de `PortalException`. |
| `handler` | `GlobalExceptionHandler` centraliza el mapeo excepción → código HTTP + cuerpo de error. |
| `security` | `JwtTokenProvider` genera y valida tokens. `JwtAuthenticationFilter` inyecta el `userId` en el `SecurityContext` de cada petición autenticada. |
| `config` | `SecurityConfig` (reglas CORS + rutas permitidas) y `AppConfig` (bean `BCryptPasswordEncoder`). |

### Flujo de una petición autenticada

```
HTTP Request
    → JwtAuthenticationFilter         (valida token, inyecta userId)
    → SecurityConfig (autorización)   (verifica rol si aplica)
    → Controller                      (extrae @AuthenticationPrincipal)
    → Service / Validator             (lógica de negocio)
    → Repository                      (acceso a datos)
    → Controller                      (construye ResponseEntity)
HTTP Response
```

### Seguridad

- **Autenticación:** JWT HMAC-SHA256, duración configurable (por defecto 24 h).
- **Contraseñas:** BCrypt con factor de coste por defecto de Spring Security.
- **Logout:** Token añadido a blacklist en memoria (`ConcurrentHashMap`) con limpieza automática programada.
- **Bloqueo de cuenta:** 3 intentos fallidos en 10 minutos bloquean el email o la IP.
- **Recuperación de contraseña:** Token de un solo uso con expiración, enviado por email.
- **CORS:** Orígenes explícitos configurados vía variable de entorno (compatible con `allowCredentials: true`).

### Entidades de dominio principales

```
Usuario (1) ──── (0..1) Propietario
                          │
                          └── (0..1) Residencia → Ciudad → Departamento → País
                          │
                          └── (0..N) Perro ──── Raza
                                        │
                                        └── (0..1) Collar
                                                      │
                                                      └── (0..N) RegistroSensores
                                                      └── (0..N) RegistroComportamiento

Usuario ──── LoginAttempt    (intentos de login por IP/email)
Usuario ──── PasswordReset   (tokens de recuperación)
Usuario ──── Notificacion
```

**Enums:**
- `UserRole`: `USER`, `ADMIN`
- `Genero`: valores del sexo biológico de la mascota
- `EstadoNotificacion`: estado de lectura de notificaciones

---

## Frontend

### Estructura de módulos

| Directorio | Contenido |
|---|---|
| `api/` | Clientes Axios por módulo (`authApi`, `mascotaApi`, `propietarioApi`, `adminApi`, `referenciaApi`). `client.ts` centraliza la instancia de Axios con inyección automática del token JWT. |
| `auth/` | Login, registro, recuperación y reset de contraseña. |
| `dashboard/` | Panel principal del usuario autenticado. |
| `mascotas/` | Listado, formulario y tarjeta de mascota. |
| `propietario/` | Completar perfil (primera vez) y editar perfil existente. |
| `admin/` | Dashboard de administración, tabla y formulario de usuarios. |
| `landing/` | Página pública de presentación. |
| `shared/` | `AuthContext` (estado global de sesión), `PrivateRoute`, `AdminRoute`, `InactivityTimer` (cierra sesión tras 15 min de inactividad), `ApiErrorBoundary`, `Notification`. |

### Gestión de autenticación

1. El token JWT se almacena en `localStorage` tras el login.
2. `client.ts` añade `Authorization: Bearer <token>` a todas las peticiones.
3. Las respuestas `401` redirigen automáticamente al login.
4. `InactivityTimer` cierra la sesión tras 15 minutos sin interacción.
5. Si `loginResponse.requiresProfileCompletion === true`, el usuario es redirigido a `/completar-perfil` antes de acceder al dashboard.

### Flujo de primera sesión

```
Login exitoso
    → requiresProfileCompletion = true  → /completar-perfil → Dashboard
    → requiresProfileCompletion = false → Dashboard
```

---

## Decisiones de arquitectura

| Decisión | Razón |
|---|---|
| JWT stateless | No requiere sesiones en servidor, escala horizontalmente. |
| Token blacklist en memoria | Permite logout real sin base de datos adicional. Se limpia automáticamente. |
| Email `stub` por defecto | Evita envíos accidentales en desarrollo. Se activa Resend vía env var. |
| `ddl-auto: validate` | Protege el esquema en producción. Las migraciones se gestionan manualmente (scripts en `src/main/resources/db/`). |
| Validadores como beans de servicio | Centraliza reglas de negocio reutilizables entre controladores sin duplicar anotaciones `@Valid`. |
| `userId` en SecurityContext, no en body | El frontend nunca envía el userId explícitamente; el servidor lo extrae siempre del token. |
