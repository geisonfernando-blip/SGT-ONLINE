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
 * @param corsOrigens              origens liberadas para a página pública, sem curinga. Vazio = CORS
 *                                 desligado (o frontend passa a ser servido na mesma origem).
 */
@ConfigurationProperties(prefix = "online")
public record ConectorProperties(String baseUrl, Duration tempoMinimoPreenchimento, List<String> corsOrigens) {

    public Duration tempoMinimo() {
        return tempoMinimoPreenchimento == null ? Duration.ofSeconds(3) : tempoMinimoPreenchimento;
    }

    /** Origens liberadas; nunca devolve lista vazia, porque aí não haveria CORS nenhum. */
    public List<String> corsOrigens() {
        return corsOrigens == null || corsOrigens.isEmpty()
                ? List.of("http://localhost:4300")
                : corsOrigens;
    }
}
