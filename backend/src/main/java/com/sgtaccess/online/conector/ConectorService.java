package com.sgtaccess.online.conector;

import com.sgtaccess.online.ErroApi;
import com.sgtaccess.online.OnlineRepository;
import com.sgtaccess.online.OnlineRepository.Instalacao;
import com.sgtaccess.online.OnlineRepository.PreCadastro;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Lado Online do contrato: entrega de {@code PRE_CADASTRO_QR} (long poll + ack) e recebimento de
 * {@code CONFIG_SITE} (idempotente por id, versão por chave). Sem regra de autorização: só transporte
 * e armazenamento.
 */
@Service
public class ConectorService {

    static final int VERSAO_CONTRATO = 1;
    static final int MAX_LOTE = 100;
    private static final ObjectMapper JSON = JsonMapper.builder().build();

    private final OnlineRepository repo;

    public ConectorService(OnlineRepository repo) {
        this.repo = repo;
    }

    /** {@code GET /entrada}: long poll até {@code espera}s; devolve mensagens com cursor acima do informado. */
    public Map<String, Object> entrada(Instalacao inst, Long aposCursor, int limite, int espera)
            throws InterruptedException {
        long nova = aposCursor == null ? 0 : aposCursor;
        List<Map<String, Object>> msgs = new ArrayList<>();
        long fim = System.currentTimeMillis() + espera * 1000L;
        while (true) {
            List<PreCadastro> lote = repo.proximos(nova, limite);
            if (!lote.isEmpty()) {
                for (PreCadastro p : lote) {
                    msgs.add(envelope(inst, p));
                    if (p.cursor() > nova) {
                        nova = p.cursor();
                    }
                }
                break;
            }
            if (System.currentTimeMillis() >= fim) {
                break;
            }
            Thread.sleep(500);
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("mensagens", msgs);
        r.put("cursor", String.valueOf(nova));
        return r;
    }

    /** {@code POST /entrada/ack}: confirma até o cursor e apaga os dados pessoais já entregues (LGPD). */
    @Transactional
    public void ack(Instalacao inst, long cursor) {
        repo.confirmarCursor(inst.id(), cursor);
        repo.purgarEntregues(cursor);
    }

    /** {@code POST /saida}: aplica um lote de CONFIG_SITE. 409 se alguma versão for obsoleta. */
    public void aplicarSaida(Instalacao inst, JsonNode corpo) {
        if (corpo == null || corpo.get("mensagens") == null || !corpo.get("mensagens").isArray()) {
            throw new ErroApi(HttpStatus.BAD_REQUEST, "MENSAGEM_INVALIDA", "corpo deve ser {mensagens[]}");
        }
        JsonNode mensagens = corpo.get("mensagens");
        if (mensagens.size() > MAX_LOTE) {
            throw new ErroApi(HttpStatus.PAYLOAD_TOO_LARGE, "LOTE_GRANDE_DEMAIS",
                    "no máximo " + MAX_LOTE + " mensagens por lote");
        }
        for (JsonNode m : mensagens) {
            aplicarConfigSite(inst, m);
        }
    }

    @Transactional
    void aplicarConfigSite(Instalacao inst, JsonNode m) {
        UUID id = uuid(m);
        if (id == null) {
            throw new ErroApi(HttpStatus.BAD_REQUEST, "MENSAGEM_INVALIDA", "id (UUID) ausente ou inválido");
        }
        if (repo.mensagemSaidaJaRecebida(id)) {
            return; // idempotência: já aplicada
        }
        int vc = m.path("versaoContrato").asInt(-1);
        if (vc != VERSAO_CONTRATO) {
            throw new ErroApi(HttpStatus.UNPROCESSABLE_ENTITY, "VERSAO_CONTRATO_NAO_SUPORTADA",
                    "versaoContrato deve ser " + VERSAO_CONTRATO);
        }
        String tenant = texto(m, "tenant");
        if (!String.valueOf(inst.tenant()).equals(tenant)) {
            throw new ErroApi(HttpStatus.BAD_REQUEST, "MENSAGEM_INVALIDA", "tenant diferente do da instalação");
        }
        long siteId = parseSite(m);
        if (!repo.sitePermitido(inst.id(), siteId)) {
            throw new ErroApi(HttpStatus.FORBIDDEN, "SITE_FORA_DO_ESCOPO", "site não permitido para a instalação");
        }
        long versao = m.path("versao").asLong(-1);
        String chave = texto(m, "chave");
        if (versao < 1 || chave == null) {
            throw new ErroApi(HttpStatus.BAD_REQUEST, "MENSAGEM_INVALIDA", "chave e versao obrigatórias");
        }
        if (versao <= repo.versaoConfigAplicada(siteId)) {
            throw new ErroApi(HttpStatus.CONFLICT, "VERSAO_OBSOLETA", "já existe versão maior para a chave");
        }

        JsonNode dados = m.path("dados");
        String identificadorQr = texto(dados, "identificadorQr");
        if (identificadorQr == null) {
            throw new ErroApi(HttpStatus.BAD_REQUEST, "MENSAGEM_INVALIDA", "identificadorQr obrigatório");
        }
        boolean ativo = dados.path("ativo").asBoolean();
        int expiraHoras = dados.path("preCadastroExpiraHoras").asInt(4);
        if (expiraHoras < 1 || expiraHoras > 24) {
            throw new ErroApi(HttpStatus.BAD_REQUEST, "MENSAGEM_INVALIDA",
                    "preCadastroExpiraHoras deve estar entre 1 e 24");
        }
        int digitos = dados.path("codigoCurtoDigitos").asInt(6);
        if (digitos != 6) {
            throw new ErroApi(HttpStatus.BAD_REQUEST, "MENSAGEM_INVALIDA", "codigoCurtoDigitos deve ser 6");
        }
        JsonNode limites = dados.path("limites");
        Integer limiteIp = inteiro(limites, "porIpHora");
        Integer limiteAparelho = inteiro(limites, "porAparelhoHora");
        Integer limiteSite = inteiro(limites, "porSiteHora");
        if (!limiteValido(limiteIp) || !limiteValido(limiteAparelho) || !limiteValido(limiteSite)) {
            throw new ErroApi(HttpStatus.BAD_REQUEST, "MENSAGEM_INVALIDA",
                    "limites, quando informados, devem ser >= 1");
        }
        String termoJson = dados.hasNonNull("termo") ? validarTermo(dados.get("termo")) : null;

        repo.aplicarConfigSite(id, siteId, chave, versao, identificadorQr, ativo, expiraHoras, limiteIp,
                limiteAparelho, limiteSite, termoJson);
    }

    /** Valida os campos da CONFIG_SITE que, inválidos, virariam erro do banco ou dado inconsistente. */
    private static boolean limiteValido(Integer v) {
        return v == null || v >= 1;
    }

    /** Termo null é válido; não-nulo exige os quatro campos e o sha256 conferindo com o texto. */
    private static String validarTermo(JsonNode termo) {
        String termoId = texto(termo, "termoId");
        long termoVersao = termo.path("termoVersao").asLong(-1);
        String sha = texto(termo, "sha256Texto");
        if (termoId == null || termoVersao < 1 || sha == null || !termo.hasNonNull("texto")
                || termo.get("texto").asText().trim().isEmpty()) {
            throw new ErroApi(HttpStatus.BAD_REQUEST, "MENSAGEM_INVALIDA",
                    "termo incompleto (termoId, termoVersao, sha256Texto, texto)");
        }
        if (!sha.matches("[0-9a-f]{64}")) {
            throw new ErroApi(HttpStatus.BAD_REQUEST, "MENSAGEM_INVALIDA", "sha256Texto do termo inválido");
        }
        if (!sha.equals(sha256(termo.get("texto").asText()))) {
            throw new ErroApi(HttpStatus.BAD_REQUEST, "MENSAGEM_INVALIDA",
                    "sha256Texto não confere com o texto do termo");
        }
        return termo.toString();
    }

    private static String sha256(String texto) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(h.length * 2);
            for (byte b : h) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Envelope (contrato §4) de um PRE_CADASTRO_QR, com `tenant`/`site` como ids do SGT em texto (PEND-099). */
    private static Map<String, Object> envelope(Instalacao inst, PreCadastro p) {
        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("preCadastroId", p.idExterno());
        dados.put("codigoCurto", p.codigoCurto());
        dados.put("expiraEm", OffsetDateTime.ofInstant(p.expiraEm(), ZoneOffset.UTC).toString());
        Map<String, Object> pessoa = new LinkedHashMap<>();
        pessoa.put("nome", p.nome());
        if (p.cpf() != null) {
            pessoa.put("cpf", p.cpf());
        }
        if (p.docTipo() != null) {
            Map<String, Object> doc = new LinkedHashMap<>();
            doc.put("tipo", p.docTipo());
            doc.put("numero", p.docNumero());
            pessoa.put("documento", doc);
        }
        if (p.empresa() != null) {
            pessoa.put("empresa", p.empresa());
        }
        if (p.telefone() != null) {
            pessoa.put("telefone", p.telefone());
        }
        if (p.email() != null) {
            pessoa.put("email", p.email());
        }
        dados.put("pessoa", pessoa);
        Map<String, Object> aceite = new LinkedHashMap<>();
        aceite.put("termoId", p.termoId());
        aceite.put("termoVersao", p.termoVersao());
        aceite.put("sha256Texto", p.termoSha256());
        aceite.put("aceitoEm", OffsetDateTime.ofInstant(p.aceitoEm(), ZoneOffset.UTC).toString());
        aceite.put("ip", p.ip());
        aceite.put("userAgent", p.userAgent());
        dados.put("aceiteTermo", aceite);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.id().toString());
        m.put("tipo", "PRE_CADASTRO_QR");
        m.put("versaoContrato", VERSAO_CONTRATO);
        m.put("tenant", String.valueOf(inst.tenant()));
        m.put("site", String.valueOf(p.siteId()));
        m.put("criadoEm", OffsetDateTime.ofInstant(Instant.now(), ZoneOffset.UTC).toString());
        m.put("chave", "precad:" + p.idExterno());
        m.put("versao", 1);
        m.put("correlacao", p.id().toString());
        m.put("dados", dados);
        return m;
    }

    private static UUID uuid(JsonNode m) {
        if (m == null || !m.isObject() || !m.hasNonNull("id")) {
            return null;
        }
        try {
            return UUID.fromString(m.get("id").asText());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static long parseSite(JsonNode m) {
        try {
            return Long.parseLong(texto(m, "site"));
        } catch (NumberFormatException e) {
            throw new ErroApi(HttpStatus.FORBIDDEN, "SITE_FORA_DO_ESCOPO", "site ausente ou inválido");
        }
    }

    private static String texto(JsonNode no, String campo) {
        return no != null && no.hasNonNull(campo) ? no.get(campo).asText().trim() : null;
    }

    private static Integer inteiro(JsonNode no, String campo) {
        if (no == null || !no.hasNonNull(campo)) {
            return null;
        }
        return no.get(campo).asInt();
    }
}
