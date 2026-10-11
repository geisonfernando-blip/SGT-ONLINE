package com.sgtaccess.online;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Página pública do QR: {@code GET /config-qr} (termo vindo da CONFIG_SITE) e {@code POST /pre-cadastro}
 * (código de 6 dígitos, validação de CPF/documento, honeypot, limites, termo null → indisponível).
 * O tempo mínimo de preenchimento é coberto no {@link FormTokenStoreTest}.
 */
class PreCadastroIntegrationTest extends OnlineTesteBase {

    // ------------------------------------------------------------------ GET /config-qr

    @Test
    void configQrDesconhecidoDevolve404() throws Exception {
        mvc.perform(get("/api/publico/config-qr/nao-existe")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("QR_INVALIDO"));
    }

    @Test
    void configQrAtivoComTermoDevolveTermoEToken() throws Exception {
        configurarSite(SITE, IDENTIFICADOR_QR, true, true, null);
        mvc.perform(get("/api/publico/config-qr/{id}", IDENTIFICADOR_QR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(true))
                .andExpect(jsonPath("$.termo.termoId").value(TERMO_ID))
                .andExpect(jsonPath("$.termo.texto").value(TEXTO_TERMO))
                .andExpect(jsonPath("$.formToken").isString());
    }

    @Test
    void configQrSemTermoDevolveTermoNulo() throws Exception {
        configurarSite(SITE, IDENTIFICADOR_QR, true, false, null);
        mvc.perform(get("/api/publico/config-qr/{id}", IDENTIFICADOR_QR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(true))
                .andExpect(jsonPath("$.termo").value(nullValue()));
    }

    // ------------------------------------------------------------------ POST /pre-cadastro (validação)

    @Test
    void preCadastroValidoGeraCodigoDe6DigitosEEntraNaFila() throws Exception {
        configurarSite(SITE, IDENTIFICADOR_QR, true, true, null);
        MvcResult r = preCadastrar(IDENTIFICADOR_QR, null, null, null);
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        JsonNode corpo = om.readTree(r.getResponse().getContentAsString());
        assertThat(corpo.get("codigoCurto").asString()).matches("\\d{6}");
        assertThat(contar("pre_cadastro_qr WHERE site_id = ?", SITE)).isEqualTo(1);
        // o pré-cadastro guarda o aceite do termo vigente vindo da CONFIG_SITE (nunca hardcoded)
        assertThat(jdbc.queryForObject("SELECT termo_id FROM pre_cadastro_qr WHERE site_id = ?", String.class,
                SITE)).isEqualTo(TERMO_ID);
    }

    @Test
    void cpfComDigitoVerificadorInvalidoDevolve400() throws Exception {
        configurarSite(SITE, IDENTIFICADOR_QR, true, true, null);
        MvcResult r = preCadastrar(IDENTIFICADOR_QR, c -> ((ObjectNode) c.get("pessoa")).put("cpf",
                "529.982.247-26"), null, null);
        assertThat(r.getResponse().getStatus()).isEqualTo(400);
        assertThat(om.readTree(r.getResponse().getContentAsString()).get("mensagem").asString())
                .isEqualTo("cpf inválido");
    }

    @Test
    void semNomeDevolve400() throws Exception {
        configurarSite(SITE, IDENTIFICADOR_QR, true, true, null);
        MvcResult r = preCadastrar(IDENTIFICADOR_QR, c -> ((ObjectNode) c.get("pessoa")).remove("nome"), null, null);
        assertThat(r.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void semCpfNemDocumentoDevolve400() throws Exception {
        configurarSite(SITE, IDENTIFICADOR_QR, true, true, null);
        MvcResult r = preCadastrar(IDENTIFICADOR_QR, c -> {
            ((ObjectNode) c.get("pessoa")).remove("cpf");
            ((ObjectNode) c.get("pessoa")).putNull("documento");
        }, null, null);
        assertThat(r.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void honeypotPreenchidoDevolve400() throws Exception {
        configurarSite(SITE, IDENTIFICADOR_QR, true, true, null);
        MvcResult r = preCadastrar(IDENTIFICADOR_QR, c -> c.put("site_web", "bot"), null, null);
        assertThat(r.getResponse().getStatus()).isEqualTo(400);
        assertThat(contar("pre_cadastro_qr")).isZero();
    }

    @Test
    void formTokenAusenteOuInvalidoDevolve400() throws Exception {
        configurarSite(SITE, IDENTIFICADOR_QR, true, true, null);
        ObjectNode corpo = corpoPadrao(IDENTIFICADOR_QR, "token-nao-emitido");
        mvc.perform(post("/api/publico/pre-cadastro").contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(corpo)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("MENSAGEM_INVALIDA"));
    }

    @Test
    void aceiteNaoMarcadoDevolve400() throws Exception {
        configurarSite(SITE, IDENTIFICADOR_QR, true, true, null);
        MvcResult r = preCadastrar(IDENTIFICADOR_QR, c -> c.put("aceite", false), null, null);
        assertThat(r.getResponse().getStatus()).isEqualTo(400);
    }

    // ------------------------------------------------------------------ limites (PEND-096)

    @Test
    void limitePorIpHoraAplicadoQuandoConfigurado() throws Exception {
        configurarSite(SITE, IDENTIFICADOR_QR, true, true, 2);
        assertThat(preCadastrar(IDENTIFICADOR_QR, null, "1.2.3.4", null).getResponse().getStatus()).isEqualTo(200);
        assertThat(preCadastrar(IDENTIFICADOR_QR, null, "1.2.3.4", null).getResponse().getStatus()).isEqualTo(200);
        MvcResult terceiro = preCadastrar(IDENTIFICADOR_QR, null, "1.2.3.4", null);
        assertThat(terceiro.getResponse().getStatus()).isEqualTo(429);
        assertThat(om.readTree(terceiro.getResponse().getContentAsString()).get("codigo").asString())
                .isEqualTo("LIMITE_EXCEDIDO");
    }

    @Test
    void limiteNuloNaoBloqueia() throws Exception {
        configurarSite(SITE, IDENTIFICADOR_QR, true, true, null);
        for (int i = 0; i < 5; i++) {
            assertThat(preCadastrar(IDENTIFICADOR_QR, null, null, null).getResponse().getStatus()).isEqualTo(200);
        }
    }

    @Test
    void limitePorAparelhoHoraAplicadoQuandoConfigurado() throws Exception {
        configurarSite(SITE, IDENTIFICADOR_QR, true, true, null);
        limitePorAparelho(2);

        assertThat(preCadastrar(IDENTIFICADOR_QR, null, null, "navegador-abc").getResponse().getStatus())
                .isEqualTo(200);
        assertThat(preCadastrar(IDENTIFICADOR_QR, null, null, "navegador-abc").getResponse().getStatus())
                .isEqualTo(200);
        // mesmo IP, aparelho diferente: o limite por aparelho não estoura
        assertThat(preCadastrar(IDENTIFICADOR_QR, null, null, "navegador-xyz").getResponse().getStatus())
                .isEqualTo(200);
        assertThat(preCadastrar(IDENTIFICADOR_QR, null, null, "navegador-abc").getResponse().getStatus())
                .isEqualTo(429);
    }

    @Test
    void aparelhoInvalidoCaiParaOIpSemRecusarOEnvio() throws Exception {
        configurarSite(SITE, IDENTIFICADOR_QR, true, true, null);
        limitePorAparelho(2);

        // cabeçalho fora do formato aceito ([A-Za-z0-9_-]{1,64}): não recusa, cai para a chave do IP
        assertThat(preCadastrar(IDENTIFICADOR_QR, null, "1.2.3.4", "aparelho com espaço!")
                .getResponse().getStatus()).isEqualTo(200);
        assertThat(preCadastrar(IDENTIFICADOR_QR, null, "1.2.3.4", "x".repeat(65))
                .getResponse().getStatus()).isEqualTo(200);
        // ... e o limite passa a valer por IP, como para qualquer visitante sem cabeçalho
        assertThat(preCadastrar(IDENTIFICADOR_QR, null, "1.2.3.4", null).getResponse().getStatus()).isEqualTo(429);
    }

    // ------------------------------------------------------------------ indisponibilidade

    @Test
    void termoNuloTornaCadastroIndisponivel() throws Exception {
        configurarSite(SITE, IDENTIFICADOR_QR, true, false, null);
        MvcResult r = preCadastrar(IDENTIFICADOR_QR, null, null, null);
        assertThat(r.getResponse().getStatus()).isEqualTo(409);
        assertThat(om.readTree(r.getResponse().getContentAsString()).get("codigo").asString())
                .isEqualTo("SEM_TERMO");
        assertThat(contar("pre_cadastro_qr")).isZero();
    }

    @Test
    void siteInativoDevolve404() throws Exception {
        configurarSite(SITE, IDENTIFICADOR_QR, false, true, null);
        MvcResult r = preCadastrar(IDENTIFICADOR_QR, null, null, null);
        assertThat(r.getResponse().getStatus()).isEqualTo(404);
        assertThat(om.readTree(r.getResponse().getContentAsString()).get("codigo").asString())
                .isEqualTo("QR_INVALIDO");
    }

    // ------------------------------------------------------------------ expiração (LGPD)

    @Test
    void expiracaoApagaDadosPessoais() throws Exception {
        configurarSite(SITE, IDENTIFICADOR_QR, true, true, null);
        assertThat(preCadastrar(IDENTIFICADOR_QR, null, null, null).getResponse().getStatus()).isEqualTo(200);
        String idExterno = jdbc.queryForObject("SELECT id_externo FROM pre_cadastro_qr", String.class);

        jdbc.update("UPDATE pre_cadastro_qr SET expira_em = now() - interval '1 minute'");
        expiracao.expirar();

        assertPurgaCompleta(idExterno, "EXPIRADO");
    }

    @Test
    void checkRecusaDadoPessoalEmLinhaNaoPendente() {
        inserirPreCadastro("onl_pre_zzvq05", "100005");
        // entregar/expirar sem anular os dados pessoais viola o CHECK da V002
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE pre_cadastro_qr SET status = 'ENTREGUE' WHERE id_externo = 'onl_pre_zzvq05'"))
                .hasMessageContaining("pre_cadastro_qr_dados_chk");
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE pre_cadastro_qr SET status = 'EXPIRADO' WHERE id_externo = 'onl_pre_zzvq05'"))
                .hasMessageContaining("pre_cadastro_qr_dados_chk");
    }

    // ------------------------------------------------------------------ helpers

    /** Limite por aparelho/hora do site de ensaio (o helper de fixture só cobre o limite por IP). */
    private void limitePorAparelho(int limite) {
        jdbc.update("UPDATE config_site SET limite_por_aparelho_hora = ? WHERE site_id = ?", limite, SITE);
    }

    private String formToken(String identificadorQr) throws Exception {
        MvcResult r = mvc.perform(get("/api/publico/config-qr/{id}", identificadorQr))
                .andExpect(status().isOk()).andReturn();
        return om.readTree(r.getResponse().getContentAsString()).get("formToken").asString();
    }

    private ObjectNode corpoPadrao(String identificadorQr, String formToken) {
        ObjectNode c = om.createObjectNode();
        c.put("identificadorQr", identificadorQr);
        c.put("formToken", formToken);
        c.put("aceite", true);
        c.put("site_web", "");
        ObjectNode p = c.putObject("pessoa");
        p.put("nome", "zzvq Visitante");
        p.put("cpf", CPF_VALIDO);
        p.putNull("documento");
        p.put("empresa", "zzvq Transportes");
        p.put("telefone", "+55 43 99999-0000");
        p.put("email", "visitante@zzvq.test");
        return c;
    }

    private MvcResult preCadastrar(String identificadorQr, Consumer<ObjectNode> customiza, String ip,
            String aparelho) throws Exception {
        ObjectNode corpo = corpoPadrao(identificadorQr, formToken(identificadorQr));
        if (customiza != null) {
            customiza.accept(corpo);
        }
        var b = post("/api/publico/pre-cadastro").contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(corpo));
        if (ip != null) {
            // o IP vem do remoteAddr da conexão; X-Forwarded-For é ignorado pelo serviço
            b.with(r -> {
                r.setRemoteAddr(ip);
                return r;
            });
        }
        if (aparelho != null) {
            b.header("X-Aparelho", aparelho);
        }
        return mvc.perform(b).andReturn();
    }
}
