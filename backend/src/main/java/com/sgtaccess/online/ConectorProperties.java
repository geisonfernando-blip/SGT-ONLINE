package com.sgtaccess.online;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuração do Online (variáveis de ambiente no compose).
 *
 * @param baseUrl                  base EXTERNA do Online usada na validação do {@code aud} do JWT do
 *                                 conector. Tem de ser IGUAL a {@code SGT_ONLINE_URL} do conector (a
 *                                 URL que ele de fato acessa).
 * @param tempoMinimoPreenchimento tempo mínimo entre carregar o formulário e enviar (anti-bot).
 * @param corsOrigens              origens liberadas para a página pública, sem curinga. Lista vazia
 *                                 só é aceita no perfil {@code dev}; fora dele o boot falha (ver
 *                                 {@link CorsConfig}).
 */
@ConfigurationProperties(prefix = "online")
public record ConectorProperties(String baseUrl, Duration tempoMinimoPreenchimento, List<String> corsOrigens) {

    public Duration tempoMinimo() {
        return tempoMinimoPreenchimento == null ? Duration.ofSeconds(3) : tempoMinimoPreenchimento;
    }

    /** Origens liberadas. Lista vazia é configuração incompleta: só o perfil `dev` a tolera. */
    public List<String> corsOrigens() {
        return corsOrigens == null ? List.of() : corsOrigens;
    }
}
