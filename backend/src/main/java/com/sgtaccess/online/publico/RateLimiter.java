package com.sgtaccess.online.publico;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Limite do QR em memória, por chave (IP, aparelho, site), janela fixa de 1 h. {@code limite} nulo =
 * sem limite (PEND-096). Os valores vêm da CONFIG_SITE, nunca são hardcoded aqui.
 *
 * <p>ponytail: janela fixa em memória; reinicia com o backend (instância única no MVP). Trocar por
 * janela deslizante/persistida se o limite precisar sobreviver a restart.
 */
@Component
public class RateLimiter {

    private static final long JANELA_MS = 3_600_000L;

    private record Entrada(long janela, int contagem) {
    }

    private final ConcurrentHashMap<String, Entrada> mapa = new ConcurrentHashMap<>();

    /** true = permitido; false = estourou o limite. {@code limite} nulo = sempre permitido. */
    public boolean permitir(String chave, Integer limite) {
        if (limite == null) {
            return true;
        }
        long janela = Instant.now().toEpochMilli() / JANELA_MS;
        Entrada e = mapa.compute(chave, (k, v) -> v == null || v.janela() != janela
                ? new Entrada(janela, 1) : new Entrada(janela, v.contagem() + 1));
        return e.contagem() <= limite;
    }

    /** Para os testes: limpa o estado. */
    public void reset() {
        mapa.clear();
    }
}
