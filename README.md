# Satoshi Pet Web

Dashboard pessoal de acumulação de Bitcoin em formato de PWA instalável. O
pet em pixel art evolui com cada recebimento on-chain real. A stack é composta
por uma aplicação Angular 22 e uma API Quarkus; **todos os serviços rodam em
containers Docker** — sem instalar Node.js, Java ou qualquer serviço no host
(ADR-002).

## Stack

| Componente | Tecnologia |
| --- | --- |
| PWA | Angular 22 + TypeScript 6 |
| API | Java 25 + Quarkus 3.33 LTS |
| Persistência | PostgreSQL 18.6 |
| Object storage | MinIO (dev) / S3-compatível (prod) |
| Proxy reverso | Caddy 2 com TLS automático |
| E-mail dev | Mailpit (captura SMTP local) |
| Bitcoin regtest | Bitcoin Core 29.0 (rede isolada) |

Versões de referência em `.nvmrc` (Node 22.23.2), `.java-version` (Java 25),
`apps/pwa/package.json` e `services/api/pom.xml`.

---

## Ambientes de desenvolvimento

| Ambiente | Papel |
| --- | --- |
| **Linux** | Oficial — CI, staging, produção e dev principal |
| **Windows** | Dev ocasional — mesmos comandos via `npm run …` |
| **macOS** | Igual ao Linux (`./infra/scripts/*.sh` ou `npm run …`) |

A **fonte da verdade** dos gates é o Node (`.mjs` em `infra/scripts/`). Os
`.sh` são atalhos finos para bash no Linux/macOS; a CI e o `package.json` da
raiz chamam os `.mjs` diretamente.

## Pré-requisitos

| Modo | O que precisa |
| --- | --- |
| **Stack Docker** (`docker compose up`) | Docker Engine + Compose v2 |
| **Dev no host** | Node.js 22+ (`.nvmrc`), Java 25 (`.java-version`), Docker para `check:api` |

### Comandos (Linux e Windows)

Na raiz do repositório — **use sempre `npm run` no Windows**; no Linux, `npm
run` ou `./infra/scripts/check-*.sh` (equivalentes):

```bash
npm run check:pwa      # versão + testes Angular
npm run check:api      # versão + mvnw verify (Docker ligado)
npm run verify         # os dois
npm run versao -- atual
```

No PowerShell, não use `./infra/scripts/*.sh`. Caminhos com espaço (ex.:
`C:\Projetos\Pet Web`) são suportados.

Verifique o Docker antes de `check:api`:

```bash
docker compose version
docker info
```

---

## Início rápido

### 1. Clone e copie o arquivo de variáveis de ambiente

```bash
git clone https://github.com/seu-org/satoshi-pet-web.git
cd satoshi-pet-web
cp .env.example .env   # revise os defaults; não commite .env
```

### 2. Suba a stack

**Stack básica** (PostgreSQL, MinIO, API, PWA, Caddy):

```bash
docker compose -f infra/docker-compose.yml up --build
```

**Stack completa** — inclui Mailpit (e-mail de captura) e Bitcoin regtest:

```bash
docker compose -f infra/docker-compose.yml --profile dev up --build
```

### 3. Acesse os serviços

| Serviço | Endereço |
| --- | --- |
| PWA (via Caddy HTTPS) | <https://localhost> |
| PWA (nginx direto) | <http://localhost:4200> |
| API (direta) | <http://localhost:8080> |
| PostgreSQL | `localhost:5432` |
| MinIO API | <http://localhost:9000> |
| MinIO Console | <http://localhost:9001> |
| Mailpit UI | <http://localhost:8025> |
| Bitcoin RPC | <http://localhost:18443> |

### 4. (Opcional) Confiar no certificado TLS local do Caddy

Para acessar `https://localhost` sem aviso de certificado:

```bash
docker exec satoshi-caddy caddy trust
# Reinicie o navegador depois
```

### 5. Parar os serviços

```bash
docker compose -f infra/docker-compose.yml down
# Para também remover os volumes (reset completo):
docker compose -f infra/docker-compose.yml down -v
```

---

## Desenvolvimento sem a composição

Os scripts da raiz continuam sendo a entrada padronizada para as verificações
do workspace. Para desenvolvimento direto com Node.js e Java instalados no
host, inicie cada serviço em um terminal separado a partir da raiz:

```bash
# terminal 1 — PWA Angular (requer Node.js 22.23.2 via .nvmrc)
npm run start:pwa
```

```bash
# terminal 2 — API Quarkus em modo dev (requer Java 25)
npm run start:api
```

---

## Verificação local antes de commit/push

Porta de saída antes de qualquer `git push`. A CI (Linux) roda os mesmos
passos via `npm run check:pwa` e `npm run check:api`.

```bash
npm run check:pwa    # PWA: versão + Vitest
npm run check:api    # API: versão + mvnw verify (Docker + Testcontainers)
npm run verify       # ambos
```

No Linux você também pode `./infra/scripts/check-pwa.sh` — delega ao mesmo
`.mjs`. No Windows, use só `npm run`.

> **Anti-padrão:** `ng build` ou `mvnw compile` verde **não** substitui os
> scripts completos. O gate é o script — não uma etapa dele.

---

## Versionamento

PWA, API e `package.json` da raiz são sempre mantidos na mesma versão `X.Y.Z`.
Nunca edite versão à mão — use o script (cross-platform):

```bash
npm run versao -- atual          # mostra versão atual
npm run versao -- verificar      # falha se divergirem
npm run versao -- funcionalidade # 0.1.0 → 0.2.0 (nova funcionalidade)
npm run versao -- corrigir       # 0.1.0 → 0.1.1 (correção de bug)
```

---

## CI/CD — GitHub Actions

O pipeline em `.github/workflows/ci.yml` executa automaticamente em push para
`main`/`develop` e em pull requests:

1. **verificar-pwa** — `check-pwa.sh` (Node 22.23.2)
2. **verificar-api** — `check-api.sh` (Java 25 + Docker + PostgreSQL 18.6)
3. **build-imagens** — `docker build` para PWA e API, tagueado com `X.Y.Z`

Em pushes para `main`, as imagens são publicadas no GitHub Container Registry
(`ghcr.io`).

---

## Produção

Para implantar usando imagens pré-construídas pelo CI:

```bash
# Na máquina de produção — defina variáveis obrigatórias no .env
export APP_VERSION=0.1.0
export DOMAIN=satoshi-pet.example.com
# ... demais variáveis de produção (ver .env.example)

docker compose \
  -f infra/docker-compose.yml \
  -f infra/docker-compose.prod.yml \
  pull

docker compose \
  -f infra/docker-compose.yml \
  -f infra/docker-compose.prod.yml \
  up -d
```

O Caddy emite certificado Let's Encrypt automaticamente quando o DNS do
`DOMAIN` aponta para o servidor.

---

## Segurança e dados locais

- Não versionar `.env`, credenciais, chaves privadas, seeds ou `wallet.dat`.
- Não solicitar nem armazenar seed, chave privada, xprv ou senha de carteira.
- Dados de containers e Bitcoin de teste ficam fora do Git conforme o
  [`.gitignore`](.gitignore).
- Alterações financeiras ficam indisponíveis offline; use regtest para validar
  integrações Bitcoin. Nunca use Mainnet para testes.

---

## Estrutura

```text
apps/pwa/           # PWA Angular 22
  Dockerfile        # Build multi-stage Node → nginx
  nginx.conf        # Serve SPA + proxy /api e /ws para api:8080
services/api/       # API Quarkus (Java 25)
  Dockerfile        # Build multi-stage Maven → JRE
infra/
  docker-compose.yml        # Stack completa de desenvolvimento
  docker-compose.prod.yml   # Overrides de produção
  caddy/
    Caddyfile               # Proxy reverso dev (TLS local)
    Caddyfile.prod          # Proxy reverso produção (Let's Encrypt)
  scripts/
    versao.sh               # Gerencia versão única PWA + API
    check-pwa.sh            # Gate de verificação da PWA
    check-api.sh            # Gate de verificação da API
docs/
  adr/                      # Decisões arquiteturais (ADR-001, ADR-002)
  backlog/                  # Épicos, histórias e critérios de aceite
  PRD-Satoshi-Pet-Web-v2.0.md
.env.example                # Template de variáveis de ambiente
.github/workflows/ci.yml    # Pipeline CI/CD
```

## Referências

- [PRD v2.0](docs/PRD-Satoshi-Pet-Web-v2.0.md)
- [Definição técnica](docs/backlog/00-definicao-tecnica.md)
- [ADR-001 — Angular vs React](docs/adr/ADR-001-frontend-angular.md)
- [ADR-002 — Execução containerizada](docs/adr/ADR-002-execucao-containerizada.md)
- [Backlog](docs/backlog/README.md)
- [Critérios de aceite](docs/backlog/11-criterios-de-aceite-e-qualidade.md)
