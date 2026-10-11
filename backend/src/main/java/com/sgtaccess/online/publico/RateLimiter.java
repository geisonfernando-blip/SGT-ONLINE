package com.sgtaccess.online.publico;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Limite do QR em memória, por chave (IP, aparelho, site), janela fixa. {@code limite} nulo = sem
 * limite (PEND-096). Os valores vêm da CONFIG_SITE, nunca são hardcoded aqui.
 *
 * <p>O mapa cresce com quantas chaves diferentes chegam. Ao passar do teto, {@link #saturado()} roda a
 * varredura das janelas vencidas. Se ainda assim o mapa estiver acima do teto (muitas chaves vivas ao
 * mesmo tempo), o limite por aparelho é **deixado de fora**: criar mais chaves piora a saturação, e os
 * limites que valem são o por IP e o por site/hora. O aviso no log não traz o valor da chave.
 *
 * <p>ponytail: janela fixa em memória; reinicia com o backend (instância única no MVP). Trocar por
 * janela deslizante/persistida se o limite precisar sobreviver a restart.
 */
@Component
public class RateLimiter {

    /** Teto de chaves em memória; acima disso, varre as janelas anteriores. */
    private static final int TETO_CHAVES = 50_000;

    private static final Logger LOG = LoggerFactory.getLogger(RateLimiter.class);

    private record Entrada(long janela, int contagem) {
    }

    private final ConcurrentHashMap<String, Entrada> mapa = new ConcurrentHashMap<>();
    private final Duration janela;
    private final int tetoChaves;
    private boolean avisouSaturacao;

    public RateLimiter() {
        this(Duration.ofHours(1), TETO_CHAVES);
    }

    /** Janela e teto configuráveis: só os testes usam com valores curtos/pequenos. */
    RateLimiter(Duration janela, int tetoChaves) {
        this.janela = janela;
        this.tetoChaves = tetoChaves;
    }

    /**
     * true = o mapa está saturado e o limite por aparelho deve ser ignorado. Faz a varredura das chaves
     * vencidas quando o teto é ultrapassado e avisa no log (uma vez por episode de saturação).
     */
    public boolean saturado() {
        if (mapa.size() <= tetoChaves) {
            avisouSaturacao = false;
            return false;
        }
        long atual = janelaAtual();
        mapa.entrySet().removeIf(entrada -> entrada.getValue().janela() != atual);
        boolean s = mapa.size() > tetoChaves;
        if (s && !avisouSaturacao) {
            avisouSaturacao = true;
            // sem o valor da chave (pode ser IP, e IP é dado pessoal)
            LOG.warn("evento=rate_limit_saturado chaves={} teto={} janela_ms={}", mapa.size(), tetoChaves,
                    janela.toMillis());
        }
        return s;
    }

    /** true = permitido; false = estourou o limite. {@code limite} nulo = sempre permitido. */
    public boolean permitir(String chave, Integer limite) {
        if (limite == null) {
            return true;
        }
        long atual = janelaAtual();
        Entrada e = mapa.compute(chave, (k, v) -> v == null || v.janela() != atual
                ? new Entrada(atual, 1) : new Entrada(atual, v.contagem() + 1));
        return e.contagem() <= limite;
    }

    /** Para os testes: limpa o estado. */
    public void reset() {
        mapa.clear();
        avisouSaturacao = false;
    }

    private long janelaAtual() {
        return Instant.now().toEpochMilli() / janela.toMillis();
    }

    /** Só para os testes: tamanho do mapa de chaves. */
    int tamanho() {
        return mapa.size();
    }
}