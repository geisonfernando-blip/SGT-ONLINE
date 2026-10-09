package com.sgtaccess.online;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.KeyPair;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Lado conector do contrato §2/§3: autenticação JWT ES256 (assinatura/aud/exp/jti), {@code GET /entrada}
 * (long poll + cursor + ack + reenvio sem ack) e {@code POST /saida} CONFIG_SITE (idempotente + versão
 * obsoleta). Os pré-cadastros de fixture têm o prefixo {@code zzvq}.
 */
class ConectorIntegrationTest extends OnlineTesteBase {

    // ------------------------------------------------------------------ autenticação (JWT)

    @Test
    void semTokenOuTokenRuimDevolve401() throws Exception {
        mvc.perform(post("/api/conector/v1/heartbeat")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/conector/v1/heartbeat").header("Authorization", "Bearer xyz"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void jwtValidoAbreHeartbeat() throws Exception {
        heartbeat(jwtValido()).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void assinaturaDeOutraChaveDevolve401() throws Exception {
        KeyPair outro = JwtTestUtil.gerarP256();
        heartbeat(assinar(claimsValidas(), outro)).andExpect(status().isUnauthorized());
    }

    @Test
    void audDivergenteDevolve401() throws Exception {
        Map<String, Object> c = claimsValidas();
        c.put("aud", "http://outra-url:8090");
        heartbeat(assinar(c, par)).andExpect(status().isUnauthorized());
    }

    @Test
    void expExpiradoDevolve401() throws Exception {
        Map<String, Object> c = claimsValidas();
        c.put("exp", Instant.now().getEpochSecond() - 60);
        heartbeat(assinar(c, par)).andExpect(status().isUnauthorized());
    }

    @Test
    void instalacaoDesconhecidaDevolve401() throws Exception {
        Map<String, Object> c = claimsValidas();
        c.put("iss", "instalacao-inexistente");
        heartbeat(assinar(c, par)).andExpect(status().isUnauthorized());
    }

    @Test
    void jtiRepetidoDevolve401NaSegundaVez() throws Exception {
        Map<String, Object> c = claimsValidas();
        c.put("jti", UUID.randomUUID().toString());
        String token = assinar(c, par);
        heartbeat(token).andExpect(status().isOk());
        heartbeat(token).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ GET /entrada (long poll + cursor + ack)

    @Test
    void entradaEntregaLoteComCursorEAposOCursorFicaVazio() throws Exception {
        inserirPreCadastro("onl_pre_zzvq01", "100001");
        inserirPreCadastro("onl_pre_zzvq02", "100002");

        JsonNode corpo = entrada(null, null, null);
        assertThat(corpo.get("mensagens").size()).isEqualTo(2);
        assertThat(corpo.get("mensagens").get(0).get("tipo").asString()).isEqualTo("PRE_CADASTRO_QR");
        assertThat(corpo.get("mensagens").get(0).get("dados").get("codigoCurto").asString()).isEqualTo("100001");
        String cursor = corpo.get("cursor").asString();
        assertThat(Long.parseLong(cursor)).isGreaterThan(0);

        // a partir do cursor entregue, nada mais volta
        JsonNode vazio = entrada(cursor, null, null);
        assertThat(vazio.get("mensagens").size()).isZero();
        assertThat(vazio.get("cursor").asString()).isEqualTo(cursor);
    }

    @Test
    void semAckAsMensagensVoltamNaProximaLeitura() throws Exception {
        inserirPreCadastro("onl_pre_zzvq03", "100003");
        assertThat(entrada(null, null, null).get("mensagens").size()).isEqualTo(1);
        // sem ack, o cursor não avança no Online: reenvia a partir do início
        assertThat(entrada(null, null, null).get("mensagens").size()).isEqualTo(1);
    }

    @Test
    void ackConfirmaCursorEPurgaDadosPessoais() throws Exception {
        inserirPreCadastro("onl_pre_zzvq04", "100004");
        String cursor = entrada(null, null, null).get("cursor").asString();

        mvc.perform(post("/api/conector/v1/entrada/ack").headers(auth())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"cursor\":\"" + cursor + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ok"));

        assertPurgaCompleta("onl_pre_zzvq04", "ENTREGUE");
        assertThat(jdbc.queryForObject("SELECT ultimo_cursor FROM instalacao WHERE id = ?", Long.class,
                INSTALACAO)).isEqualTo(Long.parseLong(cursor));
    }

    @Test
    void longPollEsperaQuandoNaoHaMensagens() throws Exception {
        long t0 = System.nanoTime();
        mvc.perform(get("/api/conector/v1/entrada").param("espera", "1").headers(auth()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.mensagens").isEmpty());
        long ms = (System.nanoTime() - t0) / 1_000_000;
        assertThat(ms).isGreaterThanOrEqualTo(900);
    }

    @Test
    void esperaZeroVoltaImediatoQuandoVazio() throws Exception {
        mvc.perform(get("/api/conector/v1/entrada").param("espera", "0").headers(auth()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.mensagens").isEmpty())
                .andExpect(jsonPath("$.cursor").value("0"));
    }

    @Test
    void limiteAcimaDoMaximoDevolve400() throws Exception {
        mvc.perform(get("/api/conector/v1/entrada").param("limite", "101").headers(auth()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("MENSAGEM_INVALIDA"));
    }

    // ------------------------------------------------------------------ POST /saida (CONFIG_SITE)

    @Test
    void configSiteAplicaIdempotenteEVersaoObsoletaDevolve409() throws Exception {
        UUID id = UUID.randomUUID();
        ObjectNode m = configSite(id, 1, dadosConfig(true));
        enviarSaida(m).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT versao FROM config_site WHERE site_id = ?", Long.class, SITE))
                .isEqualTo(1L);
        assertThat(jdbc.queryForObject(
                "SELECT termo ->> 'termoId' FROM config_site WHERE site_id = ?", String.class, SITE))
                .isEqualTo(TERMO_ID);

        // idempotente: mesmo id não reaplica nem duplica a mensagem
        enviarSaida(m).andExpect(status().isOk());
        assertThat(contar("mensagem_saida")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT versao FROM config_site WHERE site_id = ?", Long.class, SITE))
                .isEqualTo(1L);

        // versão menor/igual (id novo) → 409
        enviarSaida(configSite(UUID.randomUUID(), 1, dadosConfig(true)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("VERSAO_OBSOLETA"));

        // versão maior → aplica
        enviarSaida(configSite(UUID.randomUUID(), 2, dadosConfig(true))).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT versao FROM config_site WHERE site_id = ?", Long.class, SITE))
                .isEqualTo(2L);
    }

    @Test
    void configSiteSemTermoGuardaTermoNulo() throws Exception {
        enviarSaida(configSite(UUID.randomUUID(), 1, dadosConfig(false))).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT termo IS NULL FROM config_site WHERE site_id = ?", Boolean.class,
                SITE)).isTrue();
    }

    @Test
    void configSiteInvalidoDevolve400MensagemInvalida() throws Exception {
        Map<String, Consumer<ObjectNode>> casos = new LinkedHashMap<>();
        casos.put("preCadastroExpiraHoras=0", d -> d.put("preCadastroExpiraHoras", 0));
        casos.put("preCadastroExpiraHoras=25", d -> d.put("preCadastroExpiraHoras", 25));
        casos.put("codigoCurtoDigitos=5", d -> d.put("codigoCurtoDigitos", 5));
        casos.put("limite porIpHora=0", d -> ((ObjectNode) d.get("limites")).put("porIpHora", 0));
        casos.put("termo sha256 divergente", d -> ((ObjectNode) d.get("termo")).put("sha256Texto", "0".repeat(64)));
        casos.put("termo sem texto", d -> ((ObjectNode) d.get("termo")).remove("texto"));
        casos.put("sem identificadorQr", d -> d.remove("identificadorQr"));

        for (Map.Entry<String, Consumer<ObjectNode>> caso : casos.entrySet()) {
            ObjectNode d = dadosConfig(true);
            caso.getValue().accept(d);
            enviarSaida(configSite(UUID.randomUUID(), 1, d))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("MENSAGEM_INVALIDA"));
        }
        assertThat(contar("config_site")).isZero();
        assertThat(contar("mensagem_saida")).isZero();
    }

    @Test
    void tenantErradoDevolve400ESiteForaDoEscopoDevolve403() throws Exception {
        ObjectNode tenantErrado = configSite(UUID.randomUUID(), 1, dadosConfig(true));
        tenantErrado.put("tenant", "2");
        enviarSaida(tenantErrado).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("MENSAGEM_INVALIDA"));

        ObjectNode siteFora = configSite(UUID.randomUUID(), 1, dadosConfig(true));
        siteFora.put("site", String.valueOf(OUTRO_SITE));
        enviarSaida(siteFora).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SITE_FORA_DO_ESCOPO"));
    }

    @Test
    void versaoContratoNaoSuportadaDevolve422() throws Exception {
        ObjectNode m = configSite(UUID.randomUUID(), 1, dadosConfig(true));
        m.put("versaoContrato", 2);
        enviarSaida(m).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("VERSAO_CONTRATO_NAO_SUPORTADA"));
    }

    @Test
    void loteAcimaDe100Devolve413() throws Exception {
        ArrayNode arr = om.createArrayNode();
        for (int i = 0; i < 101; i++) {
            arr.add(configSite(UUID.randomUUID(), 1, dadosConfig(true)));
        }
        ObjectNode corpo = om.createObjectNode();
        corpo.set("mensagens", arr);
        mvc.perform(post("/api/conector/v1/saida").headers(auth())
                        .contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(corpo)))
                .andExpect(status().isPayloadTooLarge()).andExpect(jsonPath("$.codigo").value("LOTE_GRANDE_DEMAIS"));
    }

    @Test
    void excecaoDepoisDaAutenticacaoNaoEhEngolidaComo401() {
        // O endpoint de teste lança RuntimeException DEPOIS do filtro de JWT. O filtro só protege a
        // verificação do token; a exceção tem de propagar (vira 5xx no container), nunca virar 401.
        assertThatThrownBy(() -> mvc.perform(get("/api/conector/v1/teste/excecao").headers(auth())))
                .rootCause().isInstanceOf(RuntimeException.class);
    }

    // ------------------------------------------------------------------ helpers

    private ResultActions heartbeat(String token) throws Exception {
        return mvc.perform(post("/api/conector/v1/heartbeat").header("Authorization", "Bearer " + token));
    }

    private JsonNode entrada(String cursor, String limite, String espera) throws Exception {
        var b = get("/api/conector/v1/entrada").headers(auth());
        if (cursor != null) {
            b.param("cursor", cursor);
        }
        if (limite != null) {
            b.param("limite", limite);
        }
        if (espera != null) {
            b.param("espera", espera);
        }
        MvcResult r = mvc.perform(b).andExpect(status().isOk()).andReturn();
        return om.readTree(r.getResponse().getContentAsString());
    }

    private ResultActions enviarSaida(ObjectNode... mensagens) throws Exception {
        ArrayNode arr = om.createArrayNode();
        for (ObjectNode m : mensagens) {
            arr.add(m);
        }
        ObjectNode corpo = om.createObjectNode();
        corpo.set("mensagens", arr);
        return mvc.perform(post("/api/conector/v1/saida").headers(auth())
                .contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(corpo)));
    }

    private ObjectNode configSite(UUID id, long versao, ObjectNode dados) {
        ObjectNode m = om.createObjectNode();
        m.put("id", id.toString());
        m.put("tipo", "CONFIG_SITE");
        m.put("versaoContrato", 1);
        m.put("tenant", String.valueOf(TENANT));
        m.put("site", String.valueOf(SITE));
        m.put("criadoEm", OffsetDateTime.now().toString());
        m.put("chave", "site:" + SITE);
        m.put("versao", versao);
        m.set("dados", dados);
        return m;
    }

    private ObjectNode dadosConfig(boolean comTermo) {
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
}
