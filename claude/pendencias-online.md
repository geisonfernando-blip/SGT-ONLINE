# Pendências do Online (25d)

Pendências do **Online**, que é um repositório separado do SGT. As pendências do SGT continuam em
`prompts/PENDENCIAS.md` (repositório do SGT) e **não** são replicadas aqui.

Legenda: **[BLOQUEIO]** = impede go-live / demonstração oficial. **[PÓS-15/10]** = pode esperar.

---

## [BLOQUEIO] PEND-ONLINE-06 — o IP do aceite LGPD não é prova enquanto o servidor real não for conferido

**Medido em 2026-10-11, na máquina de desenvolvimento (Docker Desktop, Windows):** o container **não**
vê o IP real do visitante. Toda conexão que chega por porta publicada aparece como `172.17.0.1`
(o gateway da bridge), venha de `localhost`, venha da LAN. Evidência e método em
`ensaio-rede-local.md` seção 1.

Consequência: o campo `ip` gravado no pré-cadastro (evidência do aceite LGPD, M2) vale, no ensaio,
**apenas como registro de que houve conexão** — não como identificação do visitante.

**O que falta:** repetir o mesmo teste no servidor real (Docker Engine em **Linux**, sem Docker
Desktop), com dois dispositivos na rede local, e confirmar se o container recebe o IP de origem.

Como repetir (nenhum dado pessoal, nenhum log de IP):

```bash
# 1. probe: um container que imprime o remoteAddress da conexão
docker run -d --name probe -p 8099:8099 node:24-alpine \
  node -e "require('http').createServer((q,s)=>s.end('fonte='+q.socket.remoteAddress)).listen(8099)"

# 2. de cada dispositivo, na rede local:
#    curl http://<IP-SERVIDOR>:8099
#    → esperado se o IP real chegar: 192.168.x.y diferentes por dispositivo
#    → se vier 172.17.0.1: o NAT do Docker está descartando a origem (o problema persiste)

docker rm -f probe
```

Se o IP real chegar, o go-live fica liberado com o código atual. Se não chegar, o limite por IP do
Online é **global** (todos os visitantes juntos) e a decisão passa a ser do responsável: aceitar isso,
ou mudar a topologia (`network_mode: host`, `macvlan`, ou proxy reverso **fora** do Docker).

**Ligada a:** PEND-ONLINE-01.

---

## PEND-ONLINE-01 — `X-Forwarded-For` só com proxy confiável

Hoje `ipDo()` usa **só** o `remoteAddr` e ignora o `X-Forwarded-For` (não há proxy reverso, e aceitá-lo
de qualquer origem deixaria a chave do limite por IP forjável por `curl`). Quando existir proxy, a
regra é: aceitar XFF **somente** se o `remoteAddr` estiver em `ONLINE_PROXIES_CONFIAVEIS`.

Atenção: XFF **não** resolve sozinho — ver PEND-ONLINE-06. Depende de o IP original chegar de verdade.

## PEND-ONLINE-02 — retenção de IP para segurança é decisão LGPD em aberto

O MVP guarda o IP no pré-cadastro só até o ack ou a expiração, e **não registra IP em log**. Prazo e
base legal são decisão do responsável. Não decidir aqui.

## PEND-ONLINE-03 — o limite por aparelho é fraco por natureza

O `X-Aparelho` é controlado pelo cliente: validar o formato (`[A-Za-z0-9_-]{1,64}`) só evita inflar o
mapa de limites. Os limites que valem mesmo são o **por IP** e o **por site/hora**.

## PEND-ONLINE-04 — cabeçalhos de segurança da página (CSP)

Dependem de servir o build de produção em servidor estático (hoje é `ng serve`). Proposta em
`proposta-headers-pagina.md`.

## PEND-ONLINE-05 — `RateLimiter` em memória

Janela fixa de 1 h, por instância, reinicia com o backend. Suficiente no MVP (instância única). Ganhou
descarte das chaves de janelas vencidas ao passar do teto; se a saturação persistir além de alguns
minutos em produção, trocar por janela deslizante persistida. **[PÓS-15/10]**

## PEND-ONLINE-07 — servindo pela mesma origem (proxy reverso)

`proxy_pass /api/` no servidor estático eliminaria o CORS e simplificaria a CSP para `connect-src
'self'`. Depende do build de produção (PEND-ONLINE-04). **[PÓS-15/10]**