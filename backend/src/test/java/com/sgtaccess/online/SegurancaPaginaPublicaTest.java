package com.sgtaccess.online;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/** Cabeçalhos de segurança das respostas da API (§2/§11). */
class SegurancaPaginaPublicaTest extends OnlineTesteBase {

    @Test
    void respostaDaApiPublicaTemCabecalhosDeSeguranca() throws Exception {
        MvcResult r = mvc.perform(get("/api/publico/config-qr/{id}", "nao-existe")).andReturn();

        assertThat(r.getResponse().getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(r.getResponse().getHeader("X-Frame-Options")).isEqualTo("DENY");
        assertThat(r.getResponse().getHeader("Referrer-Policy")).isEqualTo("no-referrer");
        assertThat(r.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");
    }
}