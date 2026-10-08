package com.sgtaccess.online.conector;

import com.sgtaccess.online.ErroApi;
import com.sgtaccess.online.OnlineRepository.Instalacao;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/** Endpoints do contrato §3 (chamados pelo conector). Autenticados pelo {@link ConectorJwtFilter}. */
@RestController
@RequestMapping("/api/conector/v1")
public class ConectorController {

    private final ConectorService service;

    public ConectorController(ConectorService service) {
        this.service = service;
    }

    @GetMapping("/entrada")
    public Map<String, Object> entrada(@RequestAttribute(ConectorJwtFilter.ATTR_INSTALACAO) Instalacao inst,
                                       @RequestParam(defaultValue = "100") int limite,
                                       @RequestParam(defaultValue = "25") int espera,
                                       @RequestParam(required = false) String cursor) throws InterruptedException {
        if (limite < 1 || limite > ConectorService.MAX_LOTE) {
            throw new ErroApi(HttpStatus.BAD_REQUEST, "MENSAGEM_INVALIDA", "limite entre 1 e 100");
        }
        int e = Math.max(0, Math.min(espera, 25));
        return service.entrada(inst, parseCursor(cursor), limite, e);
    }

    @PostMapping("/entrada/ack")
    public Map<String, Object> ack(@RequestAttribute(ConectorJwtFilter.ATTR_INSTALACAO) Instalacao inst,
                                   @RequestBody JsonNode corpo) {
        JsonNode no = corpo == null ? null : corpo.get("cursor");
        if (no == null || !no.isTextual()) {
            throw new ErroApi(HttpStatus.BAD_REQUEST, "MENSAGEM_INVALIDA", "cursor obrigatório");
        }
        service.ack(inst, parseCursor(no.asText()));
        return Map.of("status", "ok");
    }

    @PostMapping("/saida")
    public Map<String, Object> saida(@RequestAttribute(ConectorJwtFilter.ATTR_INSTALACAO) Instalacao inst,
                                     @RequestBody JsonNode corpo) {
        service.aplicarSaida(inst, corpo);
        return Map.of("status", "ok");
    }

    @PostMapping("/heartbeat")
    public Map<String, Object> heartbeat(@RequestAttribute(ConectorJwtFilter.ATTR_INSTALACAO) Instalacao inst,
                                         @RequestBody(required = false) JsonNode corpo) {
        return Map.of("status", "ok");
    }

    private static Long parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(cursor);
        } catch (NumberFormatException e) {
            throw new ErroApi(HttpStatus.BAD_REQUEST, "MENSAGEM_INVALIDA", "cursor inválido");
        }
    }
}
