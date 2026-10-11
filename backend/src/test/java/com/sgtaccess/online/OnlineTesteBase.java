package com.sgtaccess.online;

import static org.assertj.core.api.Assertions.assertThat;

import com.sgtaccess.online.OnlineRepository.PreCadastro;
import com.sgtaccess.online.jobs.ExpiracaoPreCadastroJob;
import com.sgtaccess.online.publico.FormTokenStore;
import com.sgtaccess.online.publico.RateLimiter;
import java.security.KeyPair;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Base dos testes de integração do Online (25d): instalação do conector com chave EC P-256 efêmera,
 * site de ensaio (52), fixtures e limpeza com o prefixo {@code zzvq}. Banco DEDICADO de teste
 * ({@code *_test}); a guarda aborta se apontar para outro banco.
 */
@SpringBootTest
@AutoConfigureMockMvc
abstract class OnlineTesteBase {

    static final String INSTALACAO = "zzvq-inst";
    static final long TENANT = 1L;
    static final long SITE = 52L;
    static final long OUTRO_SITE = 53L;
    static final String IDENTIFICADOR_QR = "zzvqQR00001";
    static final String TERMO_ID = "termo_visitante_lgpd";
    static final String TERMO_SHA = "a".repeat(64);
    static final String TEXTO_TERMO = "zzvq: termo provisorio de fixture (nao e termo)";
    static final String CPF_VALIDO = "529.982.247-25";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired OnlineRepository repo;
    @Autowired RateLimiter rateLimiter;
    @Autowired FormTokenStore formToken;
    @Autowired ConectorProperties props;
    @Autowired ExpiracaoPreCadastroJob expiracao;

    final ObjectMapper om = JsonMapper.builder().build();

    KeyPair par;

    @BeforeEach
    void montarBase() {
        String banco = jdbc.queryForObject("SELECT current_database()", String.class);
        assertThat(banco).as("a suíte de integração exige um banco *_test").endsWith("_test");
        rateLimiter.reset();
        formToken.reset();
        limpar();
        par = JwtTestUtil.gerarP256();
        jdbc.update("INSERT INTO instalacao (id, tenant, nome, chave_publica, ativo) VALUES (?, ?, ?, ?, TRUE)",
                INSTALACAO, TENANT, "zzvq instalacao", JwtTestUtil.publicaPem(
                        (java.security.interfaces.ECPublicKey) par.getPublic()));
        jdbc.update("INSERT INTO instalacao_site (id_instalacao, site_id) VALUES (?, ?)", INSTALACAO, SITE);
    }

    @AfterEach
    void desmontarBase() {
        limpar();
        rateLimiter.reset();
        formToken.reset();
    }

    // ------------------------------------------------------------------ JWT (conector)

    Map<String, Object> claimsValidas() {
        long agora = Instant.now().getEpochSecond();
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("iss", INSTALACAO);
        c.put("aud", props.baseUrl());
        c.put("iat", agora);
        c.put("exp", agora + 60);
        c.put("jti", UUID.randomUUID().toString());
        return c;
    }

    String assinar(Map<String, Object> claims, KeyPair par) {
        return JwtTestUtil.assinar(par, Map.of("alg", "ES256"), claims);
    }

    String jwtValido() {
        return assinar(claimsValidas(), par);
    }

    HttpHeaders auth() {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(jwtValido());
        return h;
    }

    // ------------------------------------------------------------------ fixtures

    /** Insere um pré-cadastro direto na fila (outbox) para os testes do GET /entrada. */
    void inserirPreCadastro(String idExterno, String codigo) {
        Instant agora = Instant.now();
        PreCadastro p = new PreCadastro(UUID.randomUUID(), idExterno, SITE, codigo,
                agora.plus(Duration.ofHours(4)), "zzvq Visitante", "52998224725", null, null, "zzvq Empresa",
                null, null, TERMO_ID, 1, TERMO_SHA, agora, "192.168.1.50", "Mozilla/5.0 zzvq", 0);
        repo.inserirPreCadastro(p);
    }

    /** Configura um site direto na {@code config_site} (sem passar pelo POST /saida). */
    void configurarSite(long siteId, String identificadorQr, boolean ativo, boolean comTermo, Integer limiteIp) {
        String termo = null;
        if (comTermo) {
            ObjectNode t = om.createObjectNode();
            t.put("termoId", TERMO_ID);
            t.put("termoVersao", 1);
            t.put("sha256Texto", TERMO_SHA);
            t.put("texto", TEXTO_TERMO);
            termo = t.toString();
        }
        jdbc.update("""
                INSERT INTO config_site (site_id, identificador_qr, ativo, pre_cadastro_expira_horas,
                        codigo_digitos, limite_por_ip_hora, limite_por_aparelho_hora, limite_por_site_hora,
                        termo, versao)
                VALUES (?, ?, ?, 4, 6, ?, NULL, NULL, ?::jsonb, 1)
                ON CONFLICT (site_id) DO UPDATE SET
                        identificador_qr = EXCLUDED.identificador_qr, ativo = EXCLUDED.ativo,
                        limite_por_ip_hora = EXCLUDED.limite_por_ip_hora, termo = EXCLUDED.termo
                """, siteId, identificadorQr, ativo, limiteIp, termo);
    }

    int contar(String deOnde, Object... args) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM " + deOnde, Integer.class, args);
        return n == null ? 0 : n;
    }

    static String sha256(String texto) {
        try {
            byte[] h = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(texto.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(h.length * 2);
            for (byte b : h) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // ------------------------------------------------------------------ fixtures do contrato

/** Mensagem CONFIG_SITE do contrato §5 para o site de ensaio. */
    ObjectNode configSite(UUID id, long versao, ObjectNode dados) {
        ObjectNode m = om.createObjectNode();
        m.put("id", id.toString());
        m.put("tipo", "CONFIG_SITE");
        m.put("versaoContrato", 1);
        m.put("tenant", String.valueOf(TENANT));
        m.put("site", String.valueOf(SITE));
        m.put("criadoEm", java.time.OffsetDateTime.now().toString());
        m.put("chave", "site:" + SITE);
        m.put("versao", versao);
        m.set("dados", dados);
        return m;
    }

    /** `dados` de uma CONFIG_SITE válida, com ou sem termo. */
    ObjectNode dadosConfig(boolean comTermo) {
        ObjectNode d = om.createObjectNode();
        d.put("identificadorQr", IDENTIFICADOR_QR);
        d.put("ativo", true);
        d.put("preCadastroExpiraHoras", 4);
        d.put("codigoCurtoDigitos", 6);
        ObjectNode limites = d.putObject("limites");
        limites.putNull("porIpHora");
        limites.putNull("porAparelhoHora");
        limites.putNull("porSiteHora");
        if (comTermo) {
            ObjectNode t = d.putObject("termo");
            t.put("termoId", TERMO_ID);
            t.put("termoVersao", 1);
            t.put("sha256Texto", sha256(TEXTO_TERMO));
            t.put("texto", TEXTO_TERMO);
        } else {
            d.putNull("termo");
        }
        return d;
    }

    /** POST /saida autenticado com o lote de mensagens. */
    ResultActions enviarSaida(ObjectNode... mensagens) throws Exception {
        ArrayNode arr = om.createArrayNode();
        for (ObjectNode m : mensagens) {
            arr.add(m);
        }
        ObjectNode corpo = om.createObjectNode();
        corpo.set("mensagens", arr);
        return mvc.perform(MockMvcRequestBuilders.post("/api/conector/v1/saida").headers(auth())
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(corpo)));
    }

    /** Garante a purga completa (LGPD): todos os dados pessoais NULL, identificadores preservados. */
    void assertPurgaCompleta(String idExterno, String statusEsperado) {
        Map<String, Object> l = jdbc.queryForMap("""
                SELECT nome, cpf, doc_tipo, doc_numero, empresa, telefone, email, ip, user_agent,
                       site_id, codigo_curto, expira_em, status
                  FROM pre_cadastro_qr WHERE id_externo = ?
                """, idExterno);
        assertThat(l.get("status")).isEqualTo(statusEsperado);
        assertThat(l.get("nome")).isNull();
        assertThat(l.get("cpf")).isNull();
        assertThat(l.get("doc_tipo")).isNull();
        assertThat(l.get("doc_numero")).isNull();
        assertThat(l.get("empresa")).isNull();
        assertThat(l.get("telefone")).isNull();
        assertThat(l.get("email")).isNull();
        assertThat(l.get("ip")).isNull();
        assertThat(l.get("user_agent")).isNull();
        assertThat(l.get("site_id")).isNotNull();
        assertThat(l.get("codigo_curto")).isNotNull();
        assertThat(l.get("expira_em")).isNotNull();
    }

    void limpar() {
        jdbc.update("DELETE FROM pre_cadastro_qr");
        jdbc.update("DELETE FROM mensagem_saida");
        jdbc.update("DELETE FROM config_site");
        jdbc.update("DELETE FROM instalacao_site");
        jdbc.update("DELETE FROM instalacao");
        jdbc.update("DELETE FROM jti_consumido");
    }
}
