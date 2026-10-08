package com.sgtaccess.online;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuração do Online (variáveis de ambiente no compose).
 *
 * @param baseUrl                  base EXTERNA do Online usada na validação do {@code aud} do JWT do
 *                                 conector. Tem de ser IGUAL a {@code SGT_ONLINE_URL} do conector (a
 *                                 URL que ele de fato acessa).
 * @param tempoMinimoPreenchimento tempo mínimo entre carregar o formulário e enviar (anti-bot).
 */
@ConfigurationProperties(prefix = "online")
public record ConectorProperties(String baseUrl, Duration tempoMinimoPreenchimento) {

    public Duration tempoMinimo() {
        return tempoMinimoPreenchimento == null ? Duration.ofSeconds(3) : tempoMinimoPreenchimento;
    }
}
