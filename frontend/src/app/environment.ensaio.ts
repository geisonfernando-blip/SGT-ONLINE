/**
 * Base da API pública do Online no ENSAIO na rede local (demonstração de 15/10).
 *
 * <p>`environment.ts` continua intacto com `http://localhost:8090/api/publico` — que só resolve na
 * própria máquina. No ensaio, quem abre a página é o celular, pela placa do QR, então o backend é
 * chamado pelo IP da rede local da máquina do ensaio.
 *
 * <p><b>Se o IP da máquina mudar, este arquivo tem de ser atualizado</b> (e o `ONLINE_CORS_ORIGENS` do
 * `.env`). Para o ensaio no próprio PC, em vez disso, basta abrir por `localhost:4300` — o
 * `environment.ts` padrão já resolve nesse caso.
 */
export const environment = {
  production: false,
  apiBase: 'http://192.168.1.200:8090/api/publico',
};