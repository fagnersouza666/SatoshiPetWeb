# Documentação — Satoshi Pet Web

## Documentos

| Documento | Descrição |
|-----------|-----------|
| [PRD-Satoshi-Pet-Web-v2.0.md](./PRD-Satoshi-Pet-Web-v2.0.md) | Especificação funcional completa (produto, regras, arquitetura) |
| [backlog/README.md](./backlog/README.md) | Backlog de implementação — índice, convenções e ordem de entrega |
| [../AGENTS.md](../AGENTS.md) | Guia para agentes de IA — stack, invariantes e convenções do projeto |
| [operacao/tls.md](./operacao/tls.md) | Perfis TLS staging/prod e contrato com o proxy/ingress |

## Contratos

| Documento | Descrição |
|-----------|-----------|
| [contratos/magic-link.md](./contratos/magic-link.md) | Solicitação de magic link (`POST /auth/magic-link`) |
| [contratos/magic-link-verify.md](./contratos/magic-link-verify.md) | Verificação do token e decisão registro vs sessão |
| [contratos/sessao-csrf.md](./contratos/sessao-csrf.md) | Cookie `sp_session`, header CSRF e revogação |
| [contratos/websocket-endereco.md](./contratos/websocket-endereco.md) | Canal WS por endereço — snapshot, cursor e RECONNECT |
| [contratos/bitcoin-indexer-port.md](./contratos/bitcoin-indexer-port.md) | Porta de indexador on-chain (Esplora/stub) |
| [contratos/eventos-bitcoin.md](./contratos/eventos-bitcoin.md) | Catálogo versionado de eventos e transições do monitor Bitcoin |
| [contratos/eventos-bitcoin-redaction.md](./contratos/eventos-bitcoin-redaction.md) | Allowlist e redaction da projeção pública dos eventos Bitcoin |
| [contratos/eventos-pet.md](./contratos/eventos-pet.md) | Catálogo `PET_*` (payload público, CA-009) |
| [contratos/pet-apresentacao-e-stats.md](./contratos/pet-apresentacao-e-stats.md) | Fila de apresentação, skip e estatísticas do pet da conta |
| [contratos/object-storage.md](./contratos/object-storage.md) | Endpoints, buckets e variáveis do MinIO local |

## Operação

| Documento | Descrição |
|-----------|-----------|
| [operacao/csp.md](./operacao/csp.md) | Política CSP da PWA, origens permitidas e verificação |
| [operacao/cors.md](./operacao/cors.md) | Allowlist de origens da API e verificação de rejeição |
| [operacao/health.md](./operacao/health.md) | Endpoints de saúde, sondas e contrato sem dados sensíveis |
| [operacao/rate-limite.md](./operacao/rate-limite.md) | Limites configuráveis por IP e conta |

## Backlog de implementação

O backlog está organizado em épicos em [backlog/](./backlog/):

- **Produto principal:** PWA Angular 22 (substitui React do PRD §16.1 — ver ADR sugerido em `00-definicao-tecnica.md`)
- **Backend:** Java 25 + Quarkus 3.33 LTS + PostgreSQL 18.6
- **Escopo:** produto completo conforme PRD v2.0 (sem MVP)

Comece por [backlog/README.md](./backlog/README.md) e [backlog/00-definicao-tecnica.md](./backlog/00-definicao-tecnica.md).

O [`.gitignore`](../.gitignore) na raiz do repositório exclui artefatos de build, segredos, volumes Docker e dados de Bitcoin de teste; o que entra ou não no Git está em [backlog/00-definicao-tecnica.md](./backlog/00-definicao-tecnica.md) §6.1.

## Ferramentas e comandos do workspace

As versões de referência ficam declaradas em [`.nvmrc`](../.nvmrc) (Node.js
22.23.2) e [`.java-version`](../.java-version) (Java 25 LTS). O backend usa o
Maven Wrapper versionado em `services/api/mvnw`, portanto não é necessário
instalar Maven no host.

**Ambiente oficial:** Linux (CI, deploy, dev principal). **Windows:** dev
ocasional — use `npm run …` na raiz; não dependa de bash.

Os scripts Angular usam `scripts/with-node.mjs` (CLI com versão do `.nvmrc`).
Os gates `check:pwa` / `check:api` / `versao` são `.mjs` cross-platform; os
`.sh` em `infra/scripts/` são atalhos para Linux/macOS que delegam aos `.mjs`.

Os comandos abaixo devem ser executados na raiz do repositório:

| Comando | Finalidade |
|---------|------------|
| `npm run start` | Inicia a PWA Angular em desenvolvimento |
| `npm run start:pwa` | Inicia somente a PWA Angular |
| `npm run start:api` | Inicia a API Quarkus em modo dev |
| `npm run build` | Compila PWA e API |
| `npm run build:pwa` | Compila somente a PWA |
| `npm run build:api` | Empacota somente a API, sem executar testes |
| `npm test` | Executa os testes unitários da PWA e da API |
| `npm run test:pwa` | Executa somente os testes da PWA, sem watch |
| `npm --prefix apps/pwa run lint` | Verifica a formatação da fonte da PWA com Prettier |
| `npm run test:api` | Executa somente os testes da API |
| `npm run verify` | Confere a versão do produto, testa a PWA e executa `verify` da API |
| `npm run test:versao` | Testes do script `infra/scripts/versao.mjs` |
| `npm run test:infra` | Teste do bootstrap idempotente dos buckets MinIO |
| `npm run test:csp` | Verifica a CSP canônica no Caddy, nginx e API |
| `npm run versao -- atual` | Mostra as versões da raiz, PWA e API |
| `npm run versao -- verificar` | Falha se as três versões divergirem |
| `npm run check:pwa` | Prova local da PWA (`versao verificar` + testes) |
| `npm run check:api` | Prova local da API (`versao verificar` + `mvnw verify`; exige Docker) |
