package com.sgtaccess.online;

import static org.assertj.core.api.Assertions.assertThat;

import com.sgtaccess.online.publico.FormTokenStore;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * Tempo mínimo de preenchimento (anti-bot, DEC §4/§11): o token emitido ao carregar o formulário só é
 * aceito depois do tempo mínimo, e é de uso único.
 */
class FormTokenStoreTest {

    @Test
    void tokenValidoPassaAposOTempoMinimo() {
        FormTokenStore store = new FormTokenStore();
        String t = store.emitir();
        assertThat(store.consumir(t, Duration.ZERO)).isTrue();
    }

    @Test
    void tokenNovoDemaisNaoPassa() {
        FormTokenStore store = new FormTokenStore();
        String t = store.emitir();
        assertThat(store.consumir(t, Duration.ofSeconds(10))).isFalse();
    }

    @Test
    void tokenEhDeUsoUnico() {
        FormTokenStore store = new FormTokenStore();
        String t = store.emitir();
        assertThat(store.consumir(t, Duration.ZERO)).isTrue();
        assertThat(store.consumir(t, Duration.ZERO)).isFalse();
    }

    @Test
    void tokenDesconhecidoNaoPassa() {
        FormTokenStore store = new FormTokenStore();
        assertThat(store.consumir("nao-emitido", Duration.ZERO)).isFalse();
        assertThat(store.consumir(null, Duration.ZERO)).isFalse();
    }
}
