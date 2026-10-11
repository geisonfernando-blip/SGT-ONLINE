package com.sgtaccess.online;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * CORS da página pública: só as origens de {@code ONLINE_CORS_ORIGENS} passam, sem curinga. O padrão
 * do teste ({@code backend/src/test/resources/application.yml}) é {@code http://localhost:4300}.
 */
@AutoConfigureMockMvc
class CorsConfigTest extends OnlineTesteBase {

    @Autowired MockMvc mvc;

    @Test
    void origemConfiguradaTemPreflightAceito() throws Exception {
        MvcResult r = mvc.perform(options("/api/publico/config-qr/zzvqQR00001")
                        .header("Origin", "http://localhost:4300")
                        .header("Access-Control-Request-Method", "POST"))
                .andReturn();

        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        assertThat(r.getResponse().getHeader("Access-Control-Allow-Origin")).isEqualTo("http://localhost:4300");
    }

    @Test
    void origemNaoConfiguradaNaoTemPreflight() throws Exception {
        MvcResult r = mvc.perform(options("/api/publico/config-qr/zzvqQR00001")
                        .header("Origin", "https://ataque.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andReturn();

        assertThat(r.getResponse().getHeader("Access-Control-Allow-Origin")).isNull();
    }

    @Test
    void nenhumaRespostaDevolveCuringaDeOrigem() throws Exception {
        MvcResult r = mvc.perform(get("/api/publico/config-qr/zzvqQR00001")
                        .header("Origin", "https://ataque.example"))
                .andReturn();

        assertThat(r.getResponse().getHeader("Access-Control-Allow-Origin")).isNull();
        assertThat(r.getResponse().getHeader("Access-Control-Allow-Credentials")).isNull();
    }
}