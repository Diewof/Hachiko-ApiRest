import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {

  // Endpoints públicos sin autenticación — accesibles antes del login
  // para poblar formularios (países, razas, planes, departamentos, ciudades).
  // Sin paginación ni caché: cada request escanea la tabla completa.
  // Alta concurrencia esperada porque son llamados en la pantalla de registro.
  vus: 400,
  duration: '30s',

  thresholds: {

    // estos endpoints deberían ser rápidos al ser datos estáticos
    http_req_duration: ['p(95)<3000'],

    // tolerancia mínima de fallos
    http_req_failed: ['rate<0.05'],

    // checks con alta exigencia: datos de referencia son críticos para el registro
    checks: ['rate>0.95'],
  },
};

const BASE_URL = 'http://localhost:8080';

export default function () {

  // =========================
  // GET /api/referencia/razas
  // findAllByOrderByNombreRazaAsc(): escaneo completo sin paginación ni caché
  // =========================

  const razasResponse = http.get(`${BASE_URL}/api/referencia/razas`);

  check(razasResponse, {
    'razas status 200':   (r) => r.status === 200,
    'razas tiene datos':  (r) => {
      try { return JSON.parse(r.body).length > 0; }
      catch (_) { return false; }
    },
  });

  if (razasResponse.status !== 200) {
    console.log('RAZAS ERROR - STATUS:', razasResponse.status);
    console.log('RAZAS ERROR - BODY:',   razasResponse.body);
  }

  // =========================
  // GET /api/referencia/paises
  // findAllByOrderByNombreAsc(): escaneo completo sin paginación ni caché
  // =========================

  const paisesResponse = http.get(`${BASE_URL}/api/referencia/paises`);

  check(paisesResponse, {
    'paises status 200':  (r) => r.status === 200,
    'paises tiene datos': (r) => {
      try { return JSON.parse(r.body).length > 0; }
      catch (_) { return false; }
    },
  });

  if (paisesResponse.status !== 200) {
    console.log('PAISES ERROR - STATUS:', paisesResponse.status);
    console.log('PAISES ERROR - BODY:',   paisesResponse.body);
  }

  // =========================
  // GET /api/referencia/planes
  // findAllByOrderByCostoAsc(): datos de suscripción sin caché
  // =========================

  const planesResponse = http.get(`${BASE_URL}/api/referencia/planes`);

  check(planesResponse, {
    'planes status 200':  (r) => r.status === 200,
    'planes tiene datos': (r) => {
      try { return JSON.parse(r.body).length > 0; }
      catch (_) { return false; }
    },
  });

  if (planesResponse.status !== 200) {
    console.log('PLANES ERROR - STATUS:', planesResponse.status);
    console.log('PLANES ERROR - BODY:',   planesResponse.body);
  }

  sleep(1);
}
