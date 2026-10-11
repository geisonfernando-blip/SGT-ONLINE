# Proposta — cabeçalhos de segurança da PÁGINA do Online (25d)

**Situação: NÃO IMPLEMENTADO. Proposta escrita para liberação do responsável.**

O backend já emite `X-Content-Type-Options`, `X-Frame-Options: DENY`, `Referrer-Policy: no-referrer`
e `Cache-Control: no-store` nas respostas de `/api/*` (ver `CabecalhosFilter`). O que falta é o
conjunto da **página HTML** — que hoje é servida pelo `ng serve` (servidor de desenvolvimento), sem
possibilidade de cabeçalhos customizados.

## Por que não dá para fazer agora

- `frontend/Dockerfile` roda `npm start` → `ng serve --host 0.0.0.0 --port 4300`. O servidor de
  desenvolvimento não serve o build de produção e não aceita cabeçalho de resposta configurável.
- Trocar por servidor estático exige `ng build` (build pesado, depende da liberação) e uma nova
  imagem.

## O que fazer quando liberado

1. **Multi-stage no `frontend/Dockerfile`**: estágio `build` roda `npm ci` + `npm run build` (saída em
   `dist/online/browser`), estágio final serve esse diretório. Duas opções:
   - **nginx** (`nginx:1.29-alpine`) com um `frontend/nginx.conf` — ~15 linhas, controle total dos
     cabeçalhos, `try_files $uri /index.html` para o roteamento do Angular;
   - **só Java/Node estático** — mais código, sem ganho.
   Recomendo **nginx**.
2. **`frontend/nginx.conf`** com, no bloco `server`:

```nginx
add_header Content-Security-Policy "default-src 'none'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self'; connect-src 'self' http://localhost:8090 http://host.docker.internal:8090" always;
add_header X-Content-Type-Options "nosniff" always;
add_header Referrer-Policy "no-referrer" always;
add_header X-Frame-Options "DENY" always;
add_header Permissions-Policy "camera=(), microphone=(), geolocation=(), interest-cohort=()" always;
add_header Strict-Transport-Security "max-age=31536000" always;   # só quando houver HTTPS (M5: hoje não há TLS público)
```

Notas sobre a CSP:
- `connect-src` precisa da origem do backend porque o `apiBase` do `environment.ts` é absoluta. Ao
  passar para o servidor estático, o ideal é servir o backend no **mesmo domínio** com
`location /api/ { proxy_pass http://backend:8090; }` e `apiBase = '/api/publico'` — aí a CSP vira
   `connect-src 'self'` e o CORS deixa de ser necessário para o navegador. **Isso simplifica bastante e
   deve ser preferido** a manter duas origens.
- `style-src 'unsafe-inline'` é necessário porque o Angular injeta `<style>` no documento.
- `frame-ancestors 'none'` é o par moderno de `X-Frame-Options` (que já vai no backend).
- `Strict-Transport-Security` **não** entra agora: sem HTTPS na rede local (DEC M5), o HSTS no `localhost`
  pode travar o navegador em sessões posteriores.

3. **Testes** (a acrescentar junto): `nginx -t` no build e um teste de integração leve que faça `GET /`
   no container do frontend e afirme a presença dos cabeçalhos. Alternativa sem container: ler o
   `nginx.conf` como arquivo de texto no teste — mais barata, menos fiel.

4. O `meta name="referrer" content="no-referrer"` já está no `index.html` como proteção mínima
   enquanto o `ng serve` estiver no lugar.