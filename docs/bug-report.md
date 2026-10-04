# Bug Report — Satoshi Pet Web

> Baseline da auditoria: 04/10/2026 | Versão: 1.9.1 | Revisão: `8f05612`
> Correções em andamento: 1.9.2 (API 1.9.2-SNAPSHOT), ainda sem commit. Ver plano e validação abaixo.
> Stack: Angular 22.1 / TypeScript 6 / Java 25 / Quarkus 3.33.3.2 / PostgreSQL 18 / Docker
> Modo: **full** — PWA, API, persistência, eventos, jobs, integrações e infraestrutura.
> Arquivos analisados por varredura: **322**; inventário: **426 arquivos versionados**. Leitura contextual dos fluxos detalhada abaixo.

## Sumário da auditoria inicial

| Severidade | Quantidade |
|------------|------------|
| CRÍTICO | 2 |
| ALTO | 18 |
| MÉDIO | 9 |
| BAIXO | 0 |
| **Total identificado no baseline** | **29** |

**Veredicto: BLOQUEADO.** Há defeitos na configuração de implantação, no acompanhamento Bitcoin, na reserva do pet e na jornada de autenticação/arte. O veredicto descreve esta revisão do código e das configurações, não uma tentativa de implantação em produção.

Os trechos, localizações e diagnósticos abaixo preservam a auditoria inicial. As correções posteriores estão sendo aplicadas no código, com regressões e migrations V8–V12. O veredicto acima descreve o baseline, não o resultado ainda pendente da rodada de correção. Os IDs anteriores foram preservados; BUG-001 permanece no histórico.

## Acompanhamento das correções

[Plano de execução e evidências](superpowers/plans/2026-10-04-correcao-auditoria.md). A versão foi incrementada uma única vez pelo script para **1.9.2**. A consolidação por ocorrência será concluída após os testes e a revisão independente; nenhum teste bloqueado pelo ambiente será registrado como aprovado.

Já foram reproduzidos testes vermelhos para cursor, saldo público, contas, replay da reserva, concorrência do pet, replay WebSocket e contratos reais do Esplora. A implementação inclui isolamento de identidade na PWA, recuperação com verificação de novo e-mail, exclusão efetiva dos dados privados, snapshot de saldo líquido, vínculo lógico por entradas Bitcoin, projeção durável de eventos e execução de jobs protegida por transação.

Os testes iniciais da PWA (127), os seis contratos de implantação e os primeiros lotes de domínio passaram; uma nova rodada integrada verifica as mudanças adicionais. A validação de PostgreSQL/regtest, transporte HTTP/WebSocket real e navegador com service worker continua sujeita à disponibilidade do ambiente.


## Método, cobertura e limites

- Executadas as dez categorias SCAN-01..SCAN-10 da skill `bug-detector`, adaptadas a Java, TypeScript, SQL, scripts e configurações. Os matches foram usados como pistas, não como diagnóstico automático. Os números incluem testes: null/acesso 2.082; erros 85; concorrência 663; recursos 46; consultas/loops 84; lógica 288; persistência 366; configuração 27; frontend 44; APIs 191.
- Leitura contextual de conta/autenticação/filtros, monitor Esplora, motor/porções/apresentação do pet, geração/aprovação de arte, outbox/WebSocket, storage, migrations V1–V7, fontes da PWA, Compose/Caddy/nginx, scripts e testes relacionados. PRD, CC, critérios e contratos foram cruzados com o comportamento implementado.
- Ferramentas MCP de `codebase-memory` não estavam disponíveis no catálogo da sessão. Projeto/geração do grafo e `check_index_coverage` não puderam ser consultados. A evidência vem de arquivos e buscas diretas; não há alegação de cobertura estrutural exaustiva.
- Arquivos gerados, dependências vendorizadas, binários/imagens e todos os estilos não foram revisados linha a linha. A varredura de todo o projeto não demonstra ausência de outros defeitos. Não foi feita auditoria de CVEs nem atualização de dependências.
- Funcionalidades ainda explicitamente planejadas (DCA completo, clima, IA real, administração) não foram enumeradas como bugs apenas por estarem ausentes. Endpoints já expostos que afirmam executar uma operação, mas não a executam, foram incluídos.
- Confirmação estática significa que o encadeamento e o cenário estão demonstrados no código. Somente os casos indicados como reproduzidos tiveram execução isolada; isso não equivale a validação end-to-end.

### Verificações executadas

| Verificação | Resultado observado |
|-------------|---------------------|
| `npm run test:pwa` | **106 testes passaram**, 19 arquivos |
| `npm run build:pwa` | Build de produção concluído |
| `node infra/scripts/versao.mjs verificar` | Versões coerentes em 1.9.1 |
| `sh infra/minio/init-buckets.test.sh` | Passou |
| `node --test infra/scripts/*.test.mjs` | 7 arquivos passaram; `ci-gates.test.mjs` falhou |
| Diagnóstico isolado de `ci-gates.test.mjs` | `spawnSync` do subprocesso de cenário devolveu **EPERM**, stderr vazio; não contado como bug do gate |
| `npm run test:api -- --offline` | Compilou; resultado da execução: 443 testes, 1 falha, 55 erros, 237 ignorados. Bootstrap HTTP bloqueado por sockets; Mockito sem attach; também houve erros de recursos no classpath, ainda sem causa isolada |
| `node scripts/mvnw.mjs --offline -Dtest=ReserveMathTest,ReserveClockTest,EggPolicyTest,EmotionalStatePolicyTest test` | **29 testes passaram**, nenhum ignorado |
| Harness com classes reais da PWA | Reproduziu perda de sessão, preview do ovo e descarte de eventos/ausência de reconexão |
| Probe com `ReserveMath`/`ReserveClock` reais | Reproduziu desconto repetido do relógio e comparou subtração com replay após invalidar alimentação |
| `docker compose ... config --format json` com valores fictícios | Configuração consolidada inspecionada; não inicia serviços |
| Docker / testes de integração | Socket do Docker sem permissão; nenhuma validação de containers, PostgreSQL real, SMTP externo ou Bitcoin real |

Os 55 erros da suíte Java não são 55 bugs confirmados. Os artefatos temporários de investigação ficaram em `/tmp`; a evidência necessária e os cenários estão descritos neste documento. Não houve envio de fundos nem acesso a contas reais.

## CRÍTICO

### BUG-003: Volume do PostgreSQL 18 montado no caminho antigo

**Arquivo:** `infra/docker-compose.yml` — **linhas 97, 105–106**. Produção herda a montagem.

**Código problemático:**
```yaml
image: postgres:18.6
volumes:
  - postgres-data:/var/lib/postgresql/data
```

**O que acontece e impacto:** o layout da imagem 18 usa diretório versionado sob `/var/lib/postgresql`. O entrypoint detecta a montagem antiga e encerra a inicialização, inclusive quando ela está vazia. O PostgreSQL não fica saudável e a API dependente não inicia. Confirmado contra o [entrypoint oficial](https://raw.githubusercontent.com/docker-library/postgres/master/docker-entrypoint.sh) e o [Dockerfile da imagem 18](https://raw.githubusercontent.com/docker-library/postgres/master/18/bookworm/Dockerfile); não executado no daemon desta sessão.

**Solução:**
```yaml
volumes:
  - postgres-data:/var/lib/postgresql
```
A correção alinha a montagem ao layout da imagem. Para um volume já populado, planejar a migração conforme sua estrutura; alterar o caminho não migra os arquivos existentes. **Validar:** stack com volume novo e restauração controlada de volume existente.

### BUG-004: Endereço do site Caddy usa placeholder de runtime

**Arquivo:** `infra/caddy/Caddyfile.prod` — **linhas 20 e 79**.

**Código problemático:**
```caddy
{env.DOMAIN} {
```
```caddy
http://{env.DOMAIN} {
```

**O que acontece e impacto:** o domínio não é interpolado no endereço do site. Isso impede a configuração correta do host e do TLS da implantação. A [documentação do Caddy sobre endereços](https://caddyserver.com/docs/caddyfile/concepts#addresses) distingue placeholders de runtime da substituição de ambiente feita antes do parsing.

**Solução:** usar `{$DOMAIN}` nos dois endereços, preservando seus handlers; usar a mesma substituição nas opções globais que precisam dela.
```caddy
{$DOMAIN} {
    reverse_proxy pwa:80
}
http://{$DOMAIN} {
    redir https://{$DOMAIN}{uri} permanent
}
```
O trecho ilustra a sintaxe de endereço corrigida; manter no arquivo real os handlers de API, health e headers já existentes. **Validar:** `caddy adapt --validate` com `DOMAIN` de staging, seguido de prova TLS nesse ambiente.

## ALTO

### BUG-005: Cursor de backfill impede descobrir novas confirmações

**Arquivos:** `services/api/src/main/java/br/com/satoshipet/api/btc/BitcoinMonitorService.java` — **127–143**; `services/api/src/main/java/br/com/satoshipet/api/btc/EsploraBitcoinIndexer.java` — **55–63**.

**Código problemático:**
```java
indexer.getTransactions(address.canonical, state.lastSeenTxid, TX_PAGE_SIZE);
```
```java
if (!chainTxs.isEmpty() && txInfo.equals(chainTxs.get(chainTxs.size() - 1))) {
    newLastSeen = txInfo.txid();
}
state.advance(newLastSeen, newLastSeen, now);
```

**O que acontece:** Esplora devolve histórico do mais recente para o mais antigo; o cursor solicita páginas ainda mais antigas. Depois do primeiro poll, o monitor nunca volta ao início. Quando chega à última transação, permanece consultando depois dela. **Cenário:** primeiro poll conhece T1; T2 confirma depois; os próximos polls continuam depois de T1 e nunca observam T2. Uma pendente que confirme também pode ficar pendente indefinidamente. Fonte: [contrato oficial Esplora, histórico por endereço](https://github.com/Blockstream/esplora/blob/master/API.md#addresses).

**Solução:** separar cursor histórico de busca recente. Núcleo mínimo compatível com a porta atual:
```java
List<BitcoinIndexerPort.TransactionInfo> recent =
        indexer.getTransactions(address.canonical, null, TX_PAGE_SIZE);
for (var tx : recent) processTransaction(address, tx, now);
List<BitcoinIndexerPort.TransactionInfo> historical =
        indexer.getTransactions(address.canonical, state.lastSeenTxid, TX_PAGE_SIZE);
for (var tx : historical) processTransaction(address, tx, now);
if (!historical.isEmpty()) {
    String last = historical.getLast().txid();
    state.advance(last, last, now);
}
```
Para recuperação completa, paginar também a busca recente até reencontrar o marco confirmado anterior, sem limitar a recuperação a 25 transações. Diferenciar falha de página vazia antes de marcar o backfill concluído. **Validar:** T2 entra depois de T1; confirmação fora da mempool; mais de 25 transações durante interrupção. PRD §9.4.

### BUG-006: Uma transação pagando dois endereços só é aplicada ao primeiro

**Arquivos:** `services/api/src/main/java/br/com/satoshipet/api/btc/BitcoinMonitorService.java` — **162–168**; `services/api/src/main/java/br/com/satoshipet/api/btc/BitcoinTransaction.java` — **47–53**; `services/api/src/main/resources/db/migration/V3__create_bitcoin_monitor.sql` — **22**.

**Código problemático:**
```java
Optional<BitcoinTransaction> existing = BitcoinTransaction.findByTxid(txInfo.txid());
```
```java
@Column(name = "txid", nullable = false, length = 64, unique = true, updatable = false)
public String txid;
```

**Impacto:** o registro contém um único `address`, mas a deduplicação é global por txid. Pagamento com outputs para A e B cria recebimento/alimentação para A; ao monitorar B, encontra o registro de A e retorna se o status é igual. B não recebe seu histórico nem energia. PRD §9.3 exige recebimento por endereço.

**Solução mínima:** modelar observação por `(address, txid)`, ou separar transação global dos recebimentos por endereço. Para a primeira opção, criar migration nova (não editar V3 aplicada):
```sql
ALTER TABLE bitcoin_transactions DROP CONSTRAINT uq_bitcoin_transactions_txid;
ALTER TABLE bitcoin_transactions ADD CONSTRAINT uq_bitcoin_transactions_address_txid
    UNIQUE (address_id, txid);
```
```java
Optional<BitcoinTransaction> existing = BitcoinTransaction.find(
        "address = ?1 AND txid = ?2", address, txInfo.txid()).firstResultOptional();
```
Remover `unique=true` da anotação e adaptar `handleReorg` para atualizar todas as observações do txid. **Validar:** uma transação com valores distintos em A/B cria exatamente um recebimento por endereço, sem duplicar no repoll.

### BUG-007: Total recebido é apresentado e reutilizado como saldo atual

**Arquivos:** `services/api/src/main/java/br/com/satoshipet/api/btc/PublicAddressResource.java` — **102–105**; `services/api/src/main/java/br/com/satoshipet/api/pet/engine/PetEngine.java` — **292–300**; `services/api/src/main/java/br/com/satoshipet/api/btc/BitcoinMonitorService.java` — **345–354**.

**Código problemático:**
```java
long confirmedSats = receipts.stream().mapToLong(r -> r.confirmedSats).sum();
```

**Cenário e impacto:** endereço recebe 100.000 sats e depois gasta tudo. Os recebimentos continuam confirmados; a página mostra 100.000, embora o saldo seja zero. A mesma soma é usada na reconstrução/reorg do pet e pode encerrar a carência ou permitir nascimento indevidamente. `BitcoinSpend` existe, mas o monitor não persiste gastos. A consulta Esplora calcula saldo líquido corretamente, porém esse snapshot não abastece essas leituras. PRD §9.1 distingue explicitamente saldo e total recebido.

**Solução:** persistir o snapshot reconciliado em `AddressMonitorState` e consumi-lo nos DTOs/políticas, com estado de validade e instante da consulta. Campos a adicionar por migration e mapear na entidade:
```sql
ALTER TABLE address_monitor_state ADD COLUMN confirmed_balance_sats BIGINT;
ALTER TABLE address_monitor_state ADD COLUMN balance_checked_at TIMESTAMPTZ;
```
```java
if (balance.state() == BitcoinIndexerPort.BalanceState.CONFIRMED) {
    state.confirmedBalanceSats = balance.confirmedSats();
    state.balanceCheckedAt = now;
}
```
Nas leituras, usar esse saldo; se não conhecido, responder estado desconhecido/desatualizado, nunca substituir por zero nem por soma de recebimentos. Para replay de saldo histórico, persistir/reconciliar também os gastos. **Validar:** gasto parcial, gasto total, entrada/saída pendente e falha de provedor após gasto.

### BUG-008: RBF, descarte e reorg testados não são reconciliados pelo adaptador real

**Arquivos:** `services/api/src/main/java/br/com/satoshipet/api/btc/EsploraBitcoinIndexer.java` — **71–90**; `services/api/src/main/java/br/com/satoshipet/api/btc/BitcoinMonitorService.java` — **124–149, 232–280, 296–327**.

**Código problemático:**
```java
BitcoinTransaction.Status status = confirmed
        ? BitcoinTransaction.Status.CONFIRMED
        : BitcoinTransaction.Status.PENDING;
```

**O que acontece:** o Esplora só produz `PENDING`/`CONFIRMED`; uma substituída some da listagem, sem voltar como `REPLACED`. O poll não reconcilia as pendentes já armazenadas nem compara inputs. Assim o substituto vira outro recebimento e a alimentação antiga permanece. O handler `handleReorg` só é chamado pelos testes no código revisado; uma confirmada que reaparece como pendente cai no `default` do switch. Os testes injetam `REPLACED` artificialmente ou chamam o handler diretamente.

**Solução concreta de integração:** adicionar ao cliente consultas de status/outspend e guardar inputs para comprovar conflito; não inferir descarte de ausência na lista. Endpoints disponíveis no [contrato oficial Esplora](https://github.com/Blockstream/esplora/blob/master/API.md#transactions):
```java
@GET
@Path("/tx/{txid}/status")
EsploraTxStatus getTransactionStatus(@PathParam("txid") String txid);

@GET
@Path("/tx/{txid}/outspend/{vout}")
OutspendStatus getOutspend(@PathParam("txid") String txid,
                         @PathParam("vout") int vout);

record OutspendStatus(boolean spent, String txid, Integer vin, EsploraTxStatus status) {}
```
Integrar essa evidência no poll: reavaliar pendentes e confirmações recentes, chamar o handler de reorg com evidência de bloco e manter o mesmo recebimento lógico em RBF equivalente. A porta precisa transportar essa evidência; só mudar o switch não resolve. **Validar:** fake HTTP com respostas reais de Esplora para RBF/removal/reorg, em vez de depender apenas do stub de domínio. PRD §9.3, CA-029..031.

### BUG-009: Invalidar alimentação subtrai o crédito original sem replay

**Arquivo:** `services/api/src/main/java/br/com/satoshipet/api/pet/engine/PetEngine.java` — **444–455**; efeito semelhante na revisão em **434–439**.

**Código problemático:**
```java
evaluate(pet, when);
creditDelta(pet, creditedHours(pet, feeding).negate(), when);
feeding.status = FeedingStatus.INVALIDATED;
```

**Cenário:** A credita 168h; B recebe uma porção de 24h no mesmo instante e fica com crédito aplicado zero pelo teto. Invalidar A subtrai 168h e deixa zero. Reexecutando apenas B, a reserva correta é 24h. A comparação foi reproduzida com `ReserveMath` real: **subtração = 0; replay = 24**. O teste existente invalida B (o excesso), mas não A. PRD §9.3 exige reconstrução cronológica, não simples subtração.

**Solução:** marcar a invalidação e recompor a projeção de reserva a partir de recebimentos elegíveis em ordem, recalculando o teto a cada instante com `amountSats` e a porção congelada. Não usar `durationHours` já capado como único dado de replay. Núcleo de recomposição, para a lista previamente filtrada por elegibilidade:
```java
BigDecimal reserve = BigDecimal.ZERO;
Instant last = credits.isEmpty() ? now : credits.getFirst().effectiveAt();
for (ReplayCredit credit : credits) {
    reserve = ReserveClock.consume(reserve, last, null, credit.effectiveAt()).remainingHours();
    reserve = ReserveMath.applyCap(reserve,
            ReserveMath.hoursAdded(credit.amountSats(), credit.portionSats()));
    last = credit.effectiveAt();
}
ReserveClock.Consumption result = ReserveClock.consume(reserve, last, null, now);
```
`ReplayCredit` deve conter `effectiveAt`, `amountSats`, `portionSats`; preservar snapshots originais e publicar correção separada. Reconstruir também `reserveDepletedAt` quando aplicável. **Validar:** invalidar A e B separadamente, consumo entre eventos e revisão que reduz uma alimentação anterior ao teto.

### BUG-010: Jobs diferentes atualizam o mesmo pet sem trava de entidade

**Arquivo:** `services/api/src/main/java/br/com/satoshipet/api/pet/engine/PetEngine.java` — **747–753**. Relações: `btc/BitcoinMonitorJob.java`, `pet/PetTickJob.java` e entidade `pet/Pet.java`.

**Código problemático:**
```java
Pet pet = Pet.findById(petId);
```

**Impacto:** as travas dos jobs são `bitcoin-monitor` e `pet-tick`, portanto não se excluem entre si. A entidade não usa `@Version` e a leitura não bloqueia. Tick lê reserva 10; recebimento lê 10 e grava 34; tick confirma sua cópia com 9: a refeição pode ser perdida. Aprovação/porção também escrevem campos do mesmo pet. Confirmação estática; a disputa não foi executada contra PostgreSQL nesta sessão.

**Solução:** adquirir trava de escrita da linha dentro da transação antes de ler/modificar o estado, compartilhando o protocolo em todos os escritores; recarregar entidades já presentes no contexto quando necessário.
```java
Pet pet = Pet.findById(petId, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
```
Aplicar a mesma ordem de locks nos serviços de porção/arte; alternativamente usar versão otimista com retry idempotente. **Validar:** duas transações concorrentes, uma de tick e outra de recebimento, preservando energia e eventos.

### BUG-011: Reabrir a PWA exige novo login apesar do cookie válido

**Arquivos:** `apps/pwa/src/app/core/session.service.ts` — **12**; `apps/pwa/src/app/core/guards/auth.guard.ts` — **15–19**; `apps/pwa/src/app/app.config.ts` — **8–16**; `apps/pwa/src/app/core/api-client.service.ts` — **12**.

**Código problemático:**
```typescript
private readonly _account = signal<AccountInfo | null>(null);
```
```typescript
if (session.isAuthenticated()) return true;
return router.createUrlTree(['/entrar']);
```

**Cenário reproduzido:** autenticar e recarregar `/conta`, ou reabrir a PWA. A nova instância começa sem identidade, não consulta a API e redireciona ao login. O cookie persiste, mas não é utilizado para restaurar o estado. Há uma segunda dependência: o CSRF só existe em memória; `/account/me` não devolve CSRF, e o banco só guarda seu hash. Restaurar apenas a identidade deixa logout/aprovação em 403.

**Solução:** criar bootstrap autenticado sem cache que recupere identidade **e CSRF**, e aguardar a operação no guard/initializer. Núcleo do frontend:
```typescript
async restoreSession(): Promise<void> {
  try {
    const data = await firstValueFrom(
      this.api.getFresh<AccountApiResponse>('/v1/account/me'));
    this.session.setSession({
      id: data.id, email: data.email,
      address: data.bitcoinAddress, petName: data.petName,
    });
  } catch (error) {
    if (!(error instanceof HttpErrorResponse) || error.status !== 401) throw error;
    this.api.clearCsrfToken();
    this.session.clearSession();
  }
}
```
Registrar `provideAppInitializer(() => inject(AuthService).restoreSession())`. O servidor precisa permitir recuperação segura do CSRF (por exemplo, derivação estável do segredo de sessão com separação de domínio, mantendo no banco só o hash verificador), com migração/revogação das sessões antigas. Não devolver o hash persistido como se fosse o token bruto. **Validar:** reload, nova aba, cookie expirado, logout e aprovação depois do bootstrap; definir comportamento offline sem aceitar identidade cacheada como autorização.

### BUG-012: Renomeação do pet responde 200 sem persistir

**Arquivo:** `services/api/src/main/java/br/com/satoshipet/api/account/AccountResource.java` — **115–151**, escrita em **148–149**.

**Código problemático:**
```java
pet.name = trimmed;
pet.updatedAt = Instant.now();
return Response.ok(new PetNameResponse("ok", pet.name)).build();
```

**Impacto:** método, classe e filtros não abrem transação para a alteração. A resposta mostra o novo nome em memória, mas a próxima requisição volta ao nome anterior. CA-008.

**Solução:** adicionar `@jakarta.transaction.Transactional` ao método `renamePet`, mantendo autorização e consulta no mesmo limite transacional. Alternativamente extrair o corpo para serviço transacional. **Validar:** PATCH HTTP seguido de GET em outro request/transação; uma transação externa no teste mascara o defeito.

### BUG-013: Trocar para endereço novo deixa a conta sem pet

**Arquivo:** `services/api/src/main/java/br/com/satoshipet/api/account/AccountAddressChangeService.java` — **86–102**.

**Código problemático:**
```java
priorBinding.ifPresentOrElse(
        existing -> existing.rebindAsPrimary(now),
        () -> AccountAddressBinding.create(account, destination, true, now).persist()
);
```

**Cenário:** dentro das 72h, trocar A por B ainda desconhecido. O serviço cria `Address` e binding, mas não cria `Pet`. A API de pet retorna 404; o monitor apenas notifica pets já existentes (`BitcoinMonitorService.java:341–342`). A criação encontrada está no cadastro, não na troca. PRD §6.1 e CONTA-09.

**Solução:** na mesma transação, criar/reutilizar o pet do destino; validar o nome antes de desfazer o vínculo atual. Estender a requisição para receber nome quando necessário:
```java
Pet.findByAddress(destination).orElseGet(() -> {
    Pet created = Pet.create(destination, account, validatedDestinationPetName, now);
    created.persist();
    return created;
});
```
`validatedDestinationPetName` vem do novo campo validado (1–100 caracteres); não renomear um pet já existente. Serializar a criação por endereço para concorrência. **Validar:** B novo, B com pet existente e retorno a A.

### BUG-014: Código de recuperação admite consumo concorrente

**Arquivos:** `services/api/src/main/java/br/com/satoshipet/api/account/RecoveryService.java` — **78–94**; `services/api/src/main/java/br/com/satoshipet/api/account/RecoveryCode.java` — **67–81**.

**Código problemático:**
```java
Optional<RecoveryCode> candidate = RecoveryCode.findByCodeHash(codeHash);
if (candidate.isEmpty() || !candidate.get().isUsable()) {
    throw new RecoveryException("invalid_code", "Código de recuperação inválido ou já usado.");
}
RecoveryCode recoveryCode = candidate.get();
recoveryCode.markUsed(now);
```

**Impacto:** duas transações podem ler `used_at=NULL` e ambas chegar à criação de sessão. A entidade não tem versão, lock ou consumo condicional. A transação individual não torna o par SELECT/UPDATE exclusivo. O magic link já usa corretamente uma atualização condicional; a recuperação não.

**Solução:** antes de revogar/criar sessões, consumir atomicamente dentro da transação:
```java
long consumed = RecoveryCode.update(
        "usedAt = ?1 WHERE id = ?2 AND usedAt IS NULL", now, recoveryCode.id);
if (consumed != 1) {
    throw new RecoveryException("invalid_code", "Código de recuperação inválido ou já usado.");
}
recoveryCode.markUsed(now);
```
**Validar:** duas requisições concorrentes com o mesmo código, exatamente uma bem-sucedida. A corrida foi confirmada estruturalmente, sem execução concorrente nesta sessão.

### BUG-015: Exclusão confirma sucesso mantendo conta e dados privados

**Arquivos:** `services/api/src/main/java/br/com/satoshipet/api/account/AccountResource.java` — **216–225**; `services/api/src/main/java/br/com/satoshipet/api/isolation/LoggingAccountPrivateDataWipePort.java` — **21–24**.

**Código problemático:**
```java
sessionService.revokeAll(account.id, Instant.now());
wipePort.wipe(account.id);
LOG.infof("Conta apagada (wipe): account=%s", account.id);
```
O único adaptador de wipe encontrado apenas registra log, explicitamente sem ação real; o endpoint devolve 204.

**Impacto:** e-mail, vínculos, códigos e histórico permanecem. Um novo magic link encontra a mesma conta e permite retornar. O problema é a confirmação falsa numa operação exposta, embora a limpeza esteja planejada. Requisitos: CC-22, PRD §17.2 e CONTA-14.

**Solução imediata implementável:** enquanto não existir exclusão efetiva, substituir a operação por falha explícita, antes de revogar sessões, evitando informar apagamento inexistente:
```java
return Response.status(Response.Status.SERVICE_UNAVAILABLE)
        .entity(new ErrorBody("deletion_unavailable",
                "A exclusão da conta ainda não está disponível."))
        .build();
```
**Correção definitiva:** separar exclusão de conta da limpeza usada na troca de endereço; migration que desassocie referências pessoais do pet preservado; exclusão transacional dos dados privados e magic links, com tombstone reaplicável ao restaurar backup. `Account.deleteById` sozinho falha nas FKs `RESTRICT` do criador/fonte alimentar. Retornar sucesso somente após conclusão real ou enfileiramento persistente com contrato explícito. **Validar:** dados removidos, pets preservados e link/código antigo incapaz de restaurar a conta excluída.

### BUG-016: Prévia privada exibe ovo no lugar da arte a aprovar

**Arquivos:** `apps/pwa/src/app/features/conta/pet-artwork-panel.component.ts` — **28–32**; `apps/pwa/src/app/shared/pet-sprite/pet-sprite.component.ts` — **32–40, 129–134**.

**Código problemático:**
```html
<app-pet-sprite
  [presentation]="snap.presentation"
  [petState]="snap.petState"
  [atlasUrl]="previewAtlasUrl(snap)"
  [petName]="snap.petName"
/>
```
```typescript
if (this.presentation() !== 'CREATURE' || !url) {
  this.atlasImage = null;
  return;
}
```

**Cenário reproduzido:** primeira arte está `AWAITING_APPROVAL`, com URL privada válida, enquanto apresentação pública ainda é EGG. O componente nem instancia `Image`; oferece aprovação de aparência permanente mostrando apenas o ovo. O fixture do teste contém esse estado, mas verifica apenas os botões.

**Solução:** renderizar a prévia privada como criatura sem mudar a apresentação compartilhada:
```html
@if (snap.artwork?.generationStatus === 'AWAITING_APPROVAL') {
  <app-pet-sprite presentation="CREATURE"
    [atlasUrl]="previewAtlasUrl(snap)" [petName]="snap.petName" />
} @else {
  <app-pet-sprite [presentation]="snap.presentation"
    [atlasUrl]="snap.atlasUrl" [petState]="snap.petState" [petName]="snap.petName" />
}
```
**Validar:** carregar/desenhar a prévia antes de aprovar; vista pública continua com ovo; erro no asset não apresenta aprovação como se a imagem tivesse carregado.

### BUG-017: GET público omite a URL da arte aprovada

**Arquivos:** `services/api/src/main/java/br/com/satoshipet/api/btc/PublicAddressResource.java` — **120–122**; `services/api/src/main/java/br/com/satoshipet/api/pet/PetPublicSnapshot.java` — **39–54, 96–105**; `apps/pwa/src/app/features/endereco/endereco.component.ts` — **285–289**.

**Código problemático:**
```java
PetPublicSnapshot petSnapshot = Pet.findByAddress(managed)
        .map(pet -> PetPublicSnapshot.from(pet, pendingSats))
        .orElseGet(PetPublicSnapshot::empty);
```

`from()` sempre coloca `artworkVersion` e `atlasUrl` como null; o enriquecimento de arte aprovada só ocorre em `fromAddress()`. **Impacto:** abrir/recarregar página pública de pet aprovado devolve URL ausente e o sprite cai no ovo. Até o callback de `PET_ARTWORK_READY` refaz esse GET e volta a obter null; não usa a URL do evento.

**Solução:**
```java
PetPublicSnapshot petSnapshot = PetPublicSnapshot.fromAddress(managed);
```
Isso reutiliza a projeção que verifica aprovação e preenche a URL versionada. **Validar:** GET de pet aprovado contém URL/versão; não aprovado continua sem arte pública; página carrega atlas após reload.

### BUG-018: Página aberta ignora mudanças do pet e não reconecta WebSocket

**Arquivo:** `apps/pwa/src/app/core/address-websocket.service.ts` — **37–41, 78–85**.

**Código problemático:**
```typescript
this.socket.addEventListener('close', () => {
  if (this.currentAddress === address) this.socket = null;
});
```
```typescript
if (payload['eventType'] !== 'PET_ARTWORK_READY') return;
```

**Cenários reproduzidos:** `SNAPSHOT` e `PET_STATE_CHANGED` não chegam ao callback; retorno ao ovo também é descartado. Depois de `close`, nenhuma nova conexão é criada. O usuário mantém estado antigo indefinidamente após alteração ou queda da rede. O cursor é declarado no envelope, mas não usado. PRD §16.3 e contrato WebSocket.

**Solução:** generalizar callback para atualização de endereço, tratar SNAPSHOT e eventos públicos, e guardar o cursor. Exemplo do envio de replay e reconexão, com identidade do socket:
```typescript
const socket = new WebSocket(this.buildWsUrl(address));
this.socket = socket;
socket.addEventListener('open', () => {
  if (this.socket !== socket) return;
  socket.send(JSON.stringify({ type: 'RECONNECT', cursor: this.lastCursor ?? '0' }));
});
socket.addEventListener('close', () => {
  if (this.socket !== socket || this.currentAddress !== address) return;
  this.socket = null;
  this.retryTimer = window.setTimeout(() => this.openSocket(address), 1000);
});
```
Adicionar os campos `lastCursor`, `retryTimer` e o método `openSocket`; `disconnect()` deve cancelar timer e limpar identidade antes de fechar. Usar backoff limitado. Processar eventos em ordem e não sobrescrever o cursor anterior com o snapshot antes do replay. **Validar:** mudança de estado sem recarregar, queda/retorno de rede, troca de endereço e destruição do componente.

### BUG-019: Saída do criador deixa arte pendente sem aprovador

**Arquivos:** `services/api/src/main/java/br/com/satoshipet/api/art/ArtworkPipeline.java` — **99–104, 153–156**; `services/api/src/main/java/br/com/satoshipet/api/art/ArtworkService.java` — **117–126**.

**Código problemático:**
```java
"generationStatus = ?1 OR (generationStatus = ?2 AND nextRetryAt <= ?3)",
ArtGenerationStatus.GENERATING,
ArtGenerationStatus.RETRY_WAIT,
```

**Cenário:** geração válida termina; arte fica `AWAITING_APPROVAL`; criador troca de endereço. A aprovação automática só foi avaliada durante a geração. O job não revisita esse estado, e as outras contas não têm autorização para aprovar. A arte fica bloqueada permanentemente. ART-07/CC-13.

**Solução:** reconciliar pendências sem criador ativo, preservando a última geração válida:
```java
List<PetArtwork> awaiting = PetArtwork.list(
        "generationStatus", ArtGenerationStatus.AWAITING_APPROVAL);
for (PetArtwork artwork : awaiting) {
    if (shouldAutoApprove(artwork.pet)) approve(artwork, now);
}
```
Antes de decidir, bloquear/recarregar a linha da arte e revalidar vínculo/status; alternativamente disparar a reconciliação no desligamento. **Validar:** criador sai depois da geração, com outra conta acompanhando; não gerar novamente nem exigir autorização da outra conta.

### BUG-021: E-mails de produção levam o usuário para localhost

**Arquivos:** `services/api/src/main/resources/application.properties` — **91**; `services/api/src/main/java/br/com/satoshipet/api/auth/MagicLinkResource.java` — **94**; `infra/docker-compose.prod.yml` — **48–65**.

**Código problemático:**
```properties
satoshi-pet.magic-link.base-url=${MAGIC_LINK_BASE_URL:http://localhost:4200}
```

**Impacto:** nenhum Compose passa `MAGIC_LINK_BASE_URL` ao container. Defini-la apenas no `.env` do Compose não a injeta automaticamente. SMTP de produção envia URL do localhost do destinatário, impedindo login. Confirmado na configuração consolidada, sem enviar e-mail.

**Solução em `api.environment` de produção:**
```yaml
MAGIC_LINK_BASE_URL: "https://${DOMAIN:?variável DOMAIN obrigatória em produção}"
```
Se houver prefixo de URL, exigir uma base explícita em vez de derivá-la. **Validar:** capturar e-mail em SMTP de teste usando configuração de produção e conferir origem/caminho do link.

### BUG-028: Allowlist de proxies definida nos exemplos não chega à API

**Arquivos:** `services/api/src/main/resources/application-prod.properties` — **16**; `infra/docker-compose.yml` — **62–89**; `infra/docker-compose.prod.yml` — **48–65**.

**Código problemático:**
```properties
quarkus.http.proxy.trusted-proxies=${QUARKUS_HTTP_PROXY_TRUSTED_PROXIES}
```

**Impacto:** a variável dos exemplos TLS não é repassada pelo Compose. A configuração é opcional no Quarkus; ausência resulta em confiar em todos os proxies, não em falha de startup. O Compose local publica API:8080 e um cliente direto pode influenciar IP/protocolo/host encaminhados. O proxy de produção sobrescreve headers, mitigando acesso pelo Caddy; não se alega bypass externo incondicional na implantação de produção.

Prova isolada com SmallRye Config 3.16.0: `trustedProxies=Optional.empty`, saída 0. O padrão está documentado no [ProxyConfig do Quarkus 3.33.3.2](https://raw.githubusercontent.com/quarkusio/quarkus/3.33.3.2/extensions/vertx-http/runtime/src/main/java/io/quarkus/vertx/http/runtime/ProxyConfig.java).

**Solução em `api.environment`:**
```yaml
QUARKUS_HTTP_PROXY_TRUSTED_PROXIES: "${QUARKUS_HTTP_PROXY_TRUSTED_PROXIES:?configure o proxy confiável}"
```
Configurar IP/rede efetiva do proxy e utilizar identidade de conexão validada nos filtros, em vez de reler `X-Forwarded-For` bruto. **Validar:** configuração consolidada e requisição direta com headers falsos, que não deve alterar a identidade aceita.

### BUG-029: Configuração S3 de produção não é consumida pelo adaptador

**Arquivos:** `infra/docker-compose.prod.yml` — **58–62**; `services/api/src/main/java/br/com/satoshipet/api/storage/MinioObjectStorage.java` — **35–57**; `services/api/src/main/resources/application.properties` — **119–123**.

**Código problemático:**
```yaml
QUARKUS_S3_ENDPOINT_OVERRIDE: ""
AWS_ACCESS_KEY_ID: "${AWS_ACCESS_KEY_ID:?variável AWS_ACCESS_KEY_ID obrigatória em produção}"
AWS_SECRET_ACCESS_KEY: "${AWS_SECRET_ACCESS_KEY:?variável AWS_SECRET_ACCESS_KEY obrigatória em produção}"
QUARKUS_S3_AWS_REGION: "${AWS_REGION:-sa-east-1}"
```

**Impacto:** o projeto usa cliente MinIO configurado por `satoshi-pet.storage.*`/`MINIO_*`, não extensão Quarkus S3. O Compose consolidado mantém `MINIO_ENDPOINT=http://minio:9000` e credenciais locais. Mesmo fornecendo AWS, a arte fica no MinIO da stack, contrariando a configuração e a expectativa de persistência externa.

**Solução:** alinhar as variáveis ao adaptador real e exigir endpoint/buckets externos quando esse for o destino:
```yaml
MINIO_ENDPOINT: "${OBJECT_STORAGE_ENDPOINT:?informe endpoint S3 HTTPS}"
MINIO_ACCESS_KEY: "${AWS_ACCESS_KEY_ID:?informe access key}"
MINIO_SECRET_KEY: "${AWS_SECRET_ACCESS_KEY:?informe secret key}"
MINIO_BUCKET: "${OBJECT_STORAGE_BUCKET:?informe bucket aprovado}"
MINIO_STAGING_BUCKET: "${OBJECT_STORAGE_STAGING_BUCKET:?informe bucket staging}"
```
Remover configurações `QUARKUS_S3_*` sem consumidor; provisionar política/buckets no destino escolhido. **Validar:** objeto escrito/recuperado no endpoint externo de teste, não no MinIO local.

## MÉDIO

### BUG-002: Reconciliação sem mudança produz evento novo em todo poll

**Status:** achado de 12/09/2026 reavaliado e ainda aberto.

**Arquivo:** `services/api/src/main/java/br/com/satoshipet/api/btc/BitcoinMonitorService.java` — **145–149, 547–572**.

**Código problemático:**
```java
if (balance.state() == BitcoinIndexerPort.BalanceState.CONFIRMED) {
    emitReconciliationEvent(address, balance, now);
```
```java
outboxService.save(
        UUID.randomUUID(),
```

**Impacto:** endereço sem nenhuma alteração gera evento distinto a cada 60s: 1.440 por dia, por endereço. Isso cresce outbox, tráfego e armazenamento de deduplicação (BUG-026), sem representar transição de negócio. A idempotência de `OutboxService` não ajuda porque o ID é sempre novo.

**Solução:** guardar a última projeção emitida e emitir somente quando mudar saldo/pendências/tip reconciliado, usando identidade estável para retransmissão da mesma revisão. Por exemplo, com os campos adicionados à projeção persistida:
```java
boolean changed = !Objects.equals(state.lastPublishedConfirmedSats, balance.confirmedSats())
        || !Objects.equals(state.lastPublishedPendingSats, balance.pendingSats());
if (changed) {
    emitReconciliationEvent(address, balance, now);
    state.lastPublishedConfirmedSats = balance.confirmedSats();
    state.lastPublishedPendingSats = balance.pendingSats();
}
```
Incluir altura/hash quando presentes no contrato real, e manter `lastCheckedAt`/métricas para o heartbeat operacional. **Validar:** dois polls idênticos produzem um evento; alteração de saldo ou tip relevante produz outro.

### BUG-020: Reaprovação tira pet do ovo sem saldo confirmado

**Arquivo:** `services/api/src/main/java/br/com/satoshipet/api/art/ArtworkPipeline.java` — **166–183**.

**Código problemático:**
```java
if (pet.bornAt != null) {
    pet.presentation = PetPresentation.CREATURE;
}
```

**Cenário:** aprovar arte, gastar saldo, aguardar 24h e retornar ao ovo; repetir POST de aprovação. O método aceita arte já aprovada e restaura CREATURE, porque `bornAt` é permanente. O próximo tick pode corrigir o estado, mas a chamada repetida reintroduz a violação. Uma primeira aprovação muito tardia também precisa verificar elegibilidade atual. CC-12.

**Solução mínima:**
```java
if (artwork.generationStatus == ArtGenerationStatus.APPROVED) return;
if (artwork.generationStatus != ArtGenerationStatus.AWAITING_APPROVAL) {
    throw new ArtworkOperationException("not_ready", "Arte indisponível para aprovação");
}
```
Além da guarda idempotente, delegar a apresentação ao motor com saldo reconciliado, sem inferi-la apenas por `bornAt`. **Validar:** aprovação repetida após retorno ao ovo não altera apresentação, versão nem emite novo `PET_ARTWORK_READY`.

### BUG-022: nginx direto trata atlas como arquivo local e não faz upgrade do WS real

**Arquivo:** `apps/pwa/nginx.conf` — **34–44, 57–65, 87–92**.

**Código problemático:**
```nginx
location /api/ {
```
```nginx
location /ws {
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
}
```
```nginx
location ~* \.(js|css|mjs|woff2?|ttf|eot|ico|svg|png|jpg|jpeg|gif|webp|avif)$ {
    try_files $uri =404;
}
```

**Impacto:** no acesso direto documentado em `http://localhost:4200`, a regex estática prevalece sobre `/api/` para `atlas.png`/preview e devolve 404. O WebSocket usa `/api/ws/address/...`, portanto cai no proxy REST, sem os headers do bloco `/ws`. Via Caddy, a API é encaminhada diretamente e esses problemas de nginx são evitados. Regra de seleção de location: [documentação oficial nginx](https://nginx.org/en/docs/http/ngx_http_core_module.html#location).

**Solução:** manter headers existentes e usar prefixos coerentes, protegidos contra regex estática:
```nginx
location ^~ /api/ws/ {
    set $api_upstream http://api:8080;
    proxy_pass $api_upstream;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
    proxy_set_header Host $host;
    proxy_read_timeout 3600s;
    proxy_send_timeout 3600s;
}
location ^~ /api/ {
    set $api_upstream http://api:8080;
    proxy_pass $api_upstream;
    proxy_http_version 1.1;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $remote_addr;
    proxy_set_header X-Forwarded-Proto $scheme;
}
```
**Validar:** PNG da API chega ao backend e handshake WS retorna 101 pelo nginx direto. Confirmação por configuração; nginx não foi iniciado nesta sessão.

### BUG-023: Horário antigo retrocede relógio e desconta energia duas vezes

**Arquivo:** `services/api/src/main/java/br/com/satoshipet/api/pet/engine/ReserveClock.java` — **52–64**. Chamador relevante: `services/api/src/main/java/br/com/satoshipet/api/pet/PetTickJob.java` — **62–73**.

**Código problemático:**
```java
if (remaining.signum() > 0) {
    return new Consumption(remaining.setScale(ReserveMath.SCALE, ROUNDING), null, now);
}
```

**Cenário reproduzido:** reserva 24h, avaliada às 10h; chamada atrasada com `now=09h` preserva a reserva, mas grava avaliação 09h. Chamada seguinte às 10h desconta mais uma hora: **23h**, apesar de início/fim às 10h. O tick captura um único `now` antes de percorrer todos os pets; o monitor pode ter atualizado um deles depois desse instante. O teste `nowAntesDeLastEvaluatedNaoRetrocede` verifica justamente o timestamp antigo, legitimando a regressão.

**Solução:** não retroceder a marca temporal:
```java
if (now.isBefore(lastEvaluatedAt)) {
    return new Consumption(clampedReserve, depletedAt, lastEvaluatedAt);
}
```
Inserir após calcular `clampedReserve`; no job capturar o instante efetivo por pet. Reavaliar também o estado emocional com tempo monotônico. **Validar:** sequência 10h → 09h → 10h mantém 24h; chamadas ordenadas continuam consumindo normalmente.

### BUG-024: Replay WebSocket envia o evento mais antigo por último

**Arquivo:** `services/api/src/main/java/br/com/satoshipet/api/realtime/AddressWebSocket.java` — **120–129**.

**Código problemático:**
```java
for (int i = 1; i < serialized.size(); i++) {
    String payload = serialized.get(i);
    connection.sendTextAndAwait(payload);
}
yield serialized.get(0);
```

**Impacto:** ao recuperar E1, E2 e E3, o handler envia E2/E3 durante a execução e só devolve E1 ao framework depois. Clientes recebem estado e cursores fora de ordem, podendo regredir ou descartar E1. Não depende de race para a sequência do código ser incorreta.

**Solução:** enviar todos na mesma ordem e não devolver um frame adicional:
```java
for (String payload : serialized) {
    connection.sendTextAndAwait(payload);
}
yield null;
```
Em falha de envio, interromper replay e permitir nova reconexão; não continuar como se a sequência tivesse sido entregue. **Validar:** três eventos produzem cursores crescentes na conexão real. Rever também fallback quando o cursor já saiu do ring buffer.

### BUG-025: Cookie de sessão não recebe Secure em produção

**Arquivos:** `services/api/src/main/java/br/com/satoshipet/api/auth/MagicLinkVerifyResource.java` — **126–134**; `services/api/src/main/java/br/com/satoshipet/api/auth/RegistrationResource.java` — **94–102**; `services/api/src/main/java/br/com/satoshipet/api/account/AccountResource.java` — **189–194**.

**Código problemático:**
```java
.httpOnly(true)
.secure(false)
.sameSite(NewCookie.SameSite.STRICT)
```
A recuperação também omite `SameSite`.

**Impacto:** o comentário sugere que HTTPS no proxy habilitará o atributo, mas Caddy não reescreve `Set-Cookie`. HSTS/redirect mitigam parte da exposição, porém o cookie continua sem restrição própria a HTTPS e os fluxos emitem políticas diferentes. Viola contrato `docs/contratos/sessao-csrf.md`.

**Solução:** centralizar construção do cookie e configurar explicitamente por ambiente:
```java
.httpOnly(true)
.secure(secureCookies)
.sameSite(NewCookie.SameSite.STRICT)
```
`secureCookies=true` em staging/produção; false somente no HTTP local controlado. Reutilizar no login, cadastro, recuperação e expiração. **Validar:** atributos de `Set-Cookie` nos três fluxos de autenticação sob o perfil publicado.

### BUG-026: Deduplicação de eventos mantém todas as chaves e locks na memória

**Arquivo:** `services/api/src/main/java/br/com/satoshipet/api/outbox/OutboxEventDeduplication.java` — **22–24, 40–47**.

**Código problemático:**
```java
private final Set<UUID> appliedKeys = ConcurrentHashMap.newKeySet();
private final Map<UUID, Object> locks = new ConcurrentHashMap<>();
```
```java
Object lock = locks.computeIfAbsent(key, ignored -> new Object());
...
appliedKeys.add(key);
```

**Impacto:** não há remoção nem limite. Todo evento entrega mais entradas permanentes até reiniciar o processo; a repetição por minuto do BUG-002 acelera o consumo, mesmo sem clientes conectados. Exemplo de volume, não medição de heap: 1.000 endereços podem gerar 1,44 milhão de IDs por dia só de reconciliação. O ring buffer de 200 eventos não limita essas duas coleções.

**Solução:** substituir a memória sem limite por identidade/cursor persistente e janela de replay definida. Exemplo de chave durável por consumidor:
```sql
CREATE TABLE consumer_event_receipts (
    consumer_name VARCHAR(100) NOT NULL,
    event_id UUID NOT NULL,
    applied_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (consumer_name, event_id)
);
```
Consumidores transacionais devem gravar efeito/chave na mesma transação. Para WebSocket, manter cursor/ID no cliente para deduplicar retransmissões e um buffer de memória limitado; envio de rede não se torna exatamente-uma-vez pela tabela. Definir retenção dos registros junto à outbox. **Validar:** volume prolongado com heap limitado e reentrega do mesmo evento sem duplicar efeito lógico.

### BUG-027: Fuso malformado gera 500 e offsets são aceitos como zona IANA

**Arquivo:** `services/api/src/main/java/br/com/satoshipet/api/auth/RegistrationService.java` — **92–97**.

**Código problemático:**
```java
try {
    java.time.ZoneId.of(tz);
} catch (java.time.zone.ZoneRulesException e) {
    throw new RegistrationException("invalid_timezone", "Fuso horário inválido: " + tz);
}
```

**Cenário reproduzido na API Java utilizada:** `America/Sao Paulo` lança `DateTimeException`, não a subclasse capturada, e a operação cai no erro genérico do resource. `-03:00` e `UTC+03:00` são aceitos, embora o projeto exija zona IANA para lidar com regras civis. A UI atual não envia fuso; o endpoint aceita chamadas diretas.

**Solução:** restringir ao catálogo suportado, devolvendo erro de domínio:
```java
if (!java.time.ZoneId.getAvailableZoneIds().contains(tz)) {
    throw new RegistrationException("invalid_timezone", "Informe um fuso horário IANA válido.");
}
```
**Validar:** São Paulo e zonas válidas; offsets; espaço, string curta e região inexistente. Não é necessário consultar serviços externos para essa validação.

### BUG-030: Compose de produção sobe serviços auxiliares de desenvolvimento

**Arquivos:** `infra/docker-compose.yml` — **166–172, 182–204**; `infra/docker-compose.prod.yml` — comentário inicial sobre perfis.

**Código problemático:**
```yaml
mailpit:
  image: axllent/mailpit:v1.21
```
```yaml
bitcoind:
  image: bitcoin/bitcoin:29.0
```

**Impacto:** nenhum deles tem `profiles: [dev]`, apesar da documentação afirmar isolamento por perfil. A configuração consolidada de produção inclui Mailpit e regtest, com portas 8025/1025 e 18443/28332/28333 publicadas. Isso inicia processos e expõe interfaces sem finalidade na produção. Regtest não contém fundos reais; não se alega perda financeira.

**Solução:** adicionar perfil aos serviços exclusivamente locais:
```yaml
mailpit:
  profiles: [dev]
bitcoind:
  profiles: [dev]
```
Ajustar o comando de desenvolvimento para `--profile dev`, incluindo captura SMTP quando necessária. Revisar `minio-init` conforme o destino de storage efetivo do BUG-029. **Validar:** `docker compose ... config --services` na configuração de produção não inclui auxiliares locais; stack dev continua com SMTP e regtest.

## Suspeitos e pontos de cobertura a verificar

Estes itens **não entram nas 29 ocorrências**. As condições abaixo precisam de reprodução integrada ou delimitação adicional:

1. **Cache privado entre identidades:** `apps/pwa/ngsw-config.json:73–79` inclui `/api/**`; `PetAccountService.loadSnapshot()` usa GET comum; `AuthService` limpa caches no logout, mas não ao autenticar B sobre A nem no 401. `/me` usa bypass corretamente. Verificar em navegador com SW instalado: cache de A, login B e leitura privada de B com timeout/offline. Possível fallback por URL para resposta de A. Correção a avaliar: cache por identidade ou excluir endpoints privados do SW, limpar transições e cancelar respostas antigas em voo.
2. **Aprovação/regeneração concorrentes:** `ArtworkService.java:68–81` e `PetArtwork` não mostram lock/versão para impedir uma geração em andamento de sobrescrever aprovação concluída. Reproduzir duas transações com barreira e falha no storage antes de fixar severidade.
3. **Indisponibilidade prolongada durante carência:** `PetEngine.onProviderFailure()` é no-op e o tick continua usando `zeroBalanceSince`. Verificar se 24h sem reconciliação permite concluir retorno ao ovo com saldo desconhecido, contra PRD §7.5. Também verificar aviso de dado desatualizado nas projeções.
4. **Recuperação funcional incompleta:** o fluxo consome código e cria sessão, mas não verifica/substitui e-mail nem devolve novo código, previstos por CC-02/CA-006. Há indicação de conclusão no backlog; reconciliar o estado de entrega com o contrato e validar a jornada inteira de perda de acesso ao e-mail.
5. **Retenção/escala:** `RateLimitFilter` mantém chaves por identidade sem limpeza; histórico público carrega todas as transações antes de limitar a 20; jobs usam TTL sem renovação durante ciclos longos. Medir carga e retenção e criar cenários concorrentes para dimensionar correções, sem assumir valores de capacidade que não foram medidos.
6. **Bech32 maiúsculo na UI:** o backend aceita/canonicaliza BC1, mas a regex em `cadastro.component.ts:292–293` aceita só prefixo minúsculo. Reproduzido no harness; melhoria pequena de validação de entrada, mantida fora dos achados prioritários. A rejeição de testnet/regtest em produção é intencional.

## Observações gerais

### Padrões positivos observados

- `BigDecimal`/`NUMERIC` para duração e `long`/`BIGINT` para sats; funções matemáticas puras com testes de fronteira.
- Magic link persiste hash e usa consumo condicional; tokens usam aleatoriedade adequada. Prazo de troca rejeita o limite exato de 72h.
- DTOs públicos explícitos e allowlist de eventos; canal privado verifica conta da sessão e revalida durante operações.
- Outbox exige transação `MANDATORY`; erros de serialização do monitor provocam rollback. A trava de job usa aquisição condicional no PostgreSQL.
- Assets aprovados/staging separados; streams de storage são fechados com try-with-resources.
- PWA usa signals/OnPush; leitura de identidade tem bypass de SW/HTTP cache; logout exige revogação confirmada antes de limpar identidade local.
- Testes existentes detectam diversos cenários de domínio, porém mocks/stubs não substituem provas do adaptador Esplora, transações independentes e navegação real.

### Suspeitas descartadas ou não contadas

- A variável ausente de proxies **não** foi classificada como startup quebrado: o comportamento opcional foi conferido (BUG-028).
- `UriInfo.getPath()` com barra inicial não foi apontado como bypass CSRF; a implementação Quarkus usada conserva a barra nesse fluxo.
- `WebSocketEvent.data` como `JsonNode` é serializável no contrato; o problema relevante está no tratamento do cliente e na ordem de replay.
- Credenciais de fixtures/regtest não foram tratadas como segredos reais vazados.
- Restrições de sockets, Docker e attach no ambiente de auditoria não foram convertidas em bugs do produto. Erros de classpath da suíte completa permanecem sem conclusão independente.

### Ordem sugerida de correção

1. Desbloquear implantação: BUG-003/004; conferir env e storage: BUG-021/028/029/030.
2. Corrigir fidelidade on-chain e energia: BUG-005..010 e BUG-023; validar com PostgreSQL/regtest e fake HTTP Esplora.
3. Completar jornada de acesso/conta: BUG-011..015 e BUG-025/027.
4. Corrigir visualização e aprovação: BUG-016..020, BUG-022/024; prova em navegador com SW instalado.
5. Controlar geração/retenção de eventos: BUG-002/026; executar carga controlada.

Cada correção comportamental deve ter regressão significativa e documentação do contrato afetado. Incrementar versão pelo script do projeto conforme o conjunto entregue; não editar números manualmente.

## Histórico preservado da revisão anterior

- **12/09/2026 — BUG-001:** relatório anterior registrou ajuste da regex de comprimento de endereço Bitcoin no cadastro como corrigido. Esse registro histórico não é uma nova validação criptográfica de endereços nem uma nova ocorrência em aberto.
- **12/09/2026 — correções declaradas anteriores:** reutilização de pet no cadastro compartilhado, reativação de vínculo histórico na troca, ligação de `MAGIC_LINK_TTL` ao Compose e melhorias de fixtures/correlation ID. Os registros foram preservados; não significam cobertura de concorrência ou criação de pet durante troca para endereço novo.
- **BUG-002:** evento de reconciliação em todos os polls permanece aberto, com evidência atual e relação com retenção em memória descritas acima.
