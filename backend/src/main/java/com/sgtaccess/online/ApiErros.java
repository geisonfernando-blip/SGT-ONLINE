package com.sgtaccess.online;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Transforma o {@link ErroApi} no corpo padrão de erro do contrato (§8), sem dado pessoal. */
@RestControllerAdvice
public class ApiErros {

    @ExceptionHandler(ErroApi.class)
    public ResponseEntity<Map<String, Object>> erro(ErroApi e) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("codigo", e.codigo());
        corpo.put("mensagem", e.getMessage());
        corpo.put("correlacao", null);
        return ResponseEntity.status(e.status()).body(corpo);
    }
}
