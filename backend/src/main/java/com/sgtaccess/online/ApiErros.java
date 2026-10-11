package com.sgtaccess.online;

import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Transforma o {@link ErroApi} no corpo padrão de erro do contrato (§8) e qualquer exceção não
 * tratada em {@code 500 ERRO_INTERNO}, sem path, timestamp, stack, SQL ou dado pessoal.
 *
 * <p>Log mínimo estruturado (CLAUDE.md: sem dado pessoal e sem segredo): só o código do contrato, o
 * método e o caminho da rota. NUNCA IP, token, {@code jti}, corpo da requisição, nome, CPF ou aceite.
 *
 * <p>PEND: retenção de IP para fins de segurança é decisão LGPD em aberto; o MVP não guarda nem
 * registra IP em log.
 */
@RestControllerAdvice
public class ApiErros {

    private static final Logger LOG = LoggerFactory.getLogger(ApiErros.class);

    @ExceptionHandler(ErroApi.class)
    public ResponseEntity<Map<String, Object>> erro(ErroApi e, HttpServletRequest req) {
        int status = e.status().value();
        if (status >= 400) {
            LOG.warn("evento=erro_contrato codigo={} status={} metodo={} rota={}",
                    e.codigo(), status, req.getMethod(), req.getRequestURI());
        }
        return ResponseEntity.status(e.status()).body(corpo(e.codigo(), e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> inesperado(Exception e, HttpServletRequest req) {
        // a exceção NÃO entra no log: pode carregar SQL, coluna e dado pessoal na mensagem
        LOG.error("evento=erro_interno metodo={} rota={}", req.getMethod(), req.getRequestURI());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(corpo("ERRO_INTERNO", "erro interno"));
    }

    private static Map<String, Object> corpo(String codigo, String mensagem) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("codigo", codigo);
        m.put("mensagem", mensagem);
        m.put("correlacao", null);
        return m;
    }
}