import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {

  // Prueba escalonada: el dashboard de admin ejecuta 5+ queries secuenciales
  // sin caché y con ordenamiento en memoria — el endpoint más costoso del sistema
  stages: [
    { duration: '30s', target: 50 },   // calentamiento
    { duration: '1m',  target: 150 },  // carga sostenida
    { duration: '30s', target: 300 },  // pico de estrés
    { duration: '30s', target: 0 },    // enfriamiento
  ],

  thresholds: {

    // umbral más permisivo: este endpoint es inherentemente lento
    http_req_duration: ['p(95)<6000'],

    // tolerancia de fallos baja
    http_req_failed: ['rate<0.05'],

    // al menos 90% de checks exitosos
    checks: ['rate>0.90'],
  },
};

export default function () {

  // =========================
  // LOGIN COMO ADMIN
  // =========================

  const loginPayload = JSON.stringify({
    email: 'admin@hachiko.com',
    password: 'password',
  });

  const loginParams = {
    headers: {
      'Content-Type': 'application/json',
    },
  };

  const loginResponse = http.post(
    'http://localhost:8080/api/auth/login',
    loginPayload,
    loginParams
  );

  check(loginResponse, {
    'login exitoso': (r) => r.status === 200,
    'token presente': (r) => {
      try { return JSON.parse(r.body).token !== undefined; }
      catch (_) { return false; }
    },
  });

  if (loginResponse.status !== 200) {
    console.log('LOGIN FALLIDO - STATUS:', loginResponse.status);
    return;
  }

  const token = JSON.parse(loginResponse.body).token;

  // =========================
  // GET /api/admin/stats
  // Endpoint crítico: ejecuta múltiples queries secuenciales sobre
  // las tablas Usuario, LoginAttempt y hace ordenamiento en memoria
  // =========================

  const authHeaders = {
    headers: {
      Authorization: `Bearer ${token}`,
    },
  };

  const statsResponse = http.get(
    'http://localhost:8080/api/admin/stats',
    authHeaders
  );

  check(statsResponse, {
    'stats status 200':     (r) => r.status === 200,
    'stats tiene cuerpo':   (r) => r.body && r.body.length > 0,
  });

  if (statsResponse.status !== 200) {
    console.log('STATS ERROR - STATUS:', statsResponse.status);
    console.log('STATS ERROR - BODY:',   statsResponse.body);
  }

  // =========================
  // GET /api/admin/usuarios
  // findAll() sin paginación: carga la tabla completa en memoria
  // =========================

  const usuariosResponse = http.get(
    'http://localhost:8080/api/admin/usuarios',
    authHeaders
  );

  check(usuariosResponse, {
    'usuarios status 200': (r) => r.status === 200,
  });

  if (usuariosResponse.status !== 200) {
    console.log('USUARIOS ERROR - STATUS:', usuariosResponse.status);
    console.log('USUARIOS ERROR - BODY:',   usuariosResponse.body);
  }

  sleep(1);
}
