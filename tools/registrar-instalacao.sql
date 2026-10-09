-- =====================================================================================================
-- Online (25d) — registra a CHAVE PÚBLICA de uma instalação do conector (sem endpoint administrativo:
-- o contrato §2 não define endpoint de registro). A PRIVADA nunca entra aqui nem no repositório.
--
-- IDEMPOTENTE e seguro de reexecutar:
--   * mesma instalação/chave rodada de novo não grava uma segunda vez (não duplica);
--   * chave diferente para a mesma instalação ATUALIZA a chave (rotação);
--   * sites são adicionados sem duplicar (ON CONFLICT DO NOTHING);
--   * a saída deixa claro qual instalação e quais sites ficaram configurados.
--
-- USO (dev, a partir da raiz do repositório do Online): o arquivo da chave pública vem do VOLUME do
-- conector (conector-chaves, arquivo `instalacao-publica.pem`). Copie-o para dentro do db e rode:
--
--   docker compose cp <caminho>/instalacao-publica.pem db:/tmp/instalacao-publica.pem
--   docker compose exec -T db psql -U sgt_online -d sgt_online -v ON_ERROR_STOP=1 \
--     -v instalacao=sgt-dev-local -v tenant=1 -v nome='Conector dev' -v sites=52 \
--     -v arquivo=/tmp/instalacao-publica.pem -f - < tools/registrar-instalacao.sql
--   docker compose exec -T db rm /tmp/instalacao-publica.pem
--
-- Parâmetros obrigatórios: instalacao (id = SGT_CONECTOR_INSTALACAO = iss do JWT), tenant (id numérico
--   do tenant no SGT), nome, sites (ids numéricos separados por vírgula), arquivo (caminho da chave).
-- =====================================================================================================

\set ON_ERROR_STOP 1
\set chave_publica `cat :'arquivo'`

-- sanidade: não aceitar nada que não seja uma chave PÚBLICA (falha fechada no JWT depois).
-- (feita fora de um bloco $$: o psql NÃO interpola :variaveis dentro de dollar-quoting)
SELECT :'chave_publica' !~ 'BEGIN PUBLIC KEY' AS chave_invalida \gset
\if :chave_invalida
    \echo 'arquivo não contém uma chave pública (BEGIN PUBLIC KEY)'
    \quit 1
\endif

BEGIN;

INSERT INTO instalacao (id, tenant, nome, chave_publica)
VALUES (:'instalacao', :tenant::bigint, :'nome', :'chave_publica')
ON CONFLICT (id) DO UPDATE
   SET chave_publica = EXCLUDED.chave_publica,
       nome = EXCLUDED.nome,
       ativo = TRUE,
       criado_em = instalacao.criado_em
 WHERE instalacao.chave_publica IS DISTINCT FROM EXCLUDED.chave_publica
    OR instalacao.nome IS DISTINCT FROM EXCLUDED.nome
    OR NOT instalacao.ativo;

INSERT INTO instalacao_site (id_instalacao, site_id)
SELECT :'instalacao', s
  FROM unnest(string_to_array(:'sites', ',')::bigint[]) AS t(s)
ON CONFLICT DO NOTHING;

COMMIT;

-- confirmação: deixa claro o que ficou configurado
SELECT i.id AS instalacao, i.tenant, i.ativo, i.chave_publica LIKE '%PUBLIC KEY%' AS chave_publica_ok,
       (SELECT array_agg(site_id ORDER BY site_id) FROM instalacao_site WHERE id_instalacao = i.id) AS sites
  FROM instalacao i
 WHERE i.id = :'instalacao';
