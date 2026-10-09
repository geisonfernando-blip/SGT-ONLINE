package com.sgtaccess.online;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.Map;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Utilidade de teste do JWT da instalação: gera um par EC P-256, exporta a chave pública em PEM e
 * assina um JWT ES256 com assinatura em P1363 (R||S) — os mesmos primitivos que o {@link JwtVerificador}
 * usa para verificar. A chave PRIVADA fica só no par efêmero do teste, nunca em arquivo.
 */
public final class JwtTestUtil {

    private static final ObjectMapper JSON = JsonMapper.builder().build();

    private JwtTestUtil() {
    }

    public static KeyPair gerarP256() {
        try {
            KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
            g.initialize(new ECGenParameterSpec("secp256r1"));
            return g.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** PEM "PUBLIC KEY" (X.509 SubjectPublicKeyInfo) — o que vai para {@code instalacao.chave_publica}. */
    public static String publicaPem(ECPublicKey chave) {
        String b64 = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                .encodeToString(chave.getEncoded());
        return "-----BEGIN PUBLIC KEY-----\n" + b64 + "\n-----END PUBLIC KEY-----";
    }

    /** Assina {@code cabecalho}.{@code claims} com ES256 (P1363) e devolve o JWT completo. */
    public static String assinar(KeyPair par, Map<String, Object> cabecalho, Map<String, Object> claims) {
        try {
            String h = b64url(JSON.writeValueAsBytes(cabecalho));
            String c = b64url(JSON.writeValueAsBytes(claims));
            String assinado = h + "." + c;
            Signature s = Signature.getInstance("SHA256withECDSAinP1363Format");
            s.initSign(par.getPrivate());
            s.update(assinado.getBytes(StandardCharsets.US_ASCII));
            return assinado + "." + b64url(s.sign());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String b64url(byte[] b) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }
}
