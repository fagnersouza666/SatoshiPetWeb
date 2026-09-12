# Documentação — Satoshi Pet Web

## Documentos

| Documento | Descrição |
|-----------|-----------|
| [PRD-Satoshi-Pet-Web-v2.0.md](./PRD-Satoshi-Pet-Web-v2.0.md) | Especificação funcional completa (produto, regras, arquitetura) |
| [backlog/README.md](./backlog/README.md) | Backlog de implementação — índice, convenções e ordem de entrega |
| [../AGENTS.md](../AGENTS.md) | Guia para agentes de IA — stack, invariantes e convenções do projeto |

## Backlog de implementação

O backlog está organizado em épicos em [backlog/](./backlog/):

- **Produto principal:** PWA Angular 22 (substitui React do PRD §16.1 — ver ADR sugerido em `00-definicao-tecnica.md`)
- **Backend:** Java 25 + Quarkus 3.33 LTS + PostgreSQL 18.6
- **Escopo:** produto completo conforme PRD v2.0 (sem MVP)

Comece por [backlog/README.md](./backlog/README.md) e [backlog/00-definicao-tecnica.md](./backlog/00-definicao-tecnica.md).

O [`.gitignore`](../.gitignore) na raiz do repositório exclui artefatos de build, segredos, volumes Docker e dados de Bitcoin de teste; o que entra ou não no Git está em [backlog/00-definicao-tecnica.md](./backlog/00-definicao-tecnica.md) §6.1.

## Ferramentas e comandos do workspace

As versões de referência ficam declaradas em [`.nvmrc`](../.nvmrc) (Node.js
22.22.3) e [`.java-version`](../.java-version) (Java 25 LTS). O backend usa o
Maven Wrapper versionado em `services/api/mvnw`, portanto não é necessário
instalar Maven no host.

Os scripts Angular usam `scripts/with-node.sh`, que executa o CLI com a versão
do `.nvmrc` por meio do pacote `node` do npm. Assim, os gates de build e teste
também funcionam em runners que não têm nvm, fnm, mise ou volta instalados.

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
| `npm run test:api` | Executa somente os testes da API |
| `npm run verify` | Confere a versão do produto, testa a PWA e executa `verify` da API |
| `npm run test:versao` | Testes do script `infra/scripts/versao.sh` |
| `./infra/scripts/versao.sh atual` | Mostra as versões da raiz, PWA e API |
| `./infra/scripts/versao.sh verificar` | Falha se as três versões divergirem |
| `./infra/scripts/check-pwa.sh` | Prova local da PWA (`versao.sh verificar` + testes) |
| `./infra/scripts/check-api.sh` | Prova local da API (`versao.sh verificar` + `mvnw verify`; exige Docker) |
