package com.sgtaccess.online.conector;

import static org.assertj.core.api.Assertions.assertThat;

import com.sgtaccess.online.ConectorProperties;
import com.sgtaccess.online.JwtTestUtil;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyPair;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Prova, com servidor REAL ({@code RANDOM_PORT}), que exceção pós-autenticação vira 5xx (não 401) e que
 * o corpo do 5xx é o padrão do Spring Boot — sem stack trace, sem SQL e sem dados pessoais.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ErroConectorCorpoTest {

    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired ConectorProperties props;

    KeyPair par;

    @BeforeEach
    void montar() {
        jdbc.update("DELETE FROM pre_cadastro_qr");
        jdbc.update("DELETE FROM mensagem_saida");
        jdbc.update("DELETE FROM config_site");
        jdbc.update("DELETE FROM instalacao_site");
        jdbc.update("DELETE FROM instalacao");
        jdbc.update("DELETE FROM jti_consumido");
        par = JwtTestUtil.gerarP256();
        jdbc.update("INSERT INTO instalacao (id, tenant, nome, chave_publica, ativo) VALUES (?, ?, ?, ?, TRUE)",
                "zzvq-inst", 1L, "zzvq",
                JwtTestUtil.publicaPem((java.security.interfaces.ECPublicKey) par.getPublic()));
        jdbc.update("INSERT INTO instalacao_site (id_instalacao, site_id) VALUES ('zzvq-inst', 52)");
    }

    @Test
    void excecaoDepoisDaAutenticacaoDevolve500ComCorpoMinimo() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/conector/v1/teste/excecao"))
                .header("Authorization", "Bearer " + jwt())
                .GET().build();
        HttpResponse<String> resp = HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString());

        assertThat(resp.statusCode()).isBetween(500, 599);
        String corpo = resp.body();
        assertThat(corpo).isNotBlank();
        // sem stack trace, sem SQL, sem mensagem interna e sem dado pessoal
        assertThat(corpo).doesNotContain("exceção deliberada", "\tat ", "at com.sgtaccess");
        assertThat(corpo).doesNotContain("SELECT", "UPDATE", "pre_cadastro", "config_site", "jti_consumido");
        assertThat(corpo).doesNotContain("zzvq", "52998224725", "192.168");
    }

    private String jwt() {
        long agora = Instant.now().getEpochSecond();
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("iss", "zzvq-inst");
        claims.put("aud", props.baseUrl());
        claims.put("iat", agora);
        claims.put("exp", agora + 60);
        claims.put("jti", UUID.randomUUID().toString());
        return JwtTestUtil.assinar(par, Map.of("alg", "ES256"), claims);
    }
}
