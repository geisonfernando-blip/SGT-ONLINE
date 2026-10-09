package com.sgtaccess.online.conector;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint SÓ de teste, sob o prefixo do conector, que lança {@link RuntimeException} depois de
 * autenticado. Serve para provar que o {@link ConectorJwtFilter} NÃO converte exceção pós-autenticação
 * em 401: o filtro só protege a verificação do JWT e deixa o resto seguir para o tratamento padrão.
 */
@RestController
@RequestMapping("/api/conector/v1/teste")
public class ExcecaoDeTesteController {

    @GetMapping("/excecao")
    public String excecao() {
        throw new RuntimeException("exceção deliberada de teste");
    }
}
