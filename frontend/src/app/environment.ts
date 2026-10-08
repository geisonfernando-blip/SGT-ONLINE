/**
 * Base da API pública do Online (backend Spring Boot). No MVP roda na rede local; a porta/origem é
 * configurável no backend (CORS) — aqui apontamos para a porta padrão do compose.
 */
export const environment = {
  production: false,
  apiBase: 'http://localhost:8090/api/publico',
};
