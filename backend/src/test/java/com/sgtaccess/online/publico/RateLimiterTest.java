package com.sgtaccess.online.publico;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * Limite do QR: {@code null} = sem limite, bloqueio ao estourar, chave vencida zera a janela e o mapa
 * de chaves não cresce indefinidamente.
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

    /**
     * O mapa não cresce indefinidamente: ao passar do teto, a chamada seguinte descarta as chaves de
     * janelas anteriores. O teto é gatilho da varredura, não limite rígido — dentro de uma mesma janela
     * o número de chaves é o de visitantes distintos naquela hora, que é o próprio objetivo.
     */
    @Test
    void mapaDeChavesNaoCresceSemTeto() throws Exception {
        RateLimiter rl = new RateLimiter(Duration.ofMillis(40), 10);
        for (int i = 0; i < 11; i++) {
            rl.permitir("ip:" + i, 5);
        }
        assertThat(rl.tamanho()).isGreaterThan(10);

        // janela seguinte: uma nova chamada dispara a varredura e as chaves vencidas somem
        Thread.sleep(60);
        rl.permitir("ip:nova", 5);
        assertThat(rl.tamanho()).isLessThan(11);
    }
}