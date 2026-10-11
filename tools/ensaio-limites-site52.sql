-- Perfil de limites do ENSAIO/DEMONSTRAÇÃO para o site 52 (Online 25d).
--
-- POR QUE o limite por IP fica alto: no Docker Desktop o container NÃO vê o IP real do visitante —
-- toda conexão que chega por porta publicada aparece como 172.17.0.1 (medido em 2026-10-11, ver
-- claude/ensaio-rede-local.md seção 1). Como `ipDo()` usa só o `remoteAddr`, limite por IP baixo
-- vira limite para TODO MUNDO junto: o segundo visitante toma 429. Por isso o valor é alto.
--
-- IMPORTANTE: este script NÃO altera `versao` de propósito. Se a `versao` fosse incrementada, a
-- próxima CONFIG_SITE do SGT (versão 6+) seria recusada com 409 VERSAO_OBSOLETA e o ensaio de ponta a
-- ponta deixaria de aplicar. Consequência aceita: um CONFIG_SITE novo do SGT sobrescreve estes
-- valores e o perfil de ensaio precisa ser reaplicado.
--
-- Uso (no compose do Online):
--   docker compose exec -T db psql -U sgt_online -d sgt_online -f /dev/stdin < tools/ensaio-limites-site52.sql
--
-- Para desfazer depois da demonstração: restaurar NULL nos três limites (ou reenviar a CONFIG_SITE).

UPDATE config_site
   SET limite_por_ip_hora       = 200,   -- alto: todo mundo compartilha 172.17.0.1 no Docker Desktop
       limite_por_aparelho_hora = 10,
       limite_por_site_hora     = 100    -- teto real do site: o único limite que protege a portaria
 WHERE site_id = 52;

-- Evidência (sem dado pessoal): deve mostrar 200 / 10 / 100.
SELECT site_id, limite_por_ip_hora, limite_por_aparelho_hora, limite_por_site_hora, versao
  FROM config_site WHERE site_id = 52;