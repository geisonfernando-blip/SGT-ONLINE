package com.sgtaccess.online;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/**
 * Lista de origens do CORS: vazia é configuração incompleta e só passa no perfil {@code dev}. Fora
 * dele o backend não sobe, em vez de a página pública falhar silenciosamente no navegador.
 */
class CorsConfigBootTest {

    private static ConectorProperties props(List<String> origens) {
        return new ConectorProperties("http://localhost:8090", Duration.ofSeconds(3), origens);
    }

    @Test
    void perfilDevAceitaListaVazia() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev");
        assertThatCode(() -> new CorsConfig(props(List.of()), env)).doesNotThrowAnyException();
    }

    @Test
    void perfilDevAceitaListaNula() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev");
        assertThatCode(() -> new CorsConfig(props(null), env)).doesNotThrowAnyException();
    }

    @Test
    void foraDoDevListaVaziaDerrubaOBoot() {
        assertThatThrownBy(() -> new CorsConfig(props(List.of()), new MockEnvironment()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ONLINE_CORS_ORIGENS");
    }

    @Test
    void foraDoDevListaNulaDerrubaOBoot() {
        assertThatThrownBy(() -> new CorsConfig(props(null), new MockEnvironment()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void comOrigemConfiguradaNaoDerrubaOBoot() {
        assertThatCode(() -> new CorsConfig(props(List.of("http://192.168.0.10:4300")), new MockEnvironment()))
                .doesNotThrowAnyException();
        assertThat(props(List.of("http://192.168.0.10:4300")).corsOrigens()).containsExactly("http://192.168.0.10:4300");
    }
}