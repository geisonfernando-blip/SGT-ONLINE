package com.sgtaccess.online;

import org.springframework.http.HttpStatus;

/** Erro de negócio devolvido no corpo do contrato §8: {@code {codigo, mensagem, correlacao}}. */
public class ErroApi extends RuntimeException {

    private final HttpStatus status;
    private final String codigo;

    public ErroApi(HttpStatus status, String codigo, String mensagem) {
        super(mensagem);
        this.status = status;
        this.codigo = codigo;
    }

    public HttpStatus status() {
        return status;
    }

    public String codigo() {
        return codigo;
    }
}
