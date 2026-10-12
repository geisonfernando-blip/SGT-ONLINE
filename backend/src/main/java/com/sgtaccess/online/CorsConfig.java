package com.sgtaccess.online;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS só na página pública (o front Angular em outra porta/origem chama o backend). As rotas do
 * conector NÃO têm CORS: são chamadas servidor-a-servidor pelo conector, não pelo navegador.
 *
 * <p>Origem explícita por {@code ONLINE_CORS_ORIGENS} — sem curinga. O cabeçalho liberado é só o
 * {@code Content-Type} (JSON) e {@code X-Aparelho} (identificador anônimo do limite por aparelho);
 * não há credenciais, então nenhum cookie/sessão é aceito do navegador.
 *
 * <p>Lista vazia significa configuração incompleta: fora do perfil {@code dev} isso derruba o boot, em
 * vez de silenciosamente deixar a página pública chamar a API de outra origem e falhar no navegador.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private static final Logger LOG = LoggerFactory.getLogger(CorsConfig.class);

    private final ConectorProperties props;
    private final boolean dev;

    public CorsConfig(ConectorProperties props, Environment env) {
        this.props = props;
        this.dev = env.acceptsProfiles(Profiles.of("dev"));
        List<String> origens = props.corsOrigens();
        // só a QUANTIDADE: o valor é uma origem da rede local e não entra em log
        LOG.info("evento=cors_origens total={} perfil_dev={}", origens.size(), this.dev);
        if (origens.isEmpty() && !this.dev) {
            throw new IllegalStateException(
                    "ONLINE_CORS_ORIGENS vazio: informe as origens da página pública, ex.: "
                            + "http://192.168.0.10:4300 (no perfil dev o padrão é http://localhost:4300)");
        }
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