# Roteiro do ensaio de ponta a ponta — Online (25d) ↔ Conector ↔ SGT

Checklist operacional do fluxo real (MVP): QR do site → formulário do visitante → aceite LGPD →
código de 6 dígitos → `PRE_CADASTRO_QR` → conector → SGT recebe → portaria busca/confirma →
visita pontual. Sem dados pessoais nos exemplos (só `status`, `codigo_curto`, `site_id`, contagens e
`... IS NULL`).

> Variáveis de apoio (confirmar antes de rodar):
> - `INSTALACAO=sgt-dev-local` · `TENANT=1` · `SITE=52` · `QR=ensaioQR0001`
> - Comandos `online*` rodam no projeto `F:\SGT - Online`; `sgt*` no `F:\SGT - Access`.

---

## Plano de rodadas (ordem obrigatória)

O ensaio é feito em **duas rodadas completas**, porque exercitam caminhos diferentes. A segunda só
começa depois que a primeira passa inteira.

| # | Rodada | Caminho | O que valida de mais |
|---|---|---|---|
| **R1** | E2E no PC | `localhost:4300` (frontend padrão) | o fluxo inteiro, **sem** depender de firewall, CORS de LAN nem placa |
| **R2** | E2E pela placa | celular → `http://192.168.1.200:4300` (`npm run start:ensaio`) | o mesmo fluxo **pela rede**, como o público vai usar: firewall, CORS, placa e `apiBase` |

- **Pré-condição da R2:** firewall aplicado pelo responsável (entrada TCP 4300 e 8090, perfil Private,
  remoto `192.168.1.0/24`). Ver `claude/ensaio-rede-local.md`, seção 3.
- **R2 tem de passar inteira**, ponta a ponta: celular → formulário → `PRE_CADASTRO_QR` → conector →
  SGT → portaria → visita → baixa.
- **No dia da demonstração**, isso vira só uma **confirmação rápida** com um celular (o R1 já having
  provado o fluxo): abrir a placa, enviar, conferir na portaria.
- Roteiro completo de cada rodada, abaixo.

---

## Pré-condições

| # | Item | Comando de conferência | Evidência |
|---|---|---|---|
| P1 | SGT: `db` healthy (5432) | `sgt docker compose ps db` | `healthy` |
| P2 | SGT: `backend` healthy (8080) | `sgt docker compose ps backend` | `healthy` |
| P3 | SGT: `conector` rodando | `sgt docker compose ps conector` | `Up`, sem "ocioso" no log |
| P4 | Online: `db`/`backend`/`frontend` up | `online docker compose ps` | 3× `healthy` |
| P5 | `.env` do SGT tem `SGT_ONLINE_URL` e `SPRING_PROFILES_ACTIVE=dev` | `sgt grep -E 'SGT_ONLINE_URL|SPRING_PROFILES_ACTIVE' .env` | URL `http://host.docker.internal:8090` + `dev` |
| P6 | Instalação registrada no Online | `online docker compose exec -T db psql -U sgt_online -d sgt_online -c "SELECT id, tenant, ativo FROM instalacao WHERE id='sgt-dev-local'"` | `sgt-dev-local \| 1 \| t` |
| P7 | Termo de ensaio configurado no SGT (só dev) | `sgt ... configurar-termo.sql` com arquivo `SEM VALOR JURÍDICO` | 1 versão vigente (abaixo) |

---

## Passo 1 — CONFIG_SITE chega ao Online (SGT → conector → Online)

O SGT publica a `CONFIG_SITE` na outbox quando `site_visita_config`/`termo_lgpd_versao` mudam; o
conector puxa e faz `POST /saida`.

1. Conferir o termo vigente no SGT:
   ```sql
   SELECT termo_id, versao, vigente FROM termo_lgpd_versao WHERE tenant_id = 1 AND vigente;
   ```
   → **evidência:** 1 linha `vigente=t`.

2. Conferir a outbox do SGT (depois de configurar o termo):
   ```sql
   SELECT tipo, chave, versao, enviada_em IS NOT NULL AS enviada
     FROM integracao_mensagem_saida WHERE id_instalacao = (SELECT id FROM integracao_instalacao WHERE identificador='sgt-dev-local')
     ORDER BY criada_em DESC LIMIT 3;
   ```
   → **evidência:** `CONFIG_SITE`, `chave=site:52`, `enviada=t`.

3. Conferir no Online:
   ```sql
   SELECT site_id, ativo, pre_cadastro_expira_horas, versao, termo IS NOT NULL AS tem_termo
     FROM config_site WHERE site_id = 52;
   ```
   → **evidência:** linha presente, `versao ≥ 1`, `tem_termo=t`.

---

## Passo 2 — Formulário abre no Online

```bash
online docker compose exec -T backend curl -fsS "http://localhost:8090/api/publico/config-qr/ensaioQR0001"
```
→ **evidência:** HTTP 200, `"ativo": true`, `"termo"` não-nulo (texto de ensaio) e `"formToken"`.

---

## Passo 3 — Visitante preenche e gera o código

**R1 (no PC):** abrir `http://localhost:4300/q/ensaioQR0001`, preencher (nome + CPF **ou** documento),
marcar o aceite e enviar.

**R2 (pela placa, com o celular):** apontar a câmera para a placa e abrir
`http://192.168.1.200:4300/q/ensaioQR0001`, mesmo preenchimento. Confirmar que o formulário abriu
pelo `start:ensaio` — se abrir pelo `environment.ts` padrão, o celular recebeu erro de CORS.

→ **evidência:** código de 6 dígitos exibido.

Conferência no Online (sem dado pessoal):
```sql
SELECT status, codigo_curto, site_id, cursor FROM pre_cadastro_qr
 WHERE site_id = 52 ORDER BY cursor DESC LIMIT 1;
```
→ **evidência:** `status=PENDENTE`, `codigo_curto` = 6 dígitos.

---

## Passo 4 — Conector entrega o PRE_CADASTRO_QR ao SGT

O conector faz `GET /entrada` (long poll), `POST` no SGT e só então `POST /entrada/ack`.

Conferência no SGT (idempotência + pré-cadastro):
```sql
SELECT status, tipo, correlacao FROM integracao_mensagem_entrada
 WHERE id_instalacao = (SELECT id FROM integracao_instalacao WHERE identificador='sgt-dev-local')
   AND tipo = 'PRE_CADASTRO_QR' ORDER BY recebida_em DESC LIMIT 1;
```
→ **evidência:** `status=APLICADA`, `correlacao` preenchida.

```sql
SELECT status, codigo_curto, site_id FROM pre_cadastro_qr
 WHERE tenant_id = 1 AND site_id = 52 ORDER BY recebido_em DESC LIMIT 1;
```
→ **evidência:** `status=PENDENTE`, mesmo `codigo_curto` do Passo 3.

---

## Passo 5 — Ack e purga (LGPD) no Online

Depois do 2xx do SGT, o conector faz `POST /entrada/ack`; o Online marca `ENTREGUE` e apaga os
dados pessoais.

```sql
SELECT status, codigo_curto,
       nome IS NULL AND cpf IS NULL AND doc_tipo IS NULL AND doc_numero IS NULL
         AND empresa IS NULL AND telefone IS NULL AND email IS NULL
         AND ip IS NULL AND user_agent IS NULL AS dados_apagados
  FROM pre_cadastro_qr WHERE site_id = 52 ORDER BY cursor DESC LIMIT 1;
```
→ **evidência:** `status=ENTREGUE`, `dados_apagados=t`, `codigo_curto` preservado.

---

## Passo 6 — Portaria busca e confirma (SGT)

Pela tela de portaria (SGT), buscar por site + código e confirmar (conferir documento, escolher
anfitrião).

Auditoria (sem dado pessoal):
```sql
SELECT origem, cid, acao FROM auditoria_evento
 WHERE entidade = 'pre_cadastro_qr' AND acao = 'CRIAR' ORDER BY cid;
```
→ **evidência:** `origem=CONECTOR:online`, `cid` = `correlacao` da mensagem.

---

## Passo 7 — Visita pontual criada (SGT)

```sql
SELECT modalidade, status, origem, id_pre_cadastro FROM visita
 WHERE tenant_id = 1 AND site_id = 52 ORDER BY id DESC LIMIT 1;
```
→ **evidência:** `modalidade=PONTUAL`, `status=CONFIRMADA`, `origem=QR`, `id_pre_cadastro` aponta o
pré-cadastro.

```sql
SELECT status FROM pre_cadastro_qr WHERE tenant_id = 1 AND site_id = 52 ORDER BY recebido_em DESC LIMIT 1;
```
→ **evidência:** pré-cadastro `CONFIRMADO` (dados pessoais apagados no Passo 5).

---

## Desligar (fim do ensaio)

1. Desativar o termo de ensaio no SGT: `tools/visitas-mvp/desativar-termo.sql`.
2. Conferir que o Online volta a "cadastro indisponível":
   ```sql
   SELECT site_id, ativo, termo IS NULL AS sem_termo, versao FROM config_site WHERE site_id = 52;
   ```
   → **evidência:** `ativo=t` (não muda), `sem_termo=t`, `versao` incrementada.

---

## Logs (sem dados pessoais)

- Conector: `sgt docker compose logs --tail 200 conector` — procurar o lote entregue e o WARN do
  HTTP (1 vez); **nunca** corpo de mensagem, token, CPF ou aceite.
- Online: `online docker compose logs --tail 200 backend` — healthcheck e erros com só o código do
  contrato.

---

## Checklist final

- [ ] P1–P7 ok (pré-condições)
- [ ] CONFIG_SITE aplicada no Online (`config_site` com termo)
- [ ] formulário abre e gera código de 6 dígitos
- [ ] SGT recebeu `PRE_CADASTRO_QR` (`APLICADA`, pré-cadastro `PENDENTE`)
- [ ] Online purgou após o ack (`ENTREGUE`, dados pessoais nulos)
- [ ] auditoria `CONECTOR:online` + `cid` ponta a ponta
- [ ] visita `PONTUAL`/`CONFIRMADA` no SGT, pré-cadastro `CONFIRMADO`
- [ ] termo de ensaio desativado ao final
