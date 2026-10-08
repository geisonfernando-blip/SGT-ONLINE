package com.sgtaccess.online;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Verificação do JWT da instalação (contrato §2), com os mesmos primitivos que o conector usa para
 * assinar: ES256 (ECDSA P-256 + SHA-256, assinatura em P1363/R||S). Sem dependência extra de JWT.
 *
 * <p>O token tem {@code iss}, {@code aud}, {@code iat}, {@code exp} (≤ 60 s) e {@code jti} único. Aqui
 * verificamos assinatura, {@code exp} e {@code aud}; o {@code jti} (anti-replay) é conferido no
 * filtro/repositório.
 */
public final class JwtVerificador {

    public record Reivindicacoes(String iss, String aud, long exp, String jti) {
    }

    private static final ObjectMapper JSON = JsonMapper.builder().build();

    private JwtVerificador() {
    }

    /** Só o {@code iss} (para localizar a instalação/chave pública antes de verificar a assinatura). */
    public static String iss(String token) {
        String[] partes = token.split("\\.", -1);
        if (partes.length != 3) {
            throw invalido("JWT malformado");
        }
        JsonNode corpo = JSON.readTree(b64url(partes[1]));
        String iss = corpo.path("iss").asText();
        if (iss == null || iss.isBlank()) {
            throw invalido("iss ausente");
        }
        return iss;
    }

    /** Verifica assinatura + claims e devolve as reivindicações; lança se inválido. */
    public static Reivindicacoes verificar(String token, String chavePublicaPem, String audEsperado) {
        try {
            String[] partes = token.split("\\.", -1);
            if (partes.length != 3) {
                throw invalido("JWT malformado");
            }
            JsonNode cabecalho = JSON.readTree(b64url(partes[0]));
            JsonNode corpo = JSON.readTree(b64url(partes[1]));
            if (!"ES256".equals(cabecalho.path("alg").asText())) {
                throw invalido("alg não é ES256");
            }
            String assinado = partes[0] + "." + partes[1];
            byte[] assinatura = b64url(partes[2]);
            if (!assinaturaValida(publicKey(chavePublicaPem), assinado, assinatura)) {
                throw invalido("assinatura inválida");
            }
            String iss = corpo.path("iss").asText();
            String aud = corpo.path("aud").asText();
            long exp = corpo.path("exp").asLong();
            String jti = corpo.path("jti").asText();
            if (iss == null || iss.isBlank()) {
                throw invalido("iss ausente");
            }
            if (!audEsperado.equals(aud)) {
                throw invalido("aud divergente");
            }
            if (exp <= Instant.now().getEpochSecond()) {
                throw invalido("token expirado");
            }
            return new Reivindicacoes(iss, aud, exp, jti);
        } catch (GeneralSecurityException | RuntimeException e) {
            if (e instanceof IllegalArgumentException) {
                throw (IllegalArgumentException) e;
            }
            throw invalido("JWT inválido: " + e.getMessage());
        }
    }

    private static boolean assinaturaValida(PublicKey chave, String assinado, byte[] assinatura)
            throws GeneralSecurityException {
        Signature s = Signature.getInstance("SHA256withECDSAinP1363Format");
        s.initVerify(chave);
        s.update(assinado.getBytes(StandardCharsets.US_ASCII));
        return s.verify(assinatura);
    }

    private static PublicKey publicKey(String pem) throws GeneralSecurityException {
        String b64 = pem.replaceAll("-----(BEGIN|END) [A-Z ]+-----", "").replaceAll("\\s", "");
        return KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(b64)));
    }

    private static byte[] b64url(String valor) {
        int pad = (4 - valor.length() % 4) % 4;
        return Base64.getUrlDecoder().decode(valor + "=".repeat(pad));
    }

    private static IllegalArgumentException invalido(String motivo) {
        return new IllegalArgumentException(motivo);
    }
}
