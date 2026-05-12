# Health Controller — Documentación

## Descripción general

Módulo de monitoreo del estado del sistema para el portal Hachiko.
Expone dos endpoints REST bajo `/api/health` que permiten verificar si los componentes
críticos de la aplicación están funcionando correctamente.

---

## Archivos creados

### DTOs

| Archivo | Paquete |
|---|---|
| `ComponentStatus.java` | `com.hachiko.portal.dto.health` |
| `HealthResponse.java` | `com.hachiko.portal.dto.health` |
| `HealthDetailResponse.java` | `com.hachiko.portal.dto.health` |

### Servicio

| Archivo | Paquete |
|---|---|
| `IHealthService.java` | `com.hachiko.portal.service` |
| `HealthServiceImpl.java` | `com.hachiko.portal.service.impl` |

### Controlador

| Archivo | Paquete |
|---|---|
| `HealthController.java` | `com.hachiko.portal.controller` |

### Archivo modificado

| Archivo | Cambio |
|---|---|
| `SecurityConfig.java` | Se agregaron las reglas de acceso para `/api/health` y `/api/health/details` |

---

## Endpoints

Base URL: `http://localhost:8080`

---

### GET /api/health — Público

Verifica el estado general del sistema consultando únicamente la base de datos.
No requiere token de autenticación.

Retorna **HTTP 200** si el sistema está operativo y **HTTP 503** si hay un fallo crítico.

**Respuesta 200 — Sistema operativo:**
```json
{
  "status": "UP",
  "timestamp": "2026-04-30T15:00:00Z"
}
```

**Respuesta 503 — Fallo crítico:**
```json
{
  "status": "DOWN",
  "timestamp": "2026-04-30T15:00:00Z"
}
```

---

### GET /api/health/details — Requiere JWT + rol ADMIN

Verifica el estado detallado de cada componente del sistema.
Requiere token JWT de un usuario con rol `ADMIN`.

Retorna **HTTP 200** si todos los componentes están UP y **HTTP 503** si alguno falla.

**Header requerido:**
```
Authorization: Bearer <jwt_token>
```

**Respuesta 200:**
```json
{
  "status": "UP",
  "timestamp": "2026-04-30T15:00:00Z",
  "version": "0.0.1-SNAPSHOT",
  "uptimeSeconds": 3600,
  "components": [
    {
      "name": "database",
      "status": "UP",
      "message": "Conexión activa",
      "responseTimeMs": 14
    },
    {
      "name": "tokenBlacklist",
      "status": "UP",
      "message": "Servicio activo",
      "responseTimeMs": 1
    },
    {
      "name": "emailProvider",
      "status": "UP",
      "message": "Modo stub activo",
      "responseTimeMs": 0
    }
  ]
}
```

**Respuesta 503 — Base de datos caída:**
```json
{
  "status": "DOWN",
  "timestamp": "2026-04-30T15:00:00Z",
  "version": "0.0.1-SNAPSHOT",
  "uptimeSeconds": 120,
  "components": [
    {
      "name": "database",
      "status": "DOWN",
      "message": "Error de conexión: Connection refused",
      "responseTimeMs": 2001
    },
    {
      "name": "tokenBlacklist",
      "status": "UP",
      "message": "Servicio activo",
      "responseTimeMs": 1
    },
    {
      "name": "emailProvider",
      "status": "UP",
      "message": "Modo stub activo",
      "responseTimeMs": 0
    }
  ]
}
```

---

## Componentes verificados

| Componente | Cómo se verifica | Crítico |
|---|---|---|
| `database` | Ejecuta `SELECT 1` con timeout de 2 segundos via `DataSource` | Sí — determina el status general |
| `tokenBlacklist` | Llama `isBlacklisted()` con una sonda ficticia | Sí |
| `emailProvider` | Lee la propiedad `email.provider` del yaml (sin llamada de red) | No |

---

## Reglas de acceso (SecurityConfig)

```
GET /api/health         → público (sin token)
GET /api/health/details → requiere JWT + rol ADMIN
```

---

## Lógica de status general

- Si **cualquier** componente tiene `status: "DOWN"` → respuesta general `"DOWN"` + HTTP 503.
- Si todos los componentes tienen `status: "UP"` → respuesta general `"UP"` + HTTP 200.

El código HTTP 503 permite que balanceadores de carga detecten automáticamente
cuando la aplicación no está disponible.

---

## Pruebas con curl

```bash
# Endpoint público
curl http://localhost:8080/api/health

# Endpoint admin (reemplazar <token> con un JWT de ADMIN)
curl -H "Authorization: Bearer <token>" http://localhost:8080/api/health/details
```
