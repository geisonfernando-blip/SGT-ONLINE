package com.sgtaccess.online;

import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
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

    /**
     * Corpo JSON malformado ou ilegível: {@link HttpMessageNotReadableException} não é
     * {@link ErrorResponse} nesta versão do Spring, então o status 400 é definido aqui.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> jsonIlegivel(HttpMessageNotReadableException e,
            HttpServletRequest req) {
        LOG.warn("evento=erro_requisicao status=400 metodo={} rota={} excecao={}", req.getMethod(),
                req.getRequestURI(), e.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(corpo("MENSAGEM_INVALIDA", "requisição inválida"));
    }

    /**
     * Qualquer outra exceção. Um erro do próprio Spring (404 de rota inexistente, 405 de método, 415 de
     * tipo) é um {@link ErrorResponse} e mantém o status dele; o resto é 500. O corpo nunca carrega a
     * mensagem da exceção — ela pode conter SQL, coluna e dado pessoal.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> inesperado(Exception e, HttpServletRequest req) {
        int status = e instanceof ErrorResponse er ? er.getStatusCode().value()
                : HttpStatus.INTERNAL_SERVER_ERROR.value();
        boolean interno = status >= 500;
        // a exceção NÃO entra no log (a mensagem pode trazer SQL e dado pessoal); só a classe
        LOG.warn("evento={} status={} metodo={} rota={} excecao={}", interno ? "erro_interno" : "erro_requisicao",
                status, req.getMethod(), req.getRequestURI(), e.getClass().getSimpleName());
        return ResponseEntity.status(status)
                .body(corpo(interno ? "ERRO_INTERNO" : "MENSAGEM_INVALIDA", interno ? "erro interno" : "requisição inválida"));
    }

    private static Map<String, Object> corpo(String codigo, String mensagem) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("codigo", codigo);
        m.put("mensagem", mensagem);
        m.put("correlacao", null);
        return m;
    }
}