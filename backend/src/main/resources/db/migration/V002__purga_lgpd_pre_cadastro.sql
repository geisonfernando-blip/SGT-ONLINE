-- V002: purga LGPD do pré-cadastro — dados pessoais anuláveis + CHECK de consistência.
--
-- A V001 declarou `nome`, `ip` e `user_agent` como NOT NULL, mas `purgarEntregues()` (ack) e
-- `expirarVencidos()` (expiração) anulam esses campos junto com os demais dados pessoais (LGPD,
-- DEC §4/M3). O esquema contradizia o código. Aqui:
--   * os dados pessoais ficam anuláveis;
--   * um CHECK garante que, enquanto PENDENTE, os dados obrigatórios estão presentes e, fora de
--     PENDENTE (ENTREGUE/EXPIRADO), TODOS os dados pessoais estão NULL.
-- A linha persiste com site_id, codigo_curto e expira_em: o código continua único por site entre os
-- PENDENTES (índice parcial `uq_pre_cadastro_codigo` da V001) e a validade continua auditável.

ALTER TABLE pre_cadastro_qr
    ALTER COLUMN nome       DROP NOT NULL,
    ALTER COLUMN ip         DROP NOT NULL,
    ALTER COLUMN user_agent DROP NOT NULL;

-- cpf, doc_tipo, doc_numero, empresa, telefone e email já nasceram anuláveis na V001.

ALTER TABLE pre_cadastro_qr
    ADD CONSTRAINT pre_cadastro_qr_dados_chk CHECK (
        (status = 'PENDENTE'
            AND nome IS NOT NULL
            AND (cpf IS NOT NULL OR (doc_tipo IS NOT NULL AND doc_numero IS NOT NULL)))
        OR (status <> 'PENDENTE'
            AND nome IS NULL AND cpf IS NULL AND doc_tipo IS NULL AND doc_numero IS NULL
            AND empresa IS NULL AND telefone IS NULL AND email IS NULL
            AND ip IS NULL AND user_agent IS NULL));
