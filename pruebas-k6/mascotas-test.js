import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {

  // usuarios concurrentes
  vus: 600,

  // duración de la prueba
  duration: '35s',

  thresholds: {

    // 95% de requests deben responder antes de 2 segundos
    http_req_duration: ['p(95)<4000'], //Variacion de aceptacion

    // al menos 95% de checks exitosos
    checks: ['rate>0.95'],
  },
};

export default function () {


  const loginPayload = JSON.stringify({
    email: 'juan@hachiko.com', //Cambiar por la adecuada
    password: 'password', //Cambiar por la adecuada
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

  // verificar login exitoso
  check(loginResponse, {
    'login successful': (r) => r.status === 200,
  });

  // obtener token JWT
  const token = JSON.parse(loginResponse.body).token;

  // GET /api/mascotas

  const authHeaders = {
    headers: {
      Authorization: `Bearer ${token}`,
    },
  };

  const mascotasResponse = http.get(
    'http://localhost:8080/api/mascotas',
    authHeaders
  );

  // verificamos endpoint
  check(mascotasResponse, {
    'mascotas endpoint success': (r) => r.status === 200,
  });

  // mostrar errores si algo falla
  if (mascotasResponse.status !== 200) {
    console.log('ERROR STATUS:', mascotasResponse.status);
    console.log('ERROR BODY:', mascotasResponse.body);
  }

  // momento para respirar Diego
  sleep(1);
}
