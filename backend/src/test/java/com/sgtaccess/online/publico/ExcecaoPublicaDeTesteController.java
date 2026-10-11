package com.sgtaccess.online.publico;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint SÓ de teste, sob o prefixo público, que lança {@link RuntimeException} sem tratamento de
 * negócio. Serve para provar que exceção não tratada vira {@code 500 ERRO_INTERNO} no corpo do
 * contrato, sem path, timestamp, stack, SQL ou dado pessoal.
 */
@RestController
@RequestMapping("/api/publico/teste")
public class ExcecaoPublicaDeTesteController {

    @GetMapping("/excecao")
    public String excecao() {
        throw new RuntimeException("exceção deliberada de teste");
    }
}