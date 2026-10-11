package com.sgtaccess.online.publico;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Limite do QR em memória, por chave (IP, aparelho, site), janela fixa. {@code limite} nulo = sem
 * limite (PEND-096). Os valores vêm da CONFIG_SITE, nunca são hardcoded aqui.
 *
 * <p>O mapa cresce com quantas chaves diferentes chegam (IP, aparelho). Como a chave só vale dentro da
 * janela, as chaves vencidas são descartadas assim que o mapa passa do teto.
 *
 * <p>ponytail: janela fixa em memória; reinicia com o backend (instância única no MVP). Trocar por
 * janela deslizante/persistida se o limite precisar sobreviver a restart.
 */
@Component
public class RateLimiter {

    /** Teto de chaves em memória; ao passar dele, a próxima chamada varre as janelas anteriores. */
    private static final int TETO_CHAVES = 50_000;

    private record Entrada(long janela, int contagem) {
    }

    private final ConcurrentHashMap<String, Entrada> mapa = new ConcurrentHashMap<>();
    private final Duration janela;
    private final int tetoChaves;

    public RateLimiter() {
        this(Duration.ofHours(1), TETO_CHAVES);
    }

    /** Janela e teto configuráveis: só os testes usam com valores curtos/pequenos. */
    RateLimiter(Duration janela, int tetoChaves) {
        this.janela = janela;
        this.tetoChaves = tetoChaves;
    }

    /** true = permitido; false = estourou o limite. {@code limite} nulo = sempre permitido. */
    public boolean permitir(String chave, Integer limite) {
        if (limite == null) {
            return true;
        }
        long atual = janelaAtual();
        Entrada e = mapa.compute(chave, (k, v) -> v == null || v.janela() != atual
                ? new Entrada(atual, 1) : new Entrada(atual, v.contagem() + 1));
        if (mapa.size() > tetoChaves) {
            mapa.entrySet().removeIf(entrada -> entrada.getValue().janela() != atual);
        }
        return e.contagem() <= limite;
    }

    /** Para os testes: limpa o estado. */
    public void reset() {
        mapa.clear();
    }

    private long janelaAtual() {
        return Instant.now().toEpochMilli() / janela.toMillis();
    }

    /** Só para os testes: tamanho do mapa de chaves. */
    int tamanho() {
        return mapa.size();
    }
}