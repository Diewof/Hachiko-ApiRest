# Hachiko Portal

Portal web para monitoreo de salud canina mediante collares con sensores. Los propietarios gestionan sus mascotas y visualizan métricas de actividad, frecuencia cardíaca y comportamiento en tiempo real.

## Stack

| Capa | Tecnología |
|---|---|
| Backend | Spring Boot 4.0.5, Java 21 |
| Base de datos | PostgreSQL |
| Seguridad | Spring Security + JWT (JJWT 0.12.6, HMAC-SHA256) |
| ORM | Spring Data JPA / Hibernate |
| Frontend | React 18, TypeScript, Vite 5, React Router 6, Axios |

## Prerrequisitos

- Java 21
- Maven 3.9+
- PostgreSQL 15+ con base de datos `collar` creada
- Node.js 20+ (para el frontend)

## Configuración

Las variables de entorno con sus valores por defecto (desarrollo local):

| Variable | Por defecto | Descripción |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/collar` | URL de conexión PostgreSQL |
| `DB_USERNAME` | `postgres` | Usuario de base de datos |
| `DB_PASSWORD` | `admin` | Contraseña de base de datos |
| `JWT_SECRET` | `hachiko-portal-dev-secret-key-must-be-at-least-32-chars` | Clave HMAC-SHA256 (mín. 32 chars) |
| `JWT_EXPIRATION_MS` | `86400000` | Duración del token en ms (24 h por defecto) |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | Orígenes permitidos, separados por coma |
| `EMAIL_PROVIDER` | `stub` | `stub` (solo logs) o `resend` (emails reales) |
| `RESEND_API_KEY` | — | Requerido si `EMAIL_PROVIDER=resend` |
| `EMAIL_FROM` | `Hachiko <onboarding@resend.dev>` | Dirección remitente |
| `PORT` | `8080` | Puerto del servidor |

Para desarrollo local, copia `application-local.yaml` y sobreescribe solo lo necesario.

## Ejecutar

**Backend:**
```bash
./mvnw spring-boot:run
# o con perfil específico
SPRING_PROFILES_ACTIVE=local ./mvnw spring-boot:run
```

**Frontend:**
```bash
cd FrontEnd
npm install
npm run dev       # dev server en http://localhost:5173
npm run build     # producción → dist/
```

## Estructura del proyecto

```
portal/
├── src/main/java/com/hachiko/portal/
│   ├── config/          # Spring Security y beans globales
│   ├── controller/      # Endpoints REST (5 controladores)
│   ├── domain/          # Entidades JPA (23 clases + 3 enums)
│   ├── dto/             # Contratos de request/response (20 DTOs)
│   ├── exception/       # Excepciones de dominio (8 clases)
│   ├── handler/         # GlobalExceptionHandler
│   ├── repository/      # Interfaces Spring Data JPA (13)
│   ├── security/        # JwtTokenProvider + JwtAuthenticationFilter
│   └── service/         # Interfaces, validadores e implementaciones
├── FrontEnd/src/
│   ├── api/             # Clientes Axios por módulo
│   ├── auth/            # Páginas de login, registro y reset de contraseña
│   ├── dashboard/       # Panel principal del usuario
│   ├── mascotas/        # Gestión de mascotas
│   ├── propietario/     # Perfil del propietario
│   ├── admin/           # Panel de administración
│   └── shared/          # AuthContext, rutas protegidas, notificaciones
└── Documentacion/
    ├── arquitectura.md  # Arquitectura y patrones del sistema
    ├── api-referencia.md # Referencia completa de la API REST
    └── produccion/
        └── ADR-04-jwt_seguridad.md
```

## Documentación

- [Arquitectura](Documentacion/arquitectura.md) — capas, patrones y decisiones de diseño
- [API REST](Documentacion/api-referencia.md) — todos los endpoints con ejemplos
- [ADR-04: Seguridad JWT](Documentacion/produccion/ADR-04-jwt_seguridad.md) — decisiones de autenticación
