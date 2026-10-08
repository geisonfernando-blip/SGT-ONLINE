package com.sgtaccess.online.publico;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/** Página pública do QR: configuração do site e envio do pré-cadastro. Sem autenticação (balcão). */
@RestController
@RequestMapping("/api/publico")
public class PublicoController {

    private final PreCadastroService service;

    public PublicoController(PreCadastroService service) {
        this.service = service;
    }

    @GetMapping("/config-qr/{identificadorQr}")
    public Map<String, Object> config(@PathVariable String identificadorQr) {
        return service.configQr(identificadorQr);
    }

    @PostMapping("/pre-cadastro")
    public Map<String, Object> preCadastro(HttpServletRequest req, @RequestBody JsonNode corpo) {
        return service.preCadastro(req, corpo);
    }
}
