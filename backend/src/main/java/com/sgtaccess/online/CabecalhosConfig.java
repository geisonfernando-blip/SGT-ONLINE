package com.sgtaccess.online;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registra o {@link CabecalhosFilter} nas rotas da API (pública e do conector). */
@Configuration
public class CabecalhosConfig {

    @Bean
    FilterRegistrationBean<CabecalhosFilter> cabecalhos() {
        FilterRegistrationBean<CabecalhosFilter> reg = new FilterRegistrationBean<>(new CabecalhosFilter());
        reg.addUrlPatterns("/api/*");
        reg.setOrder(0);
        return reg;
    }
}