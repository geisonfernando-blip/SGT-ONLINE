package com.sgtaccess.online.publico;

import com.sgtaccess.online.ConectorProperties;
import com.sgtaccess.online.Cpf;
import com.sgtaccess.online.ErroApi;
import com.sgtaccess.online.OnlineRepository;
import com.sgtaccess.online.OnlineRepository.ConfigSite;
import com.sgtaccess.online.OnlineRepository.PreCadastro;
import jakarta.servlet.http.HttpServletRequest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Página pública do QR: devolve a configuração do site (termo vindo da CONFIG_SITE) e recebe o
 * pré-cadastro. Gera o código curto, valida os campos e enfileira o PRE_CADASTRO_QR. Não decide nada
 * sobre autorização — só cadastro.
 */
@Service
public class PreCadastroService {

    private static final ObjectMapper JSON = JsonMapper.builder().build();
    private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");
    private static final Pattern DOC_TIPO = Pattern.compile("[\\p{L}0-9 _-]{2,30}");
    private static final Pattern DOC_NUMERO = Pattern.compile("[\\p{L}0-9./ -]{1,30}");
    private static final Pattern CONTROLE = Pattern.compile("\\p{Cntrl}");
    /** Identificador anônimo do aparelho: só letras, dígitos, `_` e `-`, até 64 (limite por aparelho). */
    private static final Pattern APARELHO = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    private final OnlineRepository repo;
    private final RateLimiter rateLimiter;
    private final FormTokenStore formToken;
    private final ConectorProperties props;
    private final SecureRandom random = new SecureRandom();

    public PreCadastroService(OnlineRepository repo, RateLimiter rateLimiter, FormTokenStore formToken,
                              ConectorProperties props) {
        this.repo = repo;
        this.rateLimiter = rateLimiter;
        this.formToken = formToken;
        this.props = props;
    }

    /** {@code GET /api/publico/config-qr/{identificadorQr}}: o que a página precisa para abrir o formulário. */
    public Map<String, Object> configQr(String identificadorQr) {
        ConfigSite c = config(identificadorQr);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("ativo", c.ativo());
        r.put("preCadastroExpiraHoras", c.expiraHoras());
        r.put("codigoCurtoDigitos", 6);
        r.put("termo", c.termoJson() == null ? null : JSON.readTree(c.termoJson()));
        r.put("formToken", formToken.emitir());
        return r;
    }

    /** {@code POST /api/publico/pre-cadastro}: valida, gera o código e enfileira o PRE_CADASTRO_QR. */
    public Map<String, Object> preCadastro(HttpServletRequest req, JsonNode corpo) {
        if (corpo == null || !corpo.isObject()) {
            throw new ErroApi(HttpStatus.BAD_REQUEST, "MENSAGEM_INVALIDA", "corpo obrigatório");
        }
        // anti-bot: honeypot (campo oculto) e tempo mínimo de preenchimento
        String honeypot = texto(corpo, "site_web");
        if (honeypot != null) {
            throw new ErroApi(HttpStatus.BAD_REQUEST, "MENSAGEM_INVALIDA", "solicitação inválida");
        }
        String token = texto(corpo, "formToken");
        if (token == null || !formToken.consumir(token, props.tempoMinimo())) {
            throw new ErroApi(HttpStatus.BAD_REQUEST, "MENSAGEM_INVALIDA", "formulário enviado cedo demais");
        }
        ConfigSite c = config(texto(corpo, "identificadorQr"));
        if (!c.ativo()) {
            throw new ErroApi(HttpStatus.NOT_FOUND, "QR_INVALIDO", "QR não é mais válido");
        }
        if (c.termoJson() == null) {
            throw new ErroApi(HttpStatus.CONFLICT, "SEM_TERMO", "cadastro indisponível neste momento");
        }
        if (!corpo.path("aceite").asBoolean(false)) {
            throw new ErroApi(HttpStatus.BAD_REQUEST, "MENSAGEM_INVALIDA", "aceite do termo obrigatório");
        }

        String ip = ipDo(req);
        String aparelho = aparelho(req, ip);
        if (!rateLimiter.permitir("ip:" + ip, c.limiteIp())) {
            throw limite();
        }
        if (!rateLimiter.permitir("ap:" + aparelho, c.limiteAparelho())) {
            throw limite();
        }
        if (!rateLimiter.permitir("site:" + c.siteId(), c.limiteSite())) {
            throw limite();
        }

        JsonNode termo = JSON.readTree(c.termoJson());
        String termoId = termo.path("termoId").asText();
        long termoVersao = termo.path("termoVersao").asLong();
        String termoSha = termo.path("sha256Texto").asText();

        JsonNode pessoa = corpo.path("pessoa");
        String nome = campo(pessoa, "nome", 200, true);
        String cpf = null;
        String cpfTexto = texto(pessoa, "cpf");
        if (cpfTexto != null) {
            cpf = Cpf.digitos(cpfTexto);
            if (cpf == null || !Cpf.valido(cpf)) {
                throw new ErroApi(HttpStatus.BAD_REQUEST, "MENSAGEM_INVALIDA", "cpf inválido");
            }
        }
        String docTipo = null;
        String docNumero = null;
        JsonNode documento = pessoa == null ? null : pessoa.get("documento");
        if (documento != null && !documento.isNull()) {
            docTipo = campo(documento, "tipo", 30, true).toUpperCase(java.util.Locale.ROOT);
            docNumero = campo(documento, "numero", 30, true);
            if (!DOC_TIPO.matcher(docTipo).matches()) {
                throw new ErroApi(HttpStatus.BAD_REQUEST, "MENSAGEM_INVALIDA", "documento.tipo inválido");
            }
            if (!DOC_NUMERO.matcher(docNumero).matches()) {
                throw new ErroApi(HttpStatus.BAD_REQUEST, "MENSAGEM_INVALIDA", "documento.numero inválido");
            }
        }
        if (cpf == null && docTipo == null) {
            throw new ErroApi(HttpStatus.BAD_REQUEST, "MENSAGEM_INVALIDA", "informe o CPF ou um documento");
        }
        String empresa = campo(pessoa, "empresa", 200, false);
        String telefone = campo(pessoa, "telefone", 30, false);
        String email = campo(pessoa, "email", 100, false);
        if (email != null && !EMAIL.matcher(email).matches()) {
            throw new ErroApi(HttpStatus.BAD_REQUEST, "MENSAGEM_INVALIDA", "email inválido");
        }

        String codigo = gerarCodigo(c.siteId());
        String idExterno = "onl_pre_" + hex(8);
        Instant agora = Instant.now();
        Instant expira = agora.plus(Duration.ofHours(c.expiraHoras()));
        String userAgent = req.getHeader("User-Agent");
        if (userAgent == null || userAgent.isBlank()) {
            userAgent = "unknown";
        }

        PreCadastro p = new PreCadastro(UUID.randomUUID(), idExterno, c.siteId(), codigo, expira, nome, cpf,
                docTipo, docNumero, empresa, telefone, email, termoId, termoVersao, termoSha, agora, ip,
                userAgent, 0);
        repo.inserirPreCadastro(p);

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("codigoCurto", codigo);
        return r;
    }

    private ConfigSite config(String identificadorQr) {
        return repo.configPorIdentificador(identificadorQr)
                .orElseThrow(() -> new ErroApi(HttpStatus.NOT_FOUND, "QR_INVALIDO", "QR não é mais válido"));
    }

    private String gerarCodigo(long siteId) {
        for (int i = 0; i < 100; i++) {
            String codigo = String.format("%06d", random.nextInt(1_000_000));
            if (!repo.codigoEmUso(siteId, codigo)) {
                return codigo;
            }
        }
        throw new ErroApi(HttpStatus.CONFLICT, "LIMITE_EXCEDIDO", "não foi possível gerar o código; tente novamente");
    }

    private String hex(int bytes) {
        byte[] b = new byte[bytes];
        random.nextBytes(b);
        StringBuilder sb = new StringBuilder(bytes * 2);
        for (byte x : b) {
            sb.append(String.format("%02x", x));
        }
        return sb.toString();
    }

    /**
     * Chave do limite por aparelho. O cabeçalho vem do cliente e é controlado por ele: aceitar só um
     * formato curto e estável evita inflar o mapa de limites, mas NÃO torna o limite por aparelho
     * confiável — um cliente pode mandar outro valor a cada envio. Os limites que valem mesmo são o por
     * IP e o por site/hora.
     */
    private static String aparelho(HttpServletRequest req, String ip) {
        String v = req.getHeader("X-Aparelho");
        return v != null && APARELHO.matcher(v.trim()).matches() ? v.trim() : ip;
    }

/**
 * IP do visitante: o {@code remoteAddr} da conexão, e nada mais.
 *
 * <p>O {@code X-Forwarded-For} é deliberadamente IGNORADO: hoje não há proxy reverso (o frontend chama
 * o backend direto, em outra porta) e, sem proxy confiável na frente, aceitar esse cabeçalho deixaria a
 * chave do limite por IP forjável por qualquer cliente. Quando existir proxy (mesmo domínio com
 * {@code proxy_pass}, PEND-ONLINE-01), aí sim passa a valer, aceitando XFF somente se o
 * {@code remoteAddr} estiver na lista de proxies confiáveis.
 */
private static String ipDo(HttpServletRequest req) {
    return req.getRemoteAddr();
}

    private static ErroApi limite() {
        return new ErroApi(HttpStatus.TOO_MANY_REQUESTS, "LIMITE_EXCEDIDO", "muitas tentativas; tente mais tarde");
    }

    private static String campo(JsonNode pai, String nome, int max, boolean obrigatorio) {
        String v = texto(pai, nome);
        if (v == null) {
            if (obrigatorio) {
                throw new ErroApi(HttpStatus.BAD_REQUEST, "MENSAGEM_INVALIDA", nome + " obrigatório");
            }
            return null;
        }
        if (v.length() > max) {
            throw new ErroApi(HttpStatus.BAD_REQUEST, "MENSAGEM_INVALIDA", nome + " acima de " + max + " caracteres");
        }
        if (CONTROLE.matcher(v).find()) {
            throw new ErroApi(HttpStatus.BAD_REQUEST, "MENSAGEM_INVALIDA", nome + " com caractere de controle");
        }
        return v;
    }

    private static String texto(JsonNode pai, String nome) {
        if (pai == null || !pai.hasNonNull(nome)) {
            return null;
        }
        JsonNode no = pai.get(nome);
        if (!no.isTextual()) {
            throw new ErroApi(HttpStatus.BAD_REQUEST, "MENSAGEM_INVALIDA", nome + " deve ser texto");
        }
        String v = no.asText().trim();
        return v.isEmpty() ? null : v;
    }
}
