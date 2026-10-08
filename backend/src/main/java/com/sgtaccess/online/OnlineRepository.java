package com.sgtaccess.online;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Acesso a dados do Online. Um Online por cliente (R17): as tabelas não têm coluna de tenant — o
 * `tenant` é só validação do envelope.
 */
@Repository
public class OnlineRepository {

    public record Instalacao(String id, long tenant, String chavePublica, Long ultimoCursor) {
    }

    public record ConfigSite(long siteId, String identificadorQr, boolean ativo, int expiraHoras,
                             Integer limiteIp, Integer limiteAparelho, Integer limiteSite,
                             String termoJson, long versao) {
    }

    public record PreCadastro(UUID id, String idExterno, long siteId, String codigoCurto,
                              Instant expiraEm, String nome, String cpf, String docTipo, String docNumero,
                              String empresa, String telefone, String email, String termoId,
                              long termoVersao, String termoSha256, Instant aceitoEm, String ip,
                              String userAgent, long cursor) {
    }

    private final JdbcTemplate jdbc;

    public OnlineRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ---------------------------------------------------------------- instalação

    public Optional<Instalacao> instalacaoAtiva(String id) {
        return jdbc.query("""
                SELECT id, tenant, chave_publica, ultimo_cursor FROM instalacao WHERE id = ? AND ativo
                """, (rs, i) -> new Instalacao(rs.getString("id"), rs.getLong("tenant"),
                rs.getString("chave_publica"), rs.getObject("ultimo_cursor", Long.class)), id)
                .stream().findFirst();
    }

    public boolean sitePermitido(String idInstalacao, long siteId) {
        Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM instalacao_site WHERE id_instalacao = ? AND site_id = ?",
                Integer.class, idInstalacao, siteId);
        return n != null && n > 0;
    }

    public void confirmarCursor(String idInstalacao, long cursor) {
        jdbc.update("UPDATE instalacao SET ultimo_cursor = GREATEST(COALESCE(ultimo_cursor, 0), ?) WHERE id = ?",
                cursor, idInstalacao);
    }

    // ---------------------------------------------------------------- anti-replay do JWT

    public boolean consumirJti(String jti, Instant exp) {
        int n = jdbc.update("INSERT INTO jti_consumido (jti, expira_em) VALUES (?, ?) ON CONFLICT (jti) DO NOTHING",
                jti, Timestamp.from(exp));
        return n > 0;
    }

    public void limparJtiExpirados() {
        jdbc.update("DELETE FROM jti_consumido WHERE expira_em < now()");
    }

    // ---------------------------------------------------------------- config do site (CONFIG_SITE)

    public Optional<ConfigSite> configPorIdentificador(String identificadorQr) {
        return jdbc.query("""
                SELECT site_id, identificador_qr, ativo, pre_cadastro_expira_horas,
                       limite_por_ip_hora, limite_por_aparelho_hora, limite_por_site_hora,
                       termo::text, versao
                  FROM config_site WHERE identificador_qr = ?
                """, (rs, i) -> mapaConfig(rs), identificadorQr).stream().findFirst();
    }

    public Optional<ConfigSite> configPorSite(long siteId) {
        return jdbc.query("""
                SELECT site_id, identificador_qr, ativo, pre_cadastro_expira_horas,
                       limite_por_ip_hora, limite_por_aparelho_hora, limite_por_site_hora,
                       termo::text, versao
                  FROM config_site WHERE site_id = ?
                """, (rs, i) -> mapaConfig(rs), siteId).stream().findFirst();
    }

    private static ConfigSite mapaConfig(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ConfigSite(rs.getLong("site_id"), rs.getString("identificador_qr"), rs.getBoolean("ativo"),
                rs.getInt("pre_cadastro_expira_horas"), rs.getObject("limite_por_ip_hora", Integer.class),
                rs.getObject("limite_por_aparelho_hora", Integer.class),
                rs.getObject("limite_por_site_hora", Integer.class), rs.getString("termo"),
                rs.getLong("versao"));
    }

    public boolean mensagemSaidaJaRecebida(UUID id) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM mensagem_saida WHERE id = ?", Integer.class, id);
        return n != null && n > 0;
    }

    public long versaoConfigAplicada(long siteId) {
        Long v = jdbc.query("SELECT versao FROM config_site WHERE site_id = ?",
                (rs, i) -> rs.getLong("versao"), siteId).stream().findFirst().orElse(0L);
        return v == null ? 0L : v;
    }

    /** Aplica a CONFIG_SITE (upsert) e registra a mensagem recebida — na transação do chamador. */
    public void aplicarConfigSite(UUID id, long siteId, String chave, long versao, String identificadorQr,
                                  boolean ativo, int expiraHoras, Integer limiteIp, Integer limiteAparelho,
                                  Integer limiteSite, String termoJson) {
        jdbc.update("""
                INSERT INTO config_site (site_id, identificador_qr, ativo, pre_cadastro_expira_horas,
                        codigo_digitos, limite_por_ip_hora, limite_por_aparelho_hora, limite_por_site_hora,
                        termo, versao, atualizado_em)
                VALUES (?, ?, ?, ?, 6, ?, ?, ?, ?::jsonb, ?, now())
                ON CONFLICT (site_id) DO UPDATE SET
                        identificador_qr = EXCLUDED.identificador_qr, ativo = EXCLUDED.ativo,
                        pre_cadastro_expira_horas = EXCLUDED.pre_cadastro_expira_horas,
                        limite_por_ip_hora = EXCLUDED.limite_por_ip_hora,
                        limite_por_aparelho_hora = EXCLUDED.limite_por_aparelho_hora,
                        limite_por_site_hora = EXCLUDED.limite_por_site_hora,
                        termo = EXCLUDED.termo, versao = EXCLUDED.versao, atualizado_em = now()
                """, siteId, identificadorQr, ativo, expiraHoras, limiteIp, limiteAparelho, limiteSite,
                termoJson == null ? null : termoJson, versao);
        jdbc.update("INSERT INTO mensagem_saida (id, tipo, chave, versao, dados) VALUES (?, 'CONFIG_SITE', ?, ?, ?::jsonb) "
                        + "ON CONFLICT (id) DO NOTHING",
                id, chave, versao, termoJson == null ? "{}" : termoJson);
    }

    // ---------------------------------------------------------------- pré-cadastro (outbox)

    public boolean codigoEmUso(long siteId, String codigo) {
        Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM pre_cadastro_qr WHERE site_id = ? AND codigo_curto = ? AND status = 'PENDENTE'",
                Integer.class, siteId, codigo);
        return n != null && n > 0;
    }

    public void inserirPreCadastro(PreCadastro p) {
        jdbc.update("""
                INSERT INTO pre_cadastro_qr (id, id_externo, site_id, codigo_curto, expira_em, nome, cpf,
                        doc_tipo, doc_numero, empresa, telefone, email, termo_id, termo_versao, termo_sha256,
                        aceito_em, ip, user_agent, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDENTE')
                """, p.id(), p.idExterno(), p.siteId(), p.codigoCurto(), Timestamp.from(p.expiraEm()), p.nome(),
                p.cpf(), p.docTipo(), p.docNumero(), p.empresa(), p.telefone(), p.email(), p.termoId(),
                p.termoVersao(), p.termoSha256(), Timestamp.from(p.aceitoEm()), p.ip(), p.userAgent());
    }

    /** Mensagens com cursor acima do informado (o cursor é opaco, monotônico, por instalação). */
    public List<PreCadastro> proximos(long aposCursor, int limite) {
        return jdbc.query("""
                SELECT id, id_externo, site_id, codigo_curto, expira_em, nome, cpf, doc_tipo, doc_numero,
                       empresa, telefone, email, termo_id, termo_versao, termo_sha256, aceito_em, ip, user_agent, cursor
                  FROM pre_cadastro_qr WHERE cursor > ? ORDER BY cursor LIMIT ?
                """, (rs, i) -> new PreCadastro(UUID.fromString(rs.getString("id")), rs.getString("id_externo"),
                rs.getLong("site_id"), rs.getString("codigo_curto"), rs.getTimestamp("expira_em").toInstant(),
                rs.getString("nome"), rs.getString("cpf"), rs.getString("doc_tipo"), rs.getString("doc_numero"),
                rs.getString("empresa"), rs.getString("telefone"), rs.getString("email"), rs.getString("termo_id"),
                rs.getLong("termo_versao"), rs.getString("termo_sha256"), rs.getTimestamp("aceito_em").toInstant(),
                rs.getString("ip"), rs.getString("user_agent"), rs.getLong("cursor")), aposCursor, limite);
    }

    /** Expira os PENDENTE vencidos e apaga os dados pessoais (LGPD). Devolve quantos expiraram. */
    public int expirarVencidos() {
        return jdbc.update("""
                UPDATE pre_cadastro_qr SET status = 'EXPIRADO',
                        nome = NULL, cpf = NULL, doc_tipo = NULL, doc_numero = NULL, empresa = NULL,
                        telefone = NULL, email = NULL, ip = NULL, user_agent = NULL
                 WHERE status = 'PENDENTE' AND expira_em < now()
                """);
    }

    /** Marca ENTREGUE (ack) e apaga os dados pessoais (LGPD). Devolve quantos foram entregues. */
    public int purgarEntregues(long ateCursor) {
        return jdbc.update("""
                UPDATE pre_cadastro_qr SET status = 'ENTREGUE',
                        nome = NULL, cpf = NULL, doc_tipo = NULL, doc_numero = NULL, empresa = NULL,
                        telefone = NULL, email = NULL, ip = NULL, user_agent = NULL
                 WHERE status = 'PENDENTE' AND cursor <= ?
                """, ateCursor);
    }
}
