package com.sgtaccess.online.conector;

import com.sgtaccess.online.ConectorProperties;
import com.sgtaccess.online.OnlineRepository;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registra o filtro de JWT SÓ nas rotas do conector (contrato §3, base {@code /api/conector/v1}). */
@Configuration
public class ConectorConfig {

    @Bean
    FilterRegistrationBean<ConectorJwtFilter> conectorJwt(OnlineRepository repo, ConectorProperties props) {
        FilterRegistrationBean<ConectorJwtFilter> reg = new FilterRegistrationBean<>(
                new ConectorJwtFilter(repo, props));
        reg.addUrlPatterns("/api/conector/v1/*");
        reg.setOrder(1);
        return reg;
    }
}
