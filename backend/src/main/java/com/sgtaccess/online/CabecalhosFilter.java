package com.sgtaccess.online;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Cabeçalhos de segurança das respostas da API (§2/§11): impede sniff de conteúdo, proíbe
 * requisições de terceiros revelarem a URL e proíbe embutir a API em frame. Vale para as rotas
 * pública e do conector; o actuator fica de fora (só health, usado pelo healthcheck do compose).
 *
 * <p>A CSP e o {@code frame-ancestors} da PÁGINA ficam para quando o frontend passar a ser servido
 * pelo build de produção em servidor estático (hoje é {@code ng serve}). Ver
 * {@code claude/proposta-headers-pagina.md}.
 */
public class CabecalhosFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        res.setHeader("X-Content-Type-Options", "nosniff");
        res.setHeader("X-Frame-Options", "DENY");
        res.setHeader("Referrer-Policy", "no-referrer");
        if (req.getRequestURI().startsWith("/api/")) {
            // resposta com termo/dado pessoal nunca vai para cache intermediário
            res.setHeader("Cache-Control", "no-store");
        }
        chain.doFilter(req, res);
    }
}