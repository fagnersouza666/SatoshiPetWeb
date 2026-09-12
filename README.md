# Satoshi Pet Web

Dashboard pessoal de acumulação de Bitcoin em formato de PWA instalável. O
produto é composto por uma aplicação Angular 22 e uma API Quarkus; os serviços
de desenvolvimento são executados em containers Docker.

## Stack

| Componente | Tecnologia |
| --- | --- |
| PWA | Angular 22 + TypeScript |
| API | Java 25 + Quarkus 3.33 |
| Persistência | PostgreSQL 18.6 (etapa de infraestrutura seguinte) |
| Object storage | MinIO em desenvolvimento (etapa de infraestrutura seguinte) |

As versões de referência estão em `.nvmrc`, `.java-version`,
`apps/pwa/package.json` e `services/api/pom.xml`.

## Desenvolvimento local com Docker

Pré-requisito: Docker Engine ou Docker Desktop com o plugin Docker Compose v2.
O fluxo abaixo não exige Node.js, Java ou Maven instalados no host.

Na raiz do repositório, valide e inicie a composição:

```bash
docker compose -f infra/docker-compose.yml config
docker compose -f infra/docker-compose.yml up --build
```

Com os containers em execução:

| Serviço | Endereço |
| --- | --- |
| PWA | <http://localhost:4200> |
| API | <http://localhost:8080> |

Para interromper os serviços, use `Ctrl+C` ou, em outro terminal:

```bash
docker compose -f infra/docker-compose.yml down
```

As portas podem ser alteradas sem editar arquivos versionados:

```bash
PWA_PORT=4300 API_PORT=8180 \
  docker compose -f infra/docker-compose.yml up --build
```

Nesta composição inicial, `pwa` e `api` são construídos a partir do contexto
da raiz e usam `apps/pwa/Dockerfile` e `services/api/Dockerfile`. PostgreSQL,
MinIO e o ambiente Bitcoin regtest serão acoplados por suas respectivas
entregas do backlog. Nenhum serviço local deve ser substituído por Mainnet ou
por envio de fundos reais durante os testes.

## Desenvolvimento sem a composição

Os scripts da raiz continuam sendo a entrada padronizada para as verificações
do workspace. Para desenvolvimento direto, inicie cada serviço em um terminal
separado, sempre a partir da raiz:

```bash
# terminal 1
npm run start:pwa
```

```bash
# terminal 2
npm run start:api
```

Para as verificações:

```bash
npm run build
npm run test
npm run verify
```

O script `scripts/with-node.sh` seleciona a versão de Node.js declarada para a
PWA, e a API usa o Maven Wrapper versionado em `services/api/mvnw`. Consulte
[`docs/README.md`](docs/README.md) para a lista completa de comandos e
[`docs/backlog/00-definicao-tecnica.md`](docs/backlog/00-definicao-tecnica.md)
para as decisões de arquitetura.

## Segurança e dados locais

- Não versionar `.env`, credenciais, chaves privadas, seeds ou `wallet.dat`.
- Não solicitar nem armazenar seed, chave privada, xprv ou senha de carteira.
- Dados de containers e Bitcoin de teste ficam fora do Git conforme o
  [`.gitignore`](.gitignore).
- Alterações financeiras ficam indisponíveis offline; use regtest ou testnet
  para validar integrações Bitcoin.

## Estrutura

```text
apps/pwa/       # PWA Angular
services/api/   # API Quarkus
infra/          # Compose e scripts operacionais
docs/           # PRD, backlog, contratos e critérios de aceite
```
