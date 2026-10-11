package com.sgtaccess.online.publico;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * Limite do QR: {@code null} = sem limite, bloqueio ao estourar, chave vencida zera a janela e o mapa
 * de chaves não cresce indefinidamente. Com o mapa saturado, o aviso é dado e o chamador passa a
 * aplicar só IP e site/hora.
 */
class RateLimiterTest {

    @Test
    void limiteNuloNuncaBloqueia() {
        RateLimiter rl = new RateLimiter();
        for (int i = 0; i < 1000; i++) {
            assertThat(rl.permitir("ip:1.2.3.4", null)).isTrue();
        }
        assertThat(rl.tamanho()).isZero();
    }

    @Test
    void bloqueiaAposOTetoEChavesNaoCompartilhamContagem() {
        RateLimiter rl = new RateLimiter();
        assertThat(rl.permitir("ip:1.2.3.4", 2)).isTrue();
        assertThat(rl.permitir("ip:1.2.3.4", 2)).isTrue();
        assertThat(rl.permitir("ip:1.2.3.4", 2)).isFalse();
        assertThat(rl.permitir("ip:5.6.7.8", 2)).isTrue();
    }

    @Test
    void chaveVencidaZeraAJanela() throws Exception {
        RateLimiter rl = new RateLimiter(Duration.ofMillis(30), 1000);
        assertThat(rl.permitir("ip:1.2.3.4", 1)).isTrue();
        assertThat(rl.permitir("ip:1.2.3.4", 1)).isFalse();

        Thread.sleep(40);
        // janela nova: a mesma chave volta a ter contagem própria
        assertThat(rl.permitir("ip:1.2.3.4", 1)).isTrue();
    }

    /** Abaixo do teto nada acontece. */
    @Test
    void mapaAbaixoDoTetoNaoFicaSaturado() {
        RateLimiter rl = new RateLimiter(Duration.ofHours(1), 10);
        for (int i = 0; i < 10; i++) {
            rl.permitir("ip:" + i, 5);
        }
        assertThat(rl.saturado()).isFalse();
        assertThat(rl.tamanho()).isEqualTo(10);
    }

    /**
     * A varredura só pode descartar chaves de janelas anteriores; dentro da janela corrente o número de
     * chaves é o de visitantes distintos naquela hora, que é o próprio objetivo. Passada a janela, a
     * varredura limpa e a saturação desaparece.
     */
    @Test
    void saturacaoDesapareceDepoisDaVarreduraDasChavesVencidas() throws Exception {
        RateLimiter rl = new RateLimiter(Duration.ofMillis(40), 10);
        for (int i = 0; i < 11; i++) {
            rl.permitir("ip:" + i, 5);
        }
        assertThat(rl.saturado()).isTrue();

        Thread.sleep(60); // janela nova
        assertThat(rl.saturado()).isFalse();
        assertThat(rl.tamanho()).isLessThan(11);
    }
}