package com.sgtaccess.online.conector;

import com.sgtaccess.online.ConectorProperties;
import com.sgtaccess.online.JwtVerificador;
import com.sgtaccess.online.OnlineRepository;
import com.sgtaccess.online.OnlineRepository.Instalacao;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Autenticação das rotas do conector (contrato §2): JWT ES256 da instalação. Localiza a instalação pelo
 * {@code iss}, verifica a assinatura com a chave pública registrada, confere {@code aud}/{@code exp} e
 * o {@code jti} (anti-replay). Sem Spring Security: filtro registrado só em {@code /api/conector/v1/*}.
 */
public class ConectorJwtFilter extends OncePerRequestFilter {

    public static final String ATTR_INSTALACAO = "online.instalacao";

    private final OnlineRepository repo;
    private final ConectorProperties props;

    public ConectorJwtFilter(OnlineRepository repo, ConectorProperties props) {
        this.repo = repo;
        this.props = props;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String auth = req.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            erro(res, HttpServletResponse.SC_UNAUTHORIZED, "TOKEN_INVALIDO");
            return;
        }
        String token = auth.substring(7).trim();
        Instalacao inst;
        try {
            String iss = JwtVerificador.iss(token);
            inst = repo.instalacaoAtiva(iss)
                    .orElseThrow(() -> new Exception("instalação desconhecida ou inativa"));
            JwtVerificador.Reivindicacoes r = JwtVerificador.verificar(token, inst.chavePublica(), props.baseUrl());
            repo.limparJtiExpirados();
            if (!repo.consumirJti(r.jti(), Instant.ofEpochSecond(r.exp()))) {
                erro(res, HttpServletResponse.SC_UNAUTHORIZED, "TOKEN_INVALIDO");
                return;
            }
        } catch (Exception e) {
            // nunca logar o token nem o motivo detalhado; só o código do contrato
            erro(res, HttpServletResponse.SC_UNAUTHORIZED, "TOKEN_INVALIDO");
            return;
        }
        req.setAttribute(ATTR_INSTALACAO, inst);
        chain.doFilter(req, res);
    }

    private static void erro(HttpServletResponse res, int status, String codigo) throws IOException {
        res.setStatus(status);
        res.setContentType("application/json");
        res.setCharacterEncoding(StandardCharsets.UTF_8.name());
        res.getWriter().write("{\"codigo\":\"" + codigo + "\",\"mensagem\":\"autenticação recusada\"}");
    }
}
