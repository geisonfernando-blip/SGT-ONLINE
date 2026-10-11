# Revisão de segurança da página pública — Online (25d) — 2026-10-11

Escopo: `backend` (API pública e do conector) e `frontend` da página do QR. Nada do SGT foi tocado.
Nada de funcionalidade nova: só endurecimento, com teste.

## Estado antes

| Item | Situação encontrada |
|---|---|
| Cabeçalhos de segurança | **ausentes em todo o repositório** — nem backend nem frontend |
| CORS | `allowedOriginPatterns("*")` + `allowedHeaders("*")` em `/api/publico/**` |
| Erros | `ApiErros` só tratava `ErroApi`; o resto caía no handler padrão do Spring |
| Log | **zero linhas de log** em todo o backend — sem dado pessoal, mas sem observabilidade nenhuma |
| `X-Aparelho` | aceito verbatim como chave de limite (sem formato nem tamanho) |
| `X-Forwarded-For` | aceito de qualquer origem, sem proxy confiável |
| Honeypot + tempo mínimo | **funcionando** (`site_web` + `formToken` de uso único, 3 s) |
| Limites IP/aparelho/site | **aplicados de fato** (`RateLimiter`, janela fixa 1 h, `null` = sem limite) |

## Achados e tratamento nesta etapa

1. **CORS com curinga** → `ONLINE_CORS_ORIGENS` (lista, padrão `http://localhost:4300`), sem curinga;
   cabeçalhos explícitos (`Content-Type`, `X-Aparelho`); sem credenciais. Commit `599919b`.
2. **Sem cabeçalhos de segurança** → `CabecalhosFilter` em `/api/*`: `X-Content-Type-Options: nosniff`,
   `X-Frame-Options: DENY`, `Referrer-Policy: no-referrer`, `Cache-Control: no-store`.
   Commit `9cc8ffb`. Os cabeçalhos da **página** ficaram de fora (precisam de build): ver
   `proposta-headers-pagina.md`.
3. **Exceção não tratada vazava o handler padrão do Spring** → `@ExceptionHandler(Exception.class)`
   devolve `500 {"codigo":"ERRO_INTERNO","mensagem":"erro interno","correlacao":null}`, sem path,
   timestamp, stack, SQL ou dado pessoal. Commit `13e91d6`.
4. **Observabilidade zero** → log estruturado apenas em `codigo`, `status`, `metodo`, `rota`.
   Nunca IP, token, `jti`, corpo, nome, CPF ou aceite. Commit `990db1f`.
5. **`X-Aparelho` sem validação** → só `[A-Za-z0-9_-]{1,64}`; inválido ou ausente cai para a chave do
   IP. Commit `bede712`.
6. **`meta referrer` no-referrer** na página, como proteção mínima enquanto o `ng serve` estiver no
   lugar. Commit `1e151af`.

## Testes

48 verdes (era 41). Novos: `CorsConfigTest` (3), `SegurancaPaginaPublicaTest` (1), `ErroGenericoTest` (1),
`PreCadastroIntegrationTest` +2 (limite por aparelho e fallback). Um teste existente precisou mudar:
`ConectorIntegrationTest.excecaoDepoisDaAutenticacaoNaoEhEngolidaComo401` afirmava que a exceção
*propagava*; com o handler genérico ela vira 5xx. A intenção do teste (não virar 401) foi mantida, a
asserção passou a checar `5xx + ERRO_INTERNO`.

Rodados: `mvnw -B test -Dtest=...` em container maven, contra `sgt_online_test`. `clean verify` completo
fica para quando a suíte do SGT liberar.

## Pendências abertas (NÃO implementadas)

- **PEND-ONLINE-01 — `X-Forwarded-For` sem proxy confiável.** Hoje a requisição chega **direto** ao
  backend (o `environment.ts` aponta para `http://localhost:8090`, sem `proxyConfig`). Medido em
  2026-10-11: o Docker Desktop faz NAT das portas publicadas, então o container **não vê o IP real** —
  toda conexão externa aparece como `172.17.0.1` (ver `ensaio-rede-local.md`, seção 1). Por isso
  `ipDo()` passou a usar **só** o `remoteAddr` (XFF é ignorado). A pendência fica ligada à proposta de
  mesmo domínio com proxy reverso: quando existir proxy, aceitar XFF **só** se `remoteAddr` estiver
  em `ONLINE_PROXIES_CONFIAVEIS`. Nota: XFF **não** resolve o problema sozinho — o NAT do Docker
  descarta o IP antes de o backend ver a requisição.
- **PEND-ONLINE-02 — retenção de IP para segurança é decisão LGPD em aberto.** O MVP guarda o IP no
  pré-cadastro só até o ack/expiração e **não registra IP em log**. Definir prazo e base legal é do
  responsável; não decidir aqui.
- **PEND-ONLINE-03 — o limite por aparelho é fraco por natureza.** O `X-Aparelho` é controlado pelo
  cliente: validação de formato evita inflar o mapa de limites, mas um cliente pode mandar outro valor
  a cada envio. Os limites que valem mesmo são o **por IP** e o **por site/hora**.
- **PEND-ONLINE-04 — cabeçalhos da página** (CSP, `frame-ancestors`, `Permissions-Policy`) dependem de
  servir o build de produção; proposta em `proposta-headers-pagina.md`.
- **PEND-ONLINE-05 — `RateLimiter` em memória**, janela fixa de 1 h: reinicia com o backend e é por
  instância. Suficiente no MVP (instância única). Em 2026-10-11 ganhou descarte das chaves de janelas
  vencidas ao passar do teto de 50 000 chaves; janela deslizante/persistida só se fizer falta.

## Complemento de 2026-10-11 (após a revisão aprovada)

- `ipDo()` passou a usar **só** o `remoteAddr`; `X-Forwarded-For` é ignorado.
- Lista de origens do CORS vazia só é aceita no perfil `dev`; fora dele o **boot falha**
  (`CorsConfig`), e o compose passou a exigir `ONLINE_CORS_ORIGENS`.
- Erros do próprio Spring (404 de rota, 405 de método, 415 de tipo, 400 de JSON malformado) mantêm o
  status e saem no corpo do contrato; o 401 do filtro JWT passou a incluir `correlacao`.
- `RateLimiter` descarta chaves de janelas vencidas ao passar do teto.
- Testes: 64 verdes.