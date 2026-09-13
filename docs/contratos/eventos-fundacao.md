# Catálogo de eventos da fundação

**Versão do catálogo:** 1  \
**Escopo:** nomes compartilhados pelo outbox, WebSocket e relatórios  \
**Referências:** PRD §16.3 e definição técnica §5

Este é o catálogo único de nomes de eventos do produto. O backend expõe os
mesmos valores em `DomainEventType`; o valor textual é o que deve ser gravado
em `outbox_events.event_type` e usado nas demais fronteiras.

## Eventos catalogados

| Contexto | Eventos |
| --- | --- |
| Bitcoin | `BITCOIN_TRANSACTION_OBSERVED`, `BITCOIN_TRANSACTION_CONFIRMED`, `BITCOIN_TRANSACTION_REPLACED`, `BITCOIN_TRANSACTION_DROPPED`, `BITCOIN_CHAIN_REORG`, `BITCOIN_BALANCE_RECONCILED` |
| Pet | `PET_FEEDING_APPLIED`, `PET_FEEDING_REVISED`, `PET_FEEDING_INVALIDATED`, `PET_ARTWORK_READY`, `PET_BORN`, `PET_RETURNED_TO_EGG`, `PET_REAPPEARED`, `PET_STATE_CHANGED` |
| DCA | `DCA_RECOMMENDATION_GENERATED`, `DCA_RECOMMENDATION_EXPIRED` |
| Compras declaradas | `PURCHASE_REPORTED`, `PURCHASE_CORRECTED`, `PURCHASE_DELETED` |
| Localização | `LOCATION_CHANGE_SCHEDULED`, `LOCATION_CHANGE_APPLIED` |

Não existem aliases, nomes alternativos ou eventos adicionais fora desta
lista. Em particular, uma compra declarada, sugestão DCA ou notificação nunca
é `PET_FEEDING_APPLIED`.

## Payload e responsabilidade dos contextos

O registro da fundação persiste o payload como JSON já serializado na coluna
`outbox_events.payload`. O catálogo define os nomes, não inventa um formato de
payload para contextos que ainda não têm contrato versionado. Cada contexto é
responsável por publicar um DTO explícito e versionado antes de gravá-lo na
outbox, conforme as atividades próprias.

## Persistência transacional

O serviço de outbox exige uma transação já aberta pelo serviço de aplicação.
Estado do agregado e evento devem ser gravados nessa mesma unidade atômica;
uma chamada fora de transação é rejeitada, sem iniciar uma transação
independente. Reprocessamentos devem reutilizar o mesmo `id` lógico: se ele já
estiver gravado, a primeira versão do evento é preservada.

O monitor Bitcoin já possui o contrato público dos seis eventos em
[eventos-bitcoin.md](./eventos-bitcoin.md),
[eventos-bitcoin-schemas.md](./eventos-bitcoin-schemas.md) e na política de
redaction. Os contratos de payload do Pet, DCA, compras e localização devem ser
adicionados por suas respectivas atividades sem alterar estes nomes.

## Correlação e idempotência

`event_type` é o nome canônico; `id`, `aggregate_type`, `aggregate_id`,
`created_at`, `correlation_id` e `payload` são metadados da outbox. O ID do
evento lógico deve ser mantido nas retransmissões e não pode ser substituído
por um novo ID a cada tentativa de entrega. Dados privados não devem ser
incluídos em payloads públicos.
