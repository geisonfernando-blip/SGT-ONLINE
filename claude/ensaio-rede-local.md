# Ensaio na rede local — configuração, regras de rede e limites (Online 25d)

Documento de apoio à demonstração de 15/10. **Nada aqui foi aplicado**: as regras de firewall estão
descritas, não configuradas (a sessão não altera configuração do sistema sem autorização).

## 1. O que o container enxerga como IP do visitante

**Não é o IP real.** Medido em 2026-10-11 com um container de teste publicando uma porta e imprimindo o
`remoteAddress` da conexão:

| Origem da requisição | IP visto DENTRO do container |
|---|---|
| `http://localhost:8099` (host) | `172.17.0.1` |
| `http://192.168.1.200:8099` (mesma máquina, LAN) | `172.17.0.1` |
| `http://192.168.0.200:8099` (mesma máquina, outra LAN) | `172.17.0.1` |
| outro container | `172.17.0.3` |

O Docker Desktop faz NAT das conexões que chegam por porta publicada: o endereço de origem original é
descartado e o container vê sempre o gateway da bridge. **Todos os visitantes que chegarem pela rede
local vão aparecer com o mesmo IP.** Não há como o Online distinguir um celular do outro.

### Consequência prática (importante para o dia)

- O **limite por IP** deixa de ser por visitante e passa a valer para **todo mundo junto**. Com
  `ipDo()` usando só o `remoteAddr`, dois visitantes no mesmo site compartilham a mesma contagem.
  No ensaio, `limite_por_ip_hora` precisa estar **alto ou nulo**; caso contrário o segundo visitante
  toma 429. Confere o checklist do roteiro.
- Aceitar `X-Forwarded-For` **não resolve**: o XFF também é reescrito/descartado no mesmo NAT.
  A PEND-ONLINE-01 só faz sentido depois que existir proxy reverso **e** o IP original chegar de
  verdade.
- As proteções que hoje funcionam de verdade são o **teto por site/hora**, o **honeypot** e o **tempo
  mínimo** — todos já implementados e testados.

### Se um dia for necessário distinguir visitantes (fora do MVP)

Ordem de custo, do mais barato ao mais caro:

1. `network_mode: host` no backend na máquina do ensaio (o backend passa a ver o IP real da conexão) —
   quebra a portabilidade do compose;
2. rede `macvlan` com IP próprio por container;
3. proxy reverso **fora** do Docker (roteador/gateway) que faça o NAT só da porta interna.

Nenhuma dessas foi feita — é decisão de topologia do responsável.

> **Antes do go-live**, o mesmo teste tem de ser repetido no servidor real (Docker Engine em Linux), com
> dois dispositivos na rede local. Enquanto isso não for confirmado, o IP gravado no aceite LGPD
> **não vale como prova**. Rastreio: `PEND-ONLINE-06` em `pendencias-online.md` (bloqueio).

## 2. Frontend acessível pelo celular

`environment.ts` continua com `http://localhost:8090/api/publico`, que só resolve na própria máquina.
Para o ensaio existe a configuração `ensaio`:

- `frontend/src/app/environment.ensaio.ts` — `apiBase` derivado de `location.hostname`, ou seja, o
  backend é chamado **no mesmo host**, trocando só a porta (4300 → 8090). Não é preciso compilar com o
  IP, e o IP pode mudar (reserva de DHCP) sem quebrar o build;
- `frontend/angular.json` — configuração `ensaio` com `fileReplacements`;
- `frontend/package.json` — `npm run start:ensaio`, que já sobe com `--host 0.0.0.0 --port 4300`.

Comando no dia:

```bash
npm run start:ensaio        # dentro do container do frontend, ou local
```

## 3. Regras de firewall necessárias (DESCREVER, NÃO APLICAR)

O sistema precisa liberar **entrada TCP** nestas portas na máquina do ensaio, na rede local:

| Porta | Serviço | Quem acessa | Nota |
|---|---|---|---|
| **4300** | frontend (ng serve) | celulares na rede local | precisa escutar em `0.0.0.0`, não em `127.0.0.1` — já configurado |
| **8090** | backend (API) | celulares na rede local | é o que o `apiBase` do ensaio aponta |

Cuidados:

- liberar apenas em **perfil Private** da rede, nunca em Public;
- **não** abrir essas duas portas para a internet nem para redes desconhecidas;
- a porta **5433** (PostgreSQL) **não** deve ser liberada: só o container `db` precisa falar com ela;
- depois da demonstração, **remover** as regras.

Comandos de referência (PowerShell, **executar só com autorização**):

```powershell
# exemplo — perfil Private, protocolo TCP, apenas essas portas
New-NetFirewallRule -DisplayName "Online 4300 (ensaio)" -Direction Inbound -Action Allow `
  -Protocol TCP -LocalPort 4300 -Profile Private
New-NetFirewallRule -DisplayName "Online 8090 (ensaio)" -Direction Inbound -Action Allow `
  -Protocol TCP -LocalPort 8090 -Profile Private
```

Para conferir o que já está liberado (somente leitura):

```powershell
Get-NetFirewallRule -Direction Inbound -Enabled True |
  Where-Object { $_.DisplayName -like "Online*" }
```

## 4. Limites compatíveis com ensaio e demonstração

O `CONFIG_SITE` do site 52 define os limites (nada é hardcoded no Online). Para a demonstração:

| Limite | Valor sugerido | Por quê |
|---|---|---|
| `limites.porIpHora` | **nulo** | o IP real não chega ao container (seção 1): com valor baixo, todo mundo cai no mesmo balde e o 2º visitante leva 429 |
| `limites.porAparelhoHora` | 10 | folga para o ensaio; vale lembrar que é controlado pelo cliente (fraco) |
| `limites.porSiteHora` | **100** | teto real do site, o único limite que protege a portaria no ensaio |

Se for demonstrado o 429 na hora, subir o `porSiteHora` só para um valor baixo no momento da
demonstração e restaurar depois.