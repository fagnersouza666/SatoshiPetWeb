# Catálogo de eventos do pet

**Versão do catálogo:** 1.0  \
**Escopo:** motor do pet (`PetEngine`)  \
**Schema:** [`schemas/pet-events/v1/event.schema.json`](./schemas/pet-events/v1/event.schema.json)  \
**Referências:** PRD §16.3; CA-009; CA-014; PET-16

Este catálogo registra os nomes dos eventos `PET_*` emitidos pelo motor na
outbox transacional e publicados no WebSocket. Os nomes são a referência
única para backend, canal de endereço, fila da conta e relatórios.

Não emitir `PET_ARTWORK_READY` neste recorte (épico ART).

## Quando emitir

| Evento | Transição |
| --- | --- |
| `PET_FEEDING_APPLIED` | Nova alimentação `LIVE` criada (`PROVISIONAL` ou `VALID`) e apresentável |
| `PET_FEEDING_REVISED` | Alimentação `LIVE` existente com amount/duration/status revistos (RBF), sem ser a primeira criação |
| `PET_FEEDING_INVALIDATED` | Alimentação `LIVE` invalidada |
| `PET_BORN` | Primeira vez em que `bornAt` é preenchido |
| `PET_RETURNED_TO_EGG` | Apresentação `CREATURE` → `EGG` |
| `PET_REAPPEARED` | Apresentação `EGG` → `CREATURE` após já ter nascido. Só `lastReappearedAt` sem virar criatura (arte `PENDING`) **não** emite |
| `PET_STATE_CHANGED` | `emotionalState` mudou após `evaluate` (somente criatura) **ou** virada de apresentação/`awaitingReference` não coberta por BORN/EGG/REAPPEARED |

Confirmação de um `PROVISIONAL` que apenas promove a `VALID` com as mesmas
horas **não** gera segundo `PET_FEEDING_APPLIED` nem `PET_FEEDING_REVISED`.

Não emitir `PET_FEEDING_*` para `HISTORICAL_RECONSTRUCTION` nem para
alimentação com `presentable=false` (CA-014). A reconstrução pode emitir no
máximo um `PET_STATE_CHANGED` / `PET_BORN` se nascimento ou carência
alterarem o estado.

Compra declarada, sugestão DCA e notificação **nunca** geram
`PET_FEEDING_*` — não há caminho dessas operações até o motor.

## Payload público (CA-009)

O JSON gravado na outbox e transmitido no WebSocket contém **somente**:

| Campo | Tipo | Regra |
| --- | --- | --- |
| `address` | string | Endereço Bitcoin canônico |
| `eventType` | string | Um dos sete nomes acima |
| `occurredAt` | string ISO-8601 | Instante UTC da transição |
| `presentation` | `EGG` \| `CREATURE` | Apresentação após a transição |
| `emotionalState` | string ou omitido | Presente na criatura; omitido ou nulo no ovo |
| `reserveHours` | string decimal | `BigDecimal.toPlainString()` (CC-10; nunca `double`) |
| `awaitingReference` | boolean | Sem porção de referência |
| `feedingStatus` | `PROVISIONAL` \| `VALID` \| `INVALIDATED` | Só em `PET_FEEDING_*` |
| `amountSats` | inteiro | Só em `PET_FEEDING_*` |

Chaves **proibidas** em qualquer ponto do payload: `petId`, `accountId`,
`email`, `foodSourceAccountId`.

`aggregateType = "Pet"`. `aggregateId = pet.id` (interno à outbox, não vai
no payload público).

## Transporte

`OutboxWebSocketConsumer` publica `PET_*` no canal de endereço (campo
`address` do payload) e faz fan-out da mesma projeção pública para as contas
com vínculo ativo nesse endereço. Ver [websocket-endereco.md](./websocket-endereco.md).

A fila de apresentação da conta (`GET /api/v1/account/pet/presentation-queue`)
consome os mesmos eventos: `PET_FEEDING_APPLIED` / `PET_FEEDING_REVISED`
somente com alimentação LIVE apresentável, e `PET_BORN` / `PET_REAPPEARED`.
Ver [pet-apresentacao-e-stats.md](./pet-apresentacao-e-stats.md).
