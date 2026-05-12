# API REST — Referencia

Base URL: `http://localhost:8080`

## Autenticación

La mayoría de endpoints requieren el header:
```
Authorization: Bearer <jwt_token>
```

Los endpoints marcados como **Público** no requieren token.

---

## Módulo Auth — `/api/auth`

### POST /api/auth/login — Público

Autentica al usuario y devuelve un JWT.

**Body:**
```json
{
  "email": "usuario@ejemplo.com",
  "password": "contraseña"
}
```

**Respuesta 200:**
```json
{
  "userId": 1,
  "email": "usuario@ejemplo.com",
  "role": "USER",
  "token": "<jwt>",
  "expiresIn": 86400,
  "requiresProfileCompletion": false
}
```

Si `requiresProfileCompletion: true`, el frontend debe redirigir a `/completar-perfil` antes del dashboard.

**Errores:** `401` credenciales inválidas, `423` cuenta bloqueada.

---

### POST /api/auth/register — Público

Registra un nuevo usuario.

**Body:**
```json
{
  "email": "usuario@ejemplo.com",
  "password": "contraseña",
  "nombre": "Nombre"
}
```

**Respuesta 201:**
```json
{
  "userId": 2,
  "email": "usuario@ejemplo.com",
  "nombre": "Nombre",
  "role": "USER"
}
```

**Errores:** `409` email ya registrado, `422` validación.

---

### POST /api/auth/logout — Requiere JWT

Revoca el token activo y limpia los intentos de login.

**Respuesta 200:**
```json
{ "message": "Has cerrado sesión correctamente." }
```

---

### POST /api/auth/forgot-password — Público

Solicita un email de recuperación. Siempre responde 200 (no revela si el email existe).

**Body:**
```json
{ "email": "usuario@ejemplo.com" }
```

**Respuesta 200:**
```json
{ "message": "Si el email existe, recibirás las instrucciones de recuperación." }
```

---

### POST /api/auth/reset-password — Público

Aplica el cambio de contraseña con el token recibido por email.

**Body:**
```json
{
  "token": "<token_de_recuperacion>",
  "newPassword": "nuevaContraseña"
}
```

**Respuesta 200:**
```json
{ "message": "Contraseña actualizada exitosamente." }
```

**Errores:** `400` token inválido o expirado.

---

## Módulo Propietario — `/api/propietario`

### GET /api/propietario/me — Requiere JWT

Retorna el perfil completo del propietario autenticado.

**Respuesta 200:**
```json
{
  "propietarioId": 1,
  "usuarioId": 1,
  "nombre": "Carlos",
  "apellido": "Pérez",
  "telefono": "3001234567",
  "residencia": {
    "residenciaId": 1,
    "direccion": "Calle 123",
    "ciudad": { "ciudadId": 1, "nombre": "Bogotá" },
    "departamento": { "departamentoId": 1, "nombre": "Cundinamarca" },
    "pais": { "paisId": 1, "nombre": "Colombia" }
  }
}
```

**Errores:** `404` perfil no creado aún.

---

### POST /api/propietario — Requiere JWT

Crea el perfil por primera vez. El `usuarioId` se toma del token.

**Body:**
```json
{
  "nombre": "Carlos",
  "apellido": "Pérez",
  "telefono": "3001234567",
  "direccion": "Calle 123",
  "ciudadId": 1
}
```

**Respuesta 201:** igual a `GET /api/propietario/me`.

**Errores:** `409` perfil ya existe, `422` validación.

---

### PUT /api/propietario/{propietarioId} — Requiere JWT

Actualiza el perfil existente.

**Body:** mismos campos que el POST (todos opcionales, solo se actualizan los enviados).

**Respuesta 200:** perfil actualizado.

---

## Módulo Mascotas — `/api/mascotas`

### GET /api/mascotas — Requiere JWT

Lista todas las mascotas del propietario autenticado, ordenadas por nombre.

**Respuesta 200:**
```json
[
  {
    "perroId": 1,
    "nombre": "Max",
    "edad": 3,
    "genero": "MACHO",
    "raza": { "razaId": 1, "nombre": "Labrador" },
    "plan": { "planId": 1, "nombre": "Básico", "costo": 0 }
  }
]
```

---

### GET /api/mascotas/{perroId} — Requiere JWT

Detalle de una mascota. Verifica que pertenezca al propietario autenticado.

**Respuesta 200:** mismo formato que el elemento del listado.

**Errores:** `404` mascota no encontrada, `403` mascota de otro propietario.

---

### POST /api/mascotas — Requiere JWT

Registra una nueva mascota. El `propietarioId` se toma del token.

**Body:**
```json
{
  "nombre": "Max",
  "edad": 3,
  "genero": "MACHO",
  "razaId": 1,
  "planId": 1
}
```

**Respuesta 201:** mascota creada.

---

### PUT /api/mascotas/{perroId} — Requiere JWT

Actualiza una mascota existente.

**Body:** mismos campos que el POST.

**Respuesta 200:** mascota actualizada.

**Errores:** `403` si la mascota pertenece a otro propietario.

---

### DELETE /api/mascotas/{perroId} — Requiere JWT

Elimina una mascota.

**Respuesta 204** (sin cuerpo).

**Errores:** `403` si la mascota pertenece a otro propietario.

---

## Módulo Admin — `/api/admin` — Requiere JWT + rol ADMIN

### GET /api/admin/stats

Estadísticas generales del sistema.

**Respuesta 200:**
```json
{
  "totalUsuarios": 50,
  "totalMascotas": 120,
  "totalCollares": 80,
  "actividadReciente": [
    {
      "tipo": "REGISTRO",
      "descripcion": "Nuevo usuario registrado",
      "fecha": "2024-01-15T10:30:00"
    }
  ]
}
```

---

### GET /api/admin/usuarios

Lista todos los usuarios del sistema.

**Respuesta 200:** array de `UsuarioDTO`.

---

### GET /api/admin/usuarios/{userId}

Detalle completo de un usuario: cuenta, perfil propietario y mascotas.

**Respuesta 200:**
```json
{
  "usuario": { "userId": 1, "email": "...", "role": "USER" },
  "propietario": { ... },
  "mascotas": [ ... ]
}
```

---

### POST /api/admin/usuarios

Crea un usuario desde el panel admin (mismo contrato que `POST /api/auth/register`).

**Respuesta 201:** `UsuarioDTO`.

---

### PUT /api/admin/usuarios/{userId}/role

Cambia el rol de un usuario.

**Body:**
```json
{ "role": "ADMIN" }
```

Valores válidos: `"USER"`, `"ADMIN"`.

**Respuesta 200:**
```json
{ "message": "Rol actualizado correctamente." }
```

---

### DELETE /api/admin/usuarios/{userId}

Elimina permanentemente un usuario y sus datos asociados (cascada en BD).

**Respuesta 204** (sin cuerpo).

---

## Módulo Referencia — `/api/referencia` — Público

Catálogos para poblar formularios. No requieren autenticación.

### GET /api/referencia/paises

**Respuesta 200:**
```json
[{ "paisId": 1, "nombre": "Colombia" }]
```

---

### GET /api/referencia/departamentos?paisId={id}

**Respuesta 200:**
```json
[{ "departamentoId": 1, "nombre": "Cundinamarca", "paisId": 1 }]
```

---

### GET /api/referencia/ciudades?departamentoId={id}

**Respuesta 200:**
```json
[{ "ciudadId": 1, "nombre": "Bogotá", "departamentoId": 1 }]
```

---

### GET /api/referencia/razas

**Respuesta 200:**
```json
[{ "razaId": 1, "nombre": "Labrador" }]
```

---

### GET /api/referencia/planes

**Respuesta 200:**
```json
[{ "planId": 1, "nombre": "Básico", "descripcion": "...", "costo": 0 }]
```

---

## Respuestas de error

Todos los errores siguen el mismo formato, manejado por `GlobalExceptionHandler`:

```json
{
  "status": 404,
  "error": "Not Found",
  "message": "Mascota con id 99 no encontrada.",
  "timestamp": "2024-01-15T10:30:00"
}
```

| Código | Cuándo |
|---|---|
| `400` | Token de reset inválido o expirado |
| `401` | Credenciales incorrectas / token JWT ausente o inválido |
| `403` | Recurso de otro propietario / rol insuficiente |
| `404` | Recurso no encontrado |
| `409` | Email ya registrado / perfil ya existe |
| `422` | Errores de validación del body |
| `423` | Cuenta bloqueada por exceso de intentos fallidos |
