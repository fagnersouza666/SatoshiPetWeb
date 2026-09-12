# Épico 01 — Fundação e Infraestrutura

**Prioridade:** P0  
**Dependências:** nenhuma  
**Referência PRD:** §16, §17, §18, §14.6

---

## Objetivo

Estabelecer monorepo, pipeline, persistência, base do **PWA Angular instalável**, jobs/outbox e controles de segurança mínimos para todos os épicos subsequentes.

---

## Histórias

### FUND-01 — Monorepo e estrutura de projetos

**Descrição:** Criar repositório com `apps/pwa` (Angular 22) e `services/api` (Quarkus 3.33 LTS), scripts de build unificados e README de desenvolvimento.

**Regras de negócio:** §16.1  
**Critérios de aceite:** —  
**Dependências:** —  
**Notas técnicas:** Node ^22.22.3; Java 25; ver [00-definicao-tecnica.md](./00-definicao-tecnica.md) §6. O `.gitignore` da raiz já cobre Angular, Quarkus, Docker, segredos (PRD §17) e dados de regtest — ver §6.1 da definição técnica. Inclui `Dockerfile` de cada artefato e `infra/docker-compose.yml` com a stack completa (decisão §1.3 — tudo containerizado).

---

### FUND-02 — PostgreSQL, Flyway e entidades base

**Descrição:** Configurar PostgreSQL 18.6, Flyway, migrations iniciais para `Account`, `Address`, `AccountAddressBinding`, `Outbox`, `JobLock`.

**Regras de negócio:** §16.2, §16.4  
**Critérios de aceite:** CA-070 (base para restore)  
**Dependências:** FUND-01  
**Notas técnicas:** NUMERIC para valores monetários (CC-10). PostgreSQL executa em container (decisão §1.3 de `00-definicao-tecnica.md`) — serviço no `docker-compose.yml` com volume persistente.

---

### FUND-03 — Object storage para assets

**Descrição:** Integrar storage S3-compatível (MinIO em dev) para sprites; abstração `ObjectStoragePort`.

**Regras de negócio:** §8.2, §16.1  
**Critérios de aceite:** —  
**Dependências:** FUND-01  
**Notas técnicas:** Buckets separados: `pet-artwork`, `pet-artwork-staging`. MinIO executa em container no `docker-compose.yml` (decisão §1.3 de `00-definicao-tecnica.md`).

---

### FUND-04 — Outbox pattern e publicação de eventos

**Descrição:** Implementar outbox transacional + consumidor idempotente para eventos §16.3; travas de concorrência por domínio.

**Regras de negócio:** §16.3, §16.4  
**Critérios de aceite:** CA-033 (base idempotência)  
**Dependências:** FUND-02  
**Notas técnicas:** Mesmos nomes de evento em REST, WS e logs.

---

### FUND-05 — WebSocket server e reconexão

**Descrição:** Endpoint WebSocket Quarkus; protocolo de snapshot + cursor na reconexão; heartbeat.

**Regras de negócio:** §16.1, §18.1  
**Critérios de aceite:** — (meta p95 ≤ 500 ms commit→cliente)  
**Dependências:** FUND-04  
**Notas técnicas:** Canal por endereço (público) e por conta (privado).

---

### FUND-06 — Scheduler com trava persistida

**Descrição:** Jobs agendados com lock DB para evitar execução duplicada em múltiplas instâncias.

**Regras de negócio:** §11, §16.4  
**Critérios de aceite:** CA-058 (base)  
**Dependências:** FUND-02  
**Notas técnicas:** Usar `JobLock` com TTL e renovação.

---

### FUND-07 — HTTPS, CSP, rate limiting e health checks

**Descrição:** TLS em staging/prod; Content-Security-Policy; rate limit por IP/conta; `/q/health` e `/q/metrics`.

**Regras de negócio:** §17.1  
**Critérios de aceite:** —  
**Dependências:** FUND-01  
**Notas técnicas:** CSP compatível com PWA e service worker.

---

### FUND-08 — CI/CD pipeline

**Descrição:** Pipeline build/test/deploy: backend (JUnit + Testcontainers), frontend (unit + lint), build e publicação das imagens Docker de todos os artefatos.

**Regras de negócio:** §18  
**Critérios de aceite:** —  
**Dependências:** FUND-01  
**Notas técnicas:** Artefatos entregues exclusivamente como containers (decisão §1.3 de `00-definicao-tecnica.md`): imagem `pwa` (build estático + nginx) e imagem `api` (JVM), versionadas por tag; deploy sempre a partir das imagens do CI.

---

### FUND-09 — Ambiente regtest Bitcoin

**Descrição:** Docker regtest ou signet para testes de tx, RBF, reorg; fixtures reutilizáveis.

**Regras de negócio:** §16.4, §19.3  
**Critérios de aceite:** CA-028..CA-032 (infra de teste)  
**Dependências:** FUND-01  
**Notas técnicas:** Não usar Mainnet em CI.

---

### FUND-10 — Projeto Angular PWA base

**Descrição:** Scaffold Angular 22 com `@angular/pwa`, `manifest.webmanifest`, ícones, `ngsw-config.json`, roteamento standalone, shell responsivo mobile-first.

**Regras de negócio:** §14.6, §14.1  
**Critérios de aceite:** CA-068 (base instalável)  
**Dependências:** FUND-01, FUND-07  
**Notas técnicas:** **Produto principal** — prioridade igual ao backend.

---

### FUND-11 — Estratégia de cache do service worker

**Descrição:** Configurar `ngsw` para cache de bundle, ícones, ovo padrão; network-first para API; versionamento de assets de pet.

**Regras de negócio:** §14.6  
**Critérios de aceite:** CA-067 (limpar cache privado no logout — base)  
**Dependências:** FUND-10  
**Notas técnicas:** Não misturar sprites de duas criaturas (PRD §14.6).

---

### FUND-12 — Observabilidade base

**Descrição:** Logging estruturado, correlation ID, métricas de latência API/WS, alertas de fila outbox atrasada.

**Regras de negócio:** §18.2  
**Critérios de aceite:** —  
**Dependências:** FUND-04, FUND-07  
**Notas técnicas:** Sem segredos ou prompts em logs.

---

## Definition of Done (épico)

- [ ] PWA instalável em HTTPS local/staging com manifest válido
- [ ] Stack completa sobe com `docker compose up` (PWA, API, PostgreSQL, MinIO, regtest) — tudo em containers
- [ ] API + PostgreSQL + Flyway migrations aplicáveis do zero
- [ ] Outbox publica evento de teste end-to-end via WebSocket
- [ ] CI verde com Testcontainers PostgreSQL e build das imagens Docker
- [ ] Regtest disponível para épico BTC
