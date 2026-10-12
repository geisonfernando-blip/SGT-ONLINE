package com.sgtaccess.online;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * A lista de origens vem da variável de ambiente {@code ONLINE_CORS_ORIGENS} por PLACEHOLDER no
 * {@code application.yml}. Isso não é detalhe: o bind por nome de propriedade
 * ({@code online.cors-origens} ← {@code ONLINE_CORS_ORIGENS}) não funciona, porque a propriedade tem
 * hífen e a variável não — o backend subia com a lista vazia e caía na regra de boot. Já aconteceu uma
 * vez; este teste existe para o bind por variável de ambiente não voltar a quebrar calado.
 */
@SpringBootTest(properties = "ONLINE_CORS_ORIGENS=http://localhost:4300,http://192.168.0.9:4300")
class CorsOrigensDoAmbienteTest {

    @Autowired ConectorProperties props;

    @Test
    void listaDeOrigensVemDaVariavelDeAmbiente() {
        assertThat(props.corsOrigens())
                .containsExactly("http://localhost:4300", "http://192.168.0.9:4300");
    }
}