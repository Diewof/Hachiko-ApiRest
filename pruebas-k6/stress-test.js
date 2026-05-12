import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {

  // cantidad de usuarios virtuales concurrentes
  vus: 500,

  // duración total de la prueba
  duration: '20s',

  thresholds: {

    // tiempo promedio permitido
    http_req_duration: ['p(95)<4000'],

    // porcentaje de requests exitosos
    checks: ['rate>0.95'],
  },
};

export default function () {

  // =========================
  // LOGIN
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

  // validar login exitoso
  check(loginResponse, {
    'login successful': (r) => r.status === 200,
  });

  // obtener token JWT
  const token = JSON.parse(loginResponse.body).token;

  // =========================
  // REQUEST AL ENDPOINT PESADO
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

  // validar endpoint
  check(statsResponse, {
    'stats endpoint success': (r) => r.status === 200,
  });

  // pequeña pausa entre iteraciones
  sleep(1);
}
