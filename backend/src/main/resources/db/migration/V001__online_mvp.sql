-- V001: SGT EHS/Visitas Online (25d) — balcão de entrada do MVP do QR.
--
-- Um Online por cliente (R17): NÃO há multi-tenant aqui. O `tenant` é guardado só para validar o
-- envelope das mensagens trocadas com o SGT. O termo NÃO tem tabela própria: chega na `config_site.termo`
-- (via CONFIG_SITE) e nunca é embutido em migration/seed.

-- Instalação do conector (iss do JWT). A chave PÚBLICA (EC P-256, PEM) é registrada por script; a
-- privada nunca entra aqui.
CREATE TABLE instalacao (
    id             TEXT PRIMARY KEY,                -- iss = SGT_CONECTOR_INSTALACAO
    tenant         BIGINT NOT NULL,                 -- valida o tenant do envelope do SGT
    nome           TEXT NOT NULL,
    chave_publica  TEXT NOT NULL,                   -- PEM "PUBLIC KEY" (X.509, EC P-256)
    ativo          BOOLEAN NOT NULL DEFAULT TRUE,
    ultimo_cursor  BIGINT,                          -- último cursor confirmado (POST /entrada/ack)
    criado_em      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE instalacao_site (
    id_instalacao  TEXT NOT NULL REFERENCES instalacao(id),
    site_id        BIGINT NOT NULL,                 -- id numérico do site no SGT
    PRIMARY KEY (id_instalacao, site_id)
);

-- Configuração do QR por site, aplicada ao receber CONFIG_SITE (idempotente por chave/versão).
CREATE TABLE config_site (
    site_id                   BIGINT PRIMARY KEY,
    identificador_qr          TEXT NOT NULL UNIQUE,
    ativo                     BOOLEAN NOT NULL,
    pre_cadastro_expira_horas INTEGER NOT NULL,
    codigo_digitos            SMALLINT NOT NULL,
    limite_por_ip_hora        INTEGER,              -- null = sem limite (PEND-096)
    limite_por_aparelho_hora  INTEGER,
    limite_por_site_hora      INTEGER,
    termo                     JSONB,                -- {termoId, termoVersao, sha256Texto, texto} | null
    versao                    BIGINT NOT NULL,      -- versão da chave site:<id>
    atualizado_em             TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT config_site_digitos_chk CHECK (codigo_digitos = 6),
    CONSTRAINT config_site_horas_chk CHECK (pre_cadastro_expira_horas BETWEEN 1 AND 24)
);

-- Inbox SGT → Online (idempotência por id; versão por chave).
CREATE TABLE mensagem_saida (
    id          UUID PRIMARY KEY,
    tipo        TEXT NOT NULL,
    chave       TEXT NOT NULL,
    versao      BIGINT NOT NULL,
    dados       JSONB NOT NULL,
    recebida_em TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Pré-cadastro do QR: é o cadastro do visitante E o outbox do PRE_CADASTRO_QR (1 pré-cadastro = 1
-- mensagem). O `cursor` monotônico dá a fila de entrega por instalação (um Online = uma instalação).
CREATE TABLE pre_cadastro_qr (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    id_externo    TEXT NOT NULL UNIQUE,             -- onl_pre_…
    site_id       BIGINT NOT NULL,
    codigo_curto  TEXT NOT NULL,
    expira_em     TIMESTAMPTZ NOT NULL,
    nome          TEXT NOT NULL,
    cpf           TEXT,
    doc_tipo      TEXT,
    doc_numero    TEXT,
    empresa       TEXT,
    telefone      TEXT,
    email         TEXT,
    termo_id      TEXT NOT NULL,
    termo_versao  BIGINT NOT NULL,
    termo_sha256  TEXT NOT NULL,
    aceito_em     TIMESTAMPTZ NOT NULL,
    ip            TEXT NOT NULL,
    user_agent    TEXT NOT NULL,
    status        TEXT NOT NULL DEFAULT 'PENDENTE',  -- PENDENTE | ENTREGUE | EXPIRADO
    cursor        BIGSERIAL UNIQUE,
    criada_em     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pre_cadastro_codigo_chk CHECK (codigo_curto ~ '^[0-9]{6}$'),
    CONSTRAINT pre_cadastro_status_chk CHECK (status IN ('PENDENTE', 'ENTREGUE', 'EXPIRADO')),
    CONSTRAINT pre_cadastro_sha_chk CHECK (termo_sha256 ~ '^[0-9a-f]{64}$')
);
-- Código único por site entre os pré-cadastros válidos (não expirados).
CREATE UNIQUE INDEX uq_pre_cadastro_codigo ON pre_cadastro_qr (site_id, codigo_curto)
    WHERE status = 'PENDENTE';

-- Anti-replay do JWT (contrato §2): o jti é único dentro da janela.
CREATE TABLE jti_consumido (
    jti       TEXT PRIMARY KEY,
    expira_em TIMESTAMPTZ NOT NULL
);
