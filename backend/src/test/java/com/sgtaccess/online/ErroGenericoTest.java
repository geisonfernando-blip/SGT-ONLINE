package com.sgtaccess.online;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Exceção não tratada vira {@code 500} com o corpo do contrato (§8) e só o código do contrato: sem
 * path, timestamp, stack trace, SQL ou dado pessoal.
 */
class ErroGenericoTest extends OnlineTesteBase {

    @Test
    void excecaoNaoTratadaVira500ComCorpoDoContrato() throws Exception {
        mvc.perform(get("/api/publico/teste/excecao"))
                .andExpect(status().is5xxServerError())
                .andExpect(jsonPath("$.codigo").value("ERRO_INTERNO"))
                .andExpect(jsonPath("$.mensagem").value("erro interno"))
                .andExpect(jsonPath("$.correlacao").doesNotExist());

        MvcResult r = mvc.perform(get("/api/publico/teste/excecao")).andReturn();
        String corpo = r.getResponse().getContentAsString();
        // sem stack, sem caminho/rota interna, sem SQL e sem dado pessoal
        assertThat(corpo).doesNotContain("at com.sgtaccess", "\tat ", "exceção deliberada");
        assertThat(corpo).doesNotContain("timestamp", "path", "error", "SELECT", "pre_cadastro");
        assertThat(corpo).doesNotContain(TERMO_ID, TEXTO_TERMO, CPF_VALIDO);
    }
}