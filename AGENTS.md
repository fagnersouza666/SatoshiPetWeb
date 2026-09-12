# AGENTS.md — Satoshi Pet Web

Guia para agentes de IA que trabalham neste repositório.

## 1. O que é este projeto

**Satoshi Pet Web** é um dashboard pessoal de acumulação de Bitcoin com experiência de jogo: recebimentos reais on-chain alimentam uma criatura persistente em pixel art gerada por IA; um plano DCA calcula sugestões diárias de compra; clima, localização e horário compõem o ambiente.

- **Produto principal:** PWA instalável (não é um site com app anexo)
- **Idioma do produto e da documentação:** português do Brasil
- **Valores:** Bitcoin em sats/BTC; fiduciário em reais (BRL)
- **Escopo:** produto completo, sem MVP (PRD §3)

## 2. Estado atual do repositório

O repositório contém **somente documentação** — a implementação ainda não começou. Não há código-fonte, build ou testes neste momento.

```
Pet Web/
├── .gitignore                         # Artefatos de build, segredos, volumes locais
├── docs/
│   ├── PRD-Satoshi-Pet-Web-v2.0.md   # Especificação funcional completa (fonte da verdade)
│   └── backlog/                       # Backlog de implementação por épicos
│       ├── README.md                  # Índice, convenções e ordem de entrega
│       ├── 00-definicao-tecnica.md    # Stack, arquitetura, integrações, modelo de dados
│       ├── 01-fundacao-e-infraestrutura.md
│       ├── 02-contas-e-vinculos.md
│       ├── 03-monitor-bitcoin.md
│       ├── 04-motor-do-pet.md
│       ├── 05-arte-e-geracao-ia.md
│       ├── 06-dca-e-contabilidade.md
│       ├── 07-localizacao-e-clima.md
│       ├── 08-interface-e-pwa.md
│       ├── 09-notificacoes.md
│       ├── 10-admin-e-operacao.md
│       └── 11-criterios-de-aceite-e-qualidade.md
└── AGENTS.md
```

Estrutura planejada para o código (monorepo, ver `docs/backlog/00-definicao-tecnica.md` §6):

```
.gitignore         # Build, IDE, .env, volumes Docker, dados de regtest
apps/pwa/          # Angular 22 PWA (produto principal) + Dockerfile
services/api/      # Backend Quarkus + Dockerfile
packages/          # Tipos/eventos compartilhados (opcional)
infra/             # Docker Compose (stack completa), migrations, k8s
```

## 3. Stack definida

| Camada | Escolha |
|--------|---------|
| Frontend | **Angular 22** + TypeScript, standalone components, signals, `@angular/pwa` |
| Backend | **Java 25 LTS** + **Quarkus 3.33 LTS** (REST, WebSocket, Hibernate Panache, Flyway, Scheduler) |
| Banco | **PostgreSQL 18** — monetário em `NUMERIC`/`BigDecimal`, sats em `BIGINT`, tempo em `TIMESTAMPTZ` + zona IANA |
| Tempo real | WebSocket (reconexão com snapshot + cursor de eventos) |
| Imagens | Object storage S3-compatível (MinIO dev) |
| Bitcoin | Blockstream Esplora atrás de porta `BitcoinIndexerPort`; Mainnet só em produção, regtest/testnet em testes |
| Mercado | CoinGlass (Fear & Greed), provedor BTC/BRL a contratar |
| Clima | Open-Meteo |
| Infraestrutura | **Tudo containerizado (Docker)** — nenhum serviço instalado no host; Docker Compose no dev, mesmas imagens versionadas em produção (Compose ou K8s) |

> **Desvio registrado:** o PRD §16.1 menciona React; a decisão vigente é **Angular 22** (ver `00-definicao-tecnica.md` §1.2 e ADR sugerido).

## 4. Fontes da verdade e rastreabilidade

Antes de implementar qualquer comportamento, consulte:

1. **PRD v2.0** (`docs/PRD-Satoshi-Pet-Web-v2.0.md`) — regras de negócio. Referências no formato `§N`.
2. **CC-xx** — critérios de consolidação (PRD §20): decisões adotadas para lacunas/conflitos. Têm força de requisito.
3. **CA-xxx** — 70 critérios de aceite verificáveis (PRD §19 e `backlog/11-criterios-de-aceite-e-qualidade.md`).
4. **Histórias** — identificadores `{ÉPICO}-{NN}`: `FUND`, `CONTA`, `BTC`, `PET`, `ART`, `DCA`, `CLIMA`, `PWA`, `PUSH`, `ADMIN`.

## 5. Invariantes críticos (nunca violar)

Estas regras vêm do PRD e têm precedência sobre qualquer atalho de implementação:

### Custódia e segurança
- O sistema **nunca** guarda fundos, cria carteiras, executa compras/PIX/saques ou assina transações.
- **Nunca** solicitar seed, chave privada, xprv ou senha de carteira.
- Segredos (IA, e-mail, VAPID, APIs) somente no servidor; logs sem segredos, prompts privados ou códigos de recuperação.
- Respostas públicas usam DTOs explícitos — nunca serializar entidade privada integral (CA-009).

### Pet e alimentação
- **Somente recebimentos reais on-chain confirmados/pendentes alimentam o pet.** Compra declarada, sugestão DCA ou notificação **nunca** geram alimentação (eventos de compra nunca disparam `PET_FEEDING_APPLIED`).
- Um endereço = um pet permanente; aparência aprovada é imutável; não existe morte, reinício ou exclusão do pet.
- Reserva máxima de 168h; estados: ALIMENTADO → PENSANDO (24h) → CHATEADO (48h) → FAMINTO (72h) → CRÍTICO (96h) → HIBERNANDO.
- Retorno ao ovo: 24h contínuas de **saldo confirmado zero** (saída pendente não inicia carência).
- Alimentação única por recebimento lógico + pet; RBF/reorg recalculam, nunca duplicam.

### Contas
- Troca de endereço somente dentro de 72h da criação da conta — nem admin altera depois disso (CA-004).
- Várias contas podem acompanhar o mesmo endereço; dados privados permanecem isolados.
- Fonte alimentar única por pet (CC-05): a porção de 24h vem do plano de referência, não do plano de cada conta.

### DCA e contabilidade
- Onze faixas fixas de multiplicador (PRD §10.2) — não editáveis por usuário nem por admin comum.
- Uma sugestão por conta por ciclo (08h local); guardas contra duplicidade em mudança de fuso (CC-18).
- **Nunca usar ponto flutuante binário** em cálculos contábeis ou de sats (CC-10) — `BigDecimal`/`NUMERIC`.
- Sugestões e porções já aplicadas são snapshots imutáveis; correções recalculam saldos, não reescrevem histórico.

### Tempo e dados
- Fusos sempre por zona IANA, nunca offsets fixos.
- Validade de dados de mercado: desde 06h do dia civil anterior no fuso do ciclo (CC-17); dado vencido não é apresentado como atual.
- Falha de provedor ≠ saldo zero ≠ descarte de transação (CA-031).

## 6. Convenções de trabalho

### Idioma
- Código: identificadores em inglês; comentários e textos de UI em pt-BR.
- Commits, PRs, documentação e comunicação: **português do Brasil**.

### Commits
- Mensagens em pt-BR seguindo Conventional Commits: `feat:`, `fix:`, `chore:`, `docs:`, `refactor:`, `test:`.
- Commits separados por conjunto de regras/negócio — não misturar escopos no mesmo commit.
- **Nunca** incluir trailers de co-autoria de ferramentas de IA.

### Testes
- Todo método criado ou modificado deve ter teste (unitário e/ou integração).
- Backend: JUnit 5 + Testcontainers (PostgreSQL, regtest). Frontend: Vitest ou Karma/Jasmine.
- Cenários de aceite CA-xxx devem ser verificáveis com dados controlados.
- **Nunca** enviar fundos reais para validar software — usar regtest/testnet.

### Documentação
- Ao criar ou modificar comportamento, atualizar a documentação afetada em `docs/` no mesmo commit ou commit `docs:` adjacente.
- Decisões arquiteturais novas ou desvios do PRD devem ser registrados (ADR ou nota no documento pertinente).

### Dependências
- Usar sempre a última versão estável das bibliotecas/frameworks, salvo conflito comprovado no projeto.

## 7. Padrões de implementação

- **Idempotência:** consumidores, jobs e lançamentos financeiros com chaves idempotentes; travas de concorrência por domínio.
- **Outbox transacional:** gravar estado e publicar eventos sem perder nenhum dos lados.
- **Catálogo de eventos único** (PRD §16.3): `BITCOIN_TRANSACTION_OBSERVED`, `PET_FEEDING_APPLIED`, `DCA_RECOMMENDATION_GENERATED`, etc. — mesmo nome em backend, WebSocket e relatórios.
- **Portas e adaptadores** para integrações externas (ex.: `BitcoinIndexerPort`) — permitir trocar Esplora por Bitcoin Core + indexador.
- **Jobs agendados** (06h/07h/08h, monitor) com trava em banco e estado persistido; nada crítico depende de aba aberta.
- **Acessibilidade:** alvo WCAG 2.2 AA; estado do pet legível por texto; respeitar `prefers-reduced-motion`.
- **Offline:** leitura do último estado com aviso; alterações financeiras desabilitadas offline.

## 8. Comandos

Ainda não há código — esta seção será preenchida quando a fundação (épico `FUND`) for implementada. Referência planejada:

```bash
# Backend (services/api) — planejado
./mvnw quarkus:dev        # desenvolvimento
./mvnw test               # testes unitários
./mvnw verify             # testes + integração (Testcontainers)

# Frontend (apps/pwa) — planejado
npm start                 # dev server
npm test                  # testes unitários
npm run build             # build de produção

# Infra local — planejado
docker compose up -d      # PostgreSQL, MinIO, regtest
```

## 9. Referências

- [PRD v2.0](docs/PRD-Satoshi-Pet-Web-v2.0.md) — especificação funcional
- [Backlog](docs/backlog/README.md) — épicos, histórias e ordem de entrega
- [Definição técnica](docs/backlog/00-definicao-tecnica.md) — stack, arquitetura e integrações
- [Critérios de aceite](docs/backlog/11-criterios-de-aceite-e-qualidade.md) — matriz CA-001..CA-070 e SLOs
