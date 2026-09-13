# ADR-002 — Execução 100% Containerizada

**Status:** Aceito  
**Data:** 2026-09-12  
**Decisores:** Responsável pelo produto e equipe técnica  
**Contexto:** PRD v2.0 §16.1, `docs/backlog/00-definicao-tecnica.md` §1.3 e §2.4

---

## Contexto

O projeto Satoshi Pet Web é composto por múltiplos serviços com dependências específicas de versão: PWA Angular (Node.js 22.23.2), API Quarkus (Java 25 LTS), PostgreSQL 18.6, object storage S3-compatível, servidor de e-mail de desenvolvimento, nó Bitcoin regtest e proxy reverso HTTPS. Sem uma estratégia de execução definida, a instalação manual dessas dependências no host gera inconsistências entre ambientes de desenvolvimento, staging e produção ("funciona na minha máquina").

## Decisão

**Todos** os componentes do sistema executam em **containers Docker** em todos os ambientes — local, staging e produção. Nenhum serviço é instalado diretamente no host.

### Mapeamento de serviços por container

| Componente | Imagem | Notas |
|------------|--------|-------|
| PWA Angular | Build multi-stage `node:22.23.2` → `nginx:alpine` | Artefato estático; SPA served por nginx |
| API Quarkus | Build multi-stage `maven:3.9-temurin-25` → `eclipse-temurin:25-jre` | JVM JIT; imagem nativa GraalVM como evolução futura |
| PostgreSQL | `postgres:18.6` | Dados em volume nomeado |
| Object storage | `minio/minio` (dev) / S3 gerenciado (prod) | Buckets `pet-artwork` e `pet-artwork-staging` |
| E-mail desenvolvimento | `axllent/mailpit` | Captura SMTP local; UI web na porta 8025 |
| Bitcoin regtest | `bitcoin/bitcoin:29.0` | Rede isolada para testes; sem fundos reais |
| Proxy reverso | `caddy:2-alpine` | TLS automático local; roteamento PWA + API |

### Orquestração

| Ambiente | Orquestrador | Arquivo |
|----------|-------------|---------|
| Local / dev | Docker Compose v2 | `infra/docker-compose.yml` |
| Staging | Containers (Compose ou K8s) | Mesmas imagens do CI |
| Produção | Containers (Compose ou K8s) | Imagens imutáveis versionadas por tag semântica |

## Motivação

1. **Paridade de ambientes** — o que roda em desenvolvimento é estruturalmente idêntico ao que vai para produção.
2. **Onboarding trivial** — um único `docker compose --profile dev up --build` sobe a stack completa sem instalar Node.js, Java, Maven, PostgreSQL ou Bitcoin Core no host.
3. **Builds imutáveis** — imagens versionadas por tag semântica (`X.Y.Z`) ou SHA do commit; `latest` nunca é usada em produção.
4. **Reprodutibilidade de CI** — o CI usa as mesmas imagens e o mesmo processo de build que o desenvolvedor usa localmente.
5. **Rollback seguro e rastreável** — deploy é uma troca de tag de imagem; rollback é referenciar a tag anterior.
6. **Sem "drift" de versão** — versão de Node, Java e demais dependências estão nas imagens, não no sistema operacional do host.

## Consequências positivas

- Colaboradores sem Node.js ou Java instalados conseguem rodar e modificar o projeto.
- Novos serviços (Redis, regtest completo) entram sem alterar o ambiente do host.
- CI usa `docker build` com cache de camadas, resultando em builds incrementais rápidos.
- Snapshot de dados local (volume Docker) é portável entre máquinas via `docker export`.

## Consequências negativas / mitigações

| Consequência | Mitigação |
|-------------|-----------|
| Exige Docker Engine ou Docker Desktop instalado | Requisito mínimo documentado no README; Docker Desktop disponível em Windows, macOS e Linux |
| Rebuild da imagem é mais lento que `npm start` direto | Cache de camadas Docker: após o primeiro build, apenas camadas alteradas são reconstruídas (< 30 s em mudanças de código) |
| Debugging pode exigir `docker exec` ou port-forward | Portas de cada serviço expostas no host em desenvolvimento; documentado com endereços no README |
| TLS local requer confiança no CA do Caddy | Uma vez só: `docker exec satoshi-caddy caddy trust` ou acesso via HTTP durante dev |

## Alternativas descartadas

| Opção | Motivo de descarte |
|-------|-------------------|
| Instalação direta no host (Node/Java) | Inconsistência de versões entre colaboradores e CIs; experiência "funciona na minha máquina" |
| Vagrant / VM por serviço | Overhead significativo; menos integrado com ferramentas modernas de CI/CD |
| Dev containers (VSCode Remote) | Boa opção complementar; não exclui o Docker Compose; pode ser adicionado posteriormente |
| Podman rootless | Compatível com a decisão; mantido como opção futura se houver restrição de segurança no host |
| Nix / Nix flakes | Reprodutibilidade equivalente, mas curva de adoção muito mais alta para a equipe atual |

## Impacto em outros ADRs

- **ADR-001** (Angular 22): a PWA é construída dentro do container de build, eliminando a necessidade de Node.js local para produção.
- CI/CD: pipeline constrói, testa e publica imagens — todo artefato de entrega é uma imagem Docker versionada.

## Referências

- [Definição técnica §1.3 e §2.4](../backlog/00-definicao-tecnica.md)
- [Docker Compose v2 — referência de arquivo](https://docs.docker.com/compose/compose-file/)
- [Caddy 2 — HTTPS automático local](https://caddyserver.com/docs/automatic-https)
- [MinIO Docker](https://min.io/docs/minio/container/index.html)
- [ADR-001 — Frontend Angular](./ADR-001-frontend-angular.md)
