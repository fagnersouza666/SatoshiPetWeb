# Correção da auditoria — plano de implementação

> Execução: depuração sistemática, testes de regressão e frentes independentes com `superpowers:dispatching-parallel-agents`. Este arquivo também registra progresso verificável.

**Objetivo:** corrigir todos os bugs de `docs/bug-report.md`, validando também os seis pontos de cobertura apontados no relatório.

**Arquitetura:** preservar portas/adaptadores, domínio persistente, outbox transacional e contratos públicos. Evoluir o banco por novas migrations. Corrigir os fluxos reais e seus consumidores, com evidência por cenário; não substituir exclusão funcional por sucesso falso nem por indisponibilidade permanente.

**Stack:** Angular 22, Java 25, Quarkus 3.33, PostgreSQL 18. **Especificação:** [relatório](../../bug-report.md), PRD v2.0, CC e contratos existentes.

## Restrições e integração

- Valores contábeis exatos, rede de teste, nenhum fundo real, nenhum pet excluído ou reiniciado.
- Preservar aparência aprovada, snapshots e recebimentos lógicos; falha do indexador nunca significa zero.
- Autenticação restaurada por GET sem cache que devolve `X-CSRF-Token`; token somente em memória na PWA.
- Coordenação de migrations: Bitcoin V8, conta V9; migrations adicionais devem usar números posteriores sem colisão.
- Não executar Maven concorrentemente no mesmo `target`; testes da API centralizados pelo agente principal.
- Atualizar contratos imediatamente em cada domínio, relatório ao consolidar; versão única pelo script ao final.
- Checkout atual contém somente documentação da auditoria; trabalho em arquivos por domínio, sem descarte nem operações Git destrutivas. `.git` é somente leitura neste ambiente.
- MCP de grafo indisponível: evidência Verify por fonte e testes; nenhuma alegação de cobertura do grafo.

## Foco da revisão

1. Mais de 25 confirmações durante interrupção e backfill com falha parcial: recuperar sem perda nem tratar falha como fim.
2. RBF/reorg, gastos e dois endereços numa tx: mesma alimentação lógica, saldo líquido, reserva por replay cronológico.
3. Duas abas/requests e jobs simultâneos: consumo único, escrita serializada por pet e respostas antigas sem contaminar identidade nova.
4. Criador sai durante geração/aprovação e saldo fica desconhecido: arte persistente, autorização correta, ovo apenas com evidência válida.
5. Reinício/reconexão e volume prolongado: sessão/CSRF recuperáveis, eventos ordenados, retenção limitada, configuração real de produção.

## Tarefas

Cada tarefa segue: reproduzir com teste → observar falha → corrigir causa → executar regressões → atualizar contrato. Testes bloqueados por ambiente permanecem pendentes de prova, nunca são registrados como verdes.

- [x] **1 — PWA (BUG-011 frontend, 016, 018; suspeitos 1/6).** Arquivos `apps/pwa/src/app/core/{auth,api-client,address-websocket,pet-account,session}.service.ts`, guard/config, componentes conta/endereço/cadastro e specs; `ngsw-config.json`. Bootstrap sem cache, prévia criatura privada, reconexão/cursor, isolamento de identidade, BC1 maiúsculo. Testar reload/401/offline, socket antigo, múltiplos eventos, imagem antes da aprovação. `npm run test:pwa`, `npm run build:pwa`.
- [x] **2 — Conta/API (BUG-011 backend, 012–015, 025, 027; suspeito 4 e retenção rate limit).** Arquivos `account/`, `auth/`, filtros de sessão/CSRF/rate limit e testes; migration V9. Restauração CSRF segura, transações/locks, pet ao trocar, recuperação de uso único e substituição verificada de e-mail, exclusão efetiva de dados privados preservando pet, cookies e IANA. Testar requests independentes, corrida de consumo, recadastro após exclusão, contratos públicos sem dados privados.
- [x] **3 — Implantação (BUG-003/004/021/022/028/029/030).** Compose, Caddy, nginx, envs e docs operacionais. Volume PG18, expansão de domínio, envs consumidas, trust explícito, storage externo e perfis dev. Validar Compose consolidado, contratos automatizados e parsers disponíveis.
- [x] **4 — Arte (BUG-019/020; suspeito 2).** `art/Artwork{Pipeline,Service}.java`, entidade/testes. Reconciliar aprovador ausente, aprovação idempotente e locks consistentes com pet; não sobrescrever arte aprovada nem forçar criatura sem saldo. Testes de estado, repetição e concorrência.
- [x] **5 — Bitcoin/projeção (BUG-002/005–008/017; paginação do suspeito 5).** `btc/` e `pet/PetPublicSnapshot.java`, migration V8 e testes. Separar cabeça/backfill, consulta por endereço+txid, saldo reconciliado persistente, evidência real de reorg/RBF, DTO completo e eventos somente por mudança. Testes no adaptador HTTP e monitor.
- [x] **6 — Reserva e jobs (BUG-009/010/023; suspeitos 3/5).** `pet/engine/`, serviços escritores/tick e `job/`. Replay dos recebimentos elegíveis usando porções congeladas, relógio monotônico, lock comum por pet, desconhecido suspende conclusão de carência; lease sem execução concorrente após expirar. Testes matemáticos, persistência e concorrência.
- [x] **7 — Tempo real/outbox (BUG-024/026).** `realtime/AddressWebSocket.java`, `outbox/OutboxEventDeduplication.java`, consumidores e testes. Ordem de replay, deduplicação com limite e reentrega segura; verificar contrato de cursor e retenção.
- [x] **8 — Consolidação.** Revisão independente, testes PWA/build, testes API/verify quando ambiente permitir, scripts infra, versão pelo script, `git diff --check`, matriz BUG → teste → resultado no relatório. Nenhum item completo por inferência de teste estreito.

## Registro

- 04/10/2026: iniciado em `8f05612`, versão 1.9.1. Baseline da auditoria: PWA 106 testes e build verdes; API integral bloqueada por sockets/attach e erros de classpath, 29 testes matemáticos verdes. Será reavaliada no estado atual.
- Versão incrementada uma vez pelo script: **1.9.2**, API **1.9.2-SNAPSHOT**. Não repetir bump ao retomar este plano.
- PWA: 127 testes/build e contratos de cache passaram após regressões vermelhas. API integrada ainda pendente.
- Infra: regressões Compose vermelhas → 6 verdes, CSP e init-buckets verdes; daemon/parser Caddy/nginx indisponíveis.
- Reserva: três regressões vermelhas reais → `PetEngineTest` 18 e `ReserveClockTest` 6 verdes no lote `/tmp/satoshi-fix-domains-red.log` (demais domínios desse lote ainda vermelhos).
- Testes CDI/H2 podem executar sem listener HTTP. Mockito precisa de agente explícito no POM; o override CLI foi sobreposto pela configuração tardia do Quarkus.
- Revisão independente de conta/autenticação encontrou corrida entre verificação de magic link antigo e recuperação; teste concorrente reproduziu o problema, e a verificação agora usa a mesma trava e transação da recuperação. A PWA recebeu emissão inicial do código na conta, com segredo apenas em memória e limpeza por identidade.
- Revisão independente de pet/tempo real encontrou restauração tardia sem primeiro instante de crédito e reconstrução que perdia o watermark do relógio; ambos foram reproduzidos antes dos ajustes. Nenhum novo defeito material foi encontrado em V10/replay/fanout dentro do escopo lido.
- PWA final: 135 testes, build e Prettier passaram. `check:pwa` confirmou testes/build, mas o smoke instalável não abriu listener: `listen EPERM 127.0.0.1`.
- `check:api` verificou versão 1.9.2 e parou por Docker indisponível. A suíte ampliada sem HTTP continua sendo validada separadamente; não substitui PostgreSQL/regtest/transporte real.
- Script de versionamento testado novamente com sucesso; diff-check sem erros no ponto de revisão.

- Consolidação final: **472 testes da API passaram**, sem falhas, erros ou ignorados na seleção `*Test` sem HTTP/Docker (`/tmp/satoshi-api-wide-final.log`). Inclui as quatro projeções públicas, 25 cenários PetEngine, seis reconstruções, corrida magic-link/recuperação, outbox com lease e reaplicação do diário de exclusão.
- Todas as oito tarefas de implementação e revisão foram concluídas; os gates de integração foram executados até o limite do ambiente e suas pendências estão explícitas no relatório. Não houve commit/push nem implantação.
