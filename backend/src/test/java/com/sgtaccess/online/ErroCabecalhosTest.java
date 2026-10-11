package com.sgtaccess.online;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.node.ObjectNode;

/**
 * Toda resposta de erro da API sai com os quatro cabeçalhos de segurança e no corpo do contrato §8
 * ({@code codigo}, {@code mensagem}, {@code correlacao}), sem caminho, timestamp, stack, SQL ou dado
 * pessoal — inclusive nos erros gerados pelo próprio Spring (404 de rota, 405 de método, 415 de tipo,
 * JSON malformado).
 */
class ErroCabecalhosTest extends OnlineTesteBase {

    @Test
    void erro401DoFiltroJwtTemCabecalhosECorpoDoContrato() throws Exception {
        conferir(mvc.perform(post("/api/conector/v1/heartbeat")).andReturn(), 401, "TOKEN_INVALIDO");
    }

    @Test
    void erro403DeSiteForaDoEscopoTemCabecalhosECorpoDoContrato() throws Exception {
        ObjectNode m = configSite(java.util.UUID.randomUUID(), 1, dadosConfig(true));
        m.put("site", String.valueOf(OUTRO_SITE));
        m.put("chave", "site:" + OUTRO_SITE);

        conferir(enviarSaida(m).andReturn(), 403, "SITE_FORA_DO_ESCOPO");
    }

    @Test
    void erro404DeQrDesconhecidoTemCabecalhosECorpoDoContrato() throws Exception {
        conferir(mvc.perform(get("/api/publico/config-qr/{id}", "nao-existe")).andReturn(), 404, "QR_INVALIDO");
    }

    @Test
    void erro404DeRotaInexistenteTemCabecalhosECorpoDoContrato() throws Exception {
        conferir(mvc.perform(get("/api/publico/rota-que-nao-existe")).andReturn(), 404, "MENSAGEM_INVALIDA");
    }

    @Test
    void erro405DeMetodoNaoPermitidoTemCabecalhosECorpoDoContrato() throws Exception {
        conferir(mvc.perform(get("/api/publico/pre-cadastro")).andReturn(), 405, "MENSAGEM_INVALIDA");
    }

    @Test
    void erro415DeTipoNaoSuportadoTemCabecalhosECorpoDoContrato() throws Exception {
        conferir(mvc.perform(post("/api/publico/pre-cadastro").contentType(MediaType.TEXT_PLAIN)
                .content("nao sou json")).andReturn(), 415, "MENSAGEM_INVALIDA");
    }

    @Test
    void erro400DeJsonMalformadoTemCabecalhosECorpoDoContrato() throws Exception {
        // corpo malformado com um trecho que o Jackson costuma ecoar na mensagem de erro
        String corpoRuim = "{\"identificadorQr\": \"ensaioQR0001\", \"cpf\":\"52998224725\"";
        conferir(mvc.perform(post("/api/publico/pre-cadastro").contentType(MediaType.APPLICATION_JSON)
                .content(corpoRuim)).andReturn(), 400, "MENSAGEM_INVALIDA");
    }

    /** A mensagem do Jackson traz trecho do corpo recebido; ela não pode ecoar na resposta. */
    @Test
    void jsonMalformadoNaoEcoaTrechoDoCorpoNemDetalheInterno() throws Exception {
        String corpoRuim = "{\"identificadorQr\": \"ensaioQR0001\", \"cpf\":\"52998224725\"";
        MvcResult r = mvc.perform(post("/api/publico/pre-cadastro").contentType(MediaType.APPLICATION_JSON)
                .content(corpoRuim)).andReturn();

        String resposta = r.getResponse().getContentAsString();
        assertThat(resposta)
                .doesNotContain("52998224725", "ensaioQR0001", "identificadorQr", "cpf", "Unexpected",
                        "Unexpected end", "JsonEOFException", "readToken", "token");
        // corpo exatamente no formato do contrato, com mensagem genérica
        Map<String, Object> corpo = om.readValue(resposta, Map.class);
        assertThat(corpo).containsOnlyKeys("codigo", "mensagem", "correlacao");
        assertThat(corpo.get("codigo")).isEqualTo("MENSAGEM_INVALIDA");
        assertThat(corpo.get("mensagem")).isEqualTo("requisição inválida");
        assertThat(corpo.get("correlacao")).isNull();
    }

    private void conferir(MvcResult r, int statusEsperado, String codigoEsperado)
            throws java.io.UnsupportedEncodingException {
        assertThat(r.getResponse().getStatus()).isEqualTo(statusEsperado);
        assertThat(r.getResponse().getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(r.getResponse().getHeader("X-Frame-Options")).isEqualTo("DENY");
        assertThat(r.getResponse().getHeader("Referrer-Policy")).isEqualTo("no-referrer");
        assertThat(r.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");

        Map<String, Object> corpo = om.readValue(r.getResponse().getContentAsString(), Map.class);
        assertThat(corpo).containsOnlyKeys("codigo", "mensagem", "correlacao");
        assertThat(corpo.get("codigo")).isEqualTo(codigoEsperado);
        assertThat((String) corpo.get("mensagem")).isNotBlank();

        String bruto = r.getResponse().getContentAsString();
        assertThat(bruto).doesNotContain("timestamp", "\"path\"", "\tat ", "org.springframework", "SELECT");
        assertThat(bruto).doesNotContain(TERMO_ID, TEXTO_TERMO, CPF_VALIDO, INSTALACAO);
    }
}