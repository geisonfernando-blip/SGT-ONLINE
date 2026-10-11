/**
 * Base da API pública do Online no ENSAIO na rede local (demonstração de 15/10).
 *
 * <p>O `environment.ts` continua intacto com `http://localhost:8090/api/publico` — que só resolve na
 * própria máquina. No ensaio, quem abre a página é o celular, pela placa do QR: então o backend é
 * chamado no MESMO host, trocando só a porta. Não é preciso compilar com o IP, e o IP pode mudar
 * (reserva de DHCP) sem quebrar o build.
 *
 * <p>Se um dia for preciso apontar para outro host de verdade, é só substituir a linha `apiBase`.
 */
export const environment = {
  production: false,
  apiBase: `http://${location.hostname}:8090/api/publico`,
};