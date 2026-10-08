package com.sgtaccess.online.publico;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Token de formulário para o tempo mínimo de preenchimento (anti-bot, DEC §4/§11). Emitido ao carregar
 * o formulário e consumido (uso único) no envio: o envio só passa se o formulário ficou aberto pelo
 * menos {@code tempoMinimo}. Sem dado pessoal.
 */
@Component
public class FormTokenStore {

    private final ConcurrentHashMap<String, Instant> tokens = new ConcurrentHashMap<>();

    public String emitir() {
        String t = UUID.randomUUID().toString();
        tokens.put(t, Instant.now());
        return t;
    }

    /** true se o token existe e já passou o tempo mínimo; o token é consumido em qualquer caso. */
    public boolean consumir(String token, Duration tempoMinimo) {
        if (token == null) {
            return false;
        }
        Instant emitido = tokens.remove(token);
        return emitido != null && Duration.between(emitido, Instant.now()).compareTo(tempoMinimo) >= 0;
    }

    /** Para os testes: limpa o estado. */
    public void reset() {
        tokens.clear();
    }
}
