package com.sgtaccess.online;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS só na página pública (o front Angular em outra porta/origem chama o backend). As rotas do
 * conector NÃO têm CORS: são chamadas servidor-a-servidor pelo conector, não pelo navegador.
 *
 * <p>Origem explícita por {@code ONLINE_CORS_ORIGENS} — sem curinga. O cabeçalho liberado é só o
 * {@code Content-Type} (JSON) e {@code X-Aparelho} (identificador anônimo do limite por aparelho);
 * não há credenciais, então nenhum cookie/sessão é aceito do navegador.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final ConectorProperties props;

    public CorsConfig(ConectorProperties props) {
        this.props = props;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/publico/**")
                .allowedOrigins(props.corsOrigens().toArray(String[]::new))
                .allowedMethods("GET", "POST")
                .allowedHeaders("Content-Type", "X-Aparelho")
                .maxAge(3600);
    }
}