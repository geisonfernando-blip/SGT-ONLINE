# Resumo para continuidade — SGT EHS/Visitas Online (25d) — 2026-10-08

Repositório: `F:\SGT - Online` (Git, branch `master`). Base documental (repositório do SGT,
**não alterar**): `prompts/DEC-2026-10-06-sgt-online-visitas.md`, `docs/CONTRATO_CONECTOR_ONLINE.md`,
`claude/prompt-builder-25d-online.md`, `claude/estado-ehs-visitas-mvp.md`.

## Onde paramos (estado exato)

- Repositório do Online **criado** e com o **scaffold + todo o código-fonte escrito**.
- **Backend compila** (`./mvnw -DskipTests compile` OK, 21 classes). **Nada mais foi executado.**
- Frontend **escrito**, mas **não buildado/testado** (não rodou `npm install` ainda).
- **Não feito ainda:** testes do backend (integração), `clean verify`, build/teste do frontend,
  `docker compose up`, validação do Flyway, registro da chave pública, integração real com o conector,
  validação visual de responsividade, e **nenhum commit** (a árvore está toda `untracked`).

## Decisões e fatos firmados (não redecidir)

| Item | Decisão |
|---|---|
| Caminho / pacote / banco | `F:\SGT - Online` · `com.sgtaccess.online` · `sgt_online` (usuário `sgt_online`) |
| Portas no host | backend `8090`, frontend `4300`, postgres `5433` |
| Versões | Spring Boot **4.1.1** + Java 21; Angular **21.2** + TS 5.9 + **Vitest**; PostgreSQL **16-alpine**; node 24 / maven 3.9-temurin-21 |
| Integração real | **`host.docker.internal`**: o conector (compose do SGT) alcança o backend do Online em `http://host.docker.internal:8090`. `SGT_ONLINE_URL` (no `.env` do SGT) **tem de ser igual** a `ONLINE_BASE_URL` (no `.env` do Online) — é o `aud` do JWT |
| JWT | ES256 verificado à mão (java.security), **sem** Spring Security e **sem** nimbus; filtro só em `/api/conector/v1/*`; `jti` anti-replay em tabela |
| Cursor | `BIGSERIAL` monotônico (opaco para o SGT/conector) |
| Outbox | `pre_cadastro_qr` É o outbox (1 pré-cadastro = 1 `PRE_CADASTRO_QR`) |
| LGPD | dados pessoais do pré-cadastro apagados no ack (`ENTREGUE`) e na expiração (`EXPIRADO`) |
| Termo | vem SÓ da `CONFIG_SITE` (nunca hardcoded); sem termo → cadastro indisponível |
| Limites do QR | `null` = sem limite (PEND-096), configuráveis, sem hardcode |
| Anti-bot | honeypot (`site_web`) + tempo mínimo (`formToken`, padrão 3 s) |
| Chave pública | via `tools/registrar-instalacao.sql` (idempotente), lendo `instalacao-publica.pem` do volume do conector |
| PEND-099 | `tenant`/`site` são IDs numéricos em texto (`"1"`, `"52"`); manter aberto, contrato intacto |

## Estrutura já escrita

```
backend/src/main/java/com/sgtaccess/online/
  OnlineApplication, ConectorProperties, JwtVerificador, OnlineRepository, Cpf, ErroApi, ApiErros, CorsConfig
  conector/ ConectorJwtFilter, ConectorConfig, ConectorService, ConectorController
  publico/  PreCadastroService, PublicoController, RateLimiter, FormTokenStore
  jobs/     ExpiracaoPreCadastroJob
backend/src/main/resources/  application.yml · db/migration/V001__online_mvp.sql
backend/src/test/java/       (VAZIO — falta escrever os testes)
frontend/src/app/            app*, app.routes, environment, qr.service, features/qr/{qr.ts,html,css,spec.ts}
tools/registrar-instalacao.sql · docker-compose.yml · .env.example · .gitignore · CLAUDE.md
```

## Como continuar (ordem)

1. **Testes do backend** (integração) em `backend/src/test/java/com/sgtaccess/online/`: JWT
   (válido/assinatura/`aud`/`exp`/`jti`), `GET /entrada` (long poll + cursor + ack), `POST /saida`
   CONFIG_SITE (idempotente + `409 VERSAO_OBSOLETA`), formulário (código 6 dígitos, CPF inválido,
   honeypot/tempo, limites), termo null → indisponível. Banco de teste: crie `sgt_online_test` e use
   `ONLINE_DB_URL`/env no teste (padrão do SGT com `sgt_access_test`).
2. **Backend**: `./mvnw clean verify` em container maven (reusar volume `sgt-access-m2`):
   `docker run --rm -v "F:\SGT - Online\backend:/app" -v sgt-access-m2:/root/.m2 -w /app -e ONLINE_DB_URL=jdbc:postgresql://host.docker.internal:5433/sgt_online_test maven:3.9-eclipse-temurin-21 sh -c "./mvnw -B clean verify"`
   (ou contra o `db` do compose do Online na mesma rede). Esperado: 0 falhas.
3. **Frontend**: `docker compose build frontend` (faz `npm install`) e depois `ng test`/`ng build`
   (dentro do container ou via `docker run node`). Conferir o `qr.spec.ts` (3 casos).
4. **Subir**: `docker compose up -d --build` → `db` cria `sgt_online` (e criar `sgt_online_test`);
   Flyway aplica a V001; backend/frontend `healthy`.
5. **Chave pública**: copiar `instalacao-publica.pem` do volume `conector-chaves` do SGT e rodar
   `tools/registrar-instalacao.sql` (instalação `sgt-dev-local`, tenant `1`, site `52`).
6. **CONFIG_SITE**: a outbox do SGT já tem `site:52` (v1–v3). Após a integração, o conector entrega
   ao Online via `POST /saida`.
7. **Integração real** (⚠️ exige autorização): setar `SGT_ONLINE_URL=http://host.docker.internal:8090`
   no `.env` do SGT e reiniciar o conector (`docker compose up -d conector`). NÃO mexer no código do SGT.
   Validar: `/q/ensaioQR0001` → preencher → código → SGT recebe `PRE_CADASTRO_QR` → portaria acha o código.
8. **Responsividade**: validar visualmente mobile (≈375 px), tablet (≈768 px) e desktop (≥1280 px),
   sem rolagem horizontal.
9. **Commit** local (sem push), mensagem em português.

## Riscos / pontos de atenção

- O `npm install` do frontend ainda não foi executado: pode haver ajuste de versão/lockfile.
- `SHA256withECDSAinP1363Format` (JWT) precisa de Java 21 — só em container.
- O `db` do Online e o `db` do SGT são bancos distintos; não cruzar.
- `host.docker.internal` só existe em Docker Desktop (Windows/Mac); em Linux, ajustar.
