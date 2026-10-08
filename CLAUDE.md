# SGT EHS/Visitas Online — regras para o Claude Code

## Projeto
- **SGT EHS/Visitas Online (25d)** — balcão de entrada do MVP do QR. Repositório SEPARADO do SGT.
- Backend Spring Boot **4.1.1** (Java 21, JdbcTemplate, sem JPA), frontend Angular **21**, PostgreSQL **16**
  com Flyway. Um Online por cliente (R17): **sem multi-tenant** — `tenant` é só validação do envelope.
- Fontes de verdade: `prompts/DEC-2026-10-06-sgt-online-visitas.md` e `docs/CONTRATO_CONECTOR_ONLINE.md`
  (no repositório do SGT; **não alterá-los**). Escopo: `claude/prompt-builder-25d-online.md` (no repo do SGT).
- Idioma de código, comentários, commits e docs: **português do Brasil**.

## Regra de ouro — o Online é só o balcão
- O Online **cadastra e transporta**; **não decide nada** sobre autorização de entrada, aprovação ou
  portaria. Fluxo final: QR → formulário → aceite → código → `PRE_CADASTRO_QR` → portaria (SGT) →
  confirma (SGT) → visita pontual (SGT). Proibido reconstruir qualquer lógica do SGT aqui.
- O termo exibido vem EXCLUSIVAMENTE da `CONFIG_SITE` recebida do SGT. **Nunca hardcodar termo.**
- A chave privada da instalação nunca entra no repositório (`*.pem`, `*.pk8.pem` no `.gitignore`).
- `PEND-099`: o SGT envia `tenant`/`site` como IDs numéricos em texto. Trabalhar com esses valores;
  manter a pendência aberta e o contrato intacto.

## Regras de trabalho
- Build/testes sempre com `./mvnw clean verify` (nunca sem `clean`). Frontend: `npx ng test` e `npm run build`.
- Nunca reescrever migration commitada; correção vai em migration nova.
- SQL sempre parametrizado; erros de FK/unique viram 409/400 com mensagem legível.
- Sem `innerHTML`/`bypassSecurityTrust*` no front; só interpolação Angular.
- Interface **mobile-first e responsiva** (celular, tablet, notebook, desktop); sem dimensões fixas;
  áreas de toque ≥ 44 px.
- Nenhuma chamada HTTP a equipamento; só saída (o conector puxa; o Online nunca chama o SGT).
- Logs sem dados pessoais e sem segredos (nunca corpo de mensagem, token, CPF ou aceite).
- Não instalar software nem alterar configuração do sistema sem perguntar.

## Ao terminar cada etapa
Relatório com: commits, migrations, testes (antes/depois), decisões aplicadas, o que NÃO foi feito e
por quê, pendências sugeridas.
