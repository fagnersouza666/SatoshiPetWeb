# Fila de apresentação e estatísticas do pet da conta

**Escopo:** PET-15 (fila/cursor), PET-17 (stats), PET-18 (tick)  
**Referências:** PRD §9.5, §14.3; CC-15, CC-21; CA-009, CA-014, CA-025, CA-034, CA-035

Auth: cookie `sp_session` + `AuthenticatedSession`, no mesmo formato 401 de
`AccountResource` (`code=unauthorized`). `POST` de skip exige `X-CSRF-Token`.
Visitante anônimo **não** persiste cursor nesta API (401).

## Snapshot — `GET /api/v1/account/pet`

Bloco compartilhado (`PetPublicSnapshot`, CA-009) mais fila e stats da conta.
Sem `petId` / `accountId` / `email`. Duas contas no mesmo endereço veem o
mesmo bloco (nome, apresentação, estado, reserva, rótulos). `petState` só na
criatura; no ovo o campo é omitido. `reserveHours` é string
`BigDecimal.toPlainString()` (CC-10).

`operationalLabel`, nesta ordem:

1. `awaitingReference` → `Aguardando referência do plano`
2. ovo + sats pendentes de entrada → `Recebimento pendente`
3. ovo + `bornAt` preenchido + artwork ≠ `APPROVED` → `Preparando nascimento`
4. senão omitido

```json
{
  "petName": "Pixel",
  "presentation": "EGG",
  "reserveHours": "0.0000000000",
  "awaitingReference": true,
  "pendingMovesEgg": false,
  "operationalLabel": "Aguardando referência do plano",
  "presentationQueue": { "items": [] },
  "stats": {
    "bornAt": null,
    "ageHours": null,
    "timeInStateHours": {
      "ALIMENTADO": "0.0000000000",
      "PENSANDO": "0.0000000000",
      "CHATEADO": "0.0000000000",
      "FAMINTO": "0.0000000000",
      "CRITICO": "0.0000000000",
      "HIBERNANDO": "0.0000000000"
    },
    "reconstructedPeriod": false,
    "observedPeriod": false
  }
}
```

O mesmo bloco (sem fila/stats) aparece em `GET /api/v1/public/addresses/{address}`.
Sem pet no endereço, os campos do pet são omitidos na resposta pública.

## Fila — `GET /api/v1/account/pet/presentation-queue`

Itens elegíveis da outbox do pet da conta autenticada:

- `aggregateType = Pet`, `aggregateId = pet.id`
- tipos `PET_FEEDING_APPLIED`, `PET_FEEDING_REVISED`, `PET_BORN`, `PET_REAPPEARED`
- `createdAt >= boundAt` do vínculo ativo desta conta
- alimentação só entra se existir `PetFeeding` do mesmo evento com
  `origin=LIVE` e `presentable=true` (CA-014)

Cursor: `presentation_cursors.last_presented_event_id`. A fila são os elegíveis
**depois** do evento cursor (`createdAt`, depois `id`). Sem cursor, todos os
elegíveis desde o vínculo. Ordem: `createdAt ASC, id ASC`.

```json
{
  "items": [
    {
      "eventId": "<uuid>",
      "eventType": "PET_FEEDING_APPLIED",
      "occurredAt": "2026-09-13T15:00:00Z",
      "amountSats": 5000
    }
  ]
}
```

Sem `petId` / `accountId` / `email`. `amountSats` só se existir no payload.

## Skip — `POST /api/v1/account/pet/presentation/skip`

Avança o cursor para o **último** evento elegível atual (incluindo os já atrás
do cursor). Não chama `creditDelta` e não altera `reserveHours`,
`emotionalState` nem `presentation`. Cria o cursor se não existir.

```json
{ "skipped": true }
```

Sem eventos elegíveis: no-op 200 com o mesmo corpo.

## Stats — `GET /api/v1/account/pet/stats`

```json
{
  "bornAt": "2026-09-11T15:00:00Z",
  "ageHours": "48.0000000000",
  "timeInStateHours": {
    "ALIMENTADO": "12.0000000000",
    "PENSANDO": "3.0000000000",
    "CHATEADO": "0.0000000000",
    "FAMINTO": "0.0000000000",
    "CRITICO": "0.0000000000",
    "HIBERNANDO": "0.0000000000"
  },
  "reconstructedPeriod": false,
  "observedPeriod": true
}
```

- `bornAt` ISO-8601 ou `null` se nunca nasceu
- `ageHours` string decimal desde `bornAt` até agora; **não** zera no ovo (CC-21)
- `timeInStateHours` reconstitui as seis emoções pelos instantes de
  `PET_STATE_CHANGED` / `PET_BORN` / `PET_REAPPEARED` / `PET_RETURNED_TO_EGG`
  + intervalo aberto até `now` (ou `lastEvaluatedAt` se estiver no futuro)
- `reconstructedPeriod` se existe `PetFeeding` `HISTORICAL_RECONSTRUCTION`
- `observedPeriod` se existe `PetFeeding` `LIVE`
- horas em string `BigDecimal` (CC-10); sem petId

## Tick — `PetTickJob`

`@Scheduled(every = "60s", identity = "pet-tick")`. Lock `JobLockService`
nome `pet-tick`, TTL 120s. Para cada pet: `petLifecycle.tick(id, Instant.now())`
em UTC. Falha em um pet loga e segue. Desabilitado em testes
(`quarkus.scheduler.enabled=false`).
