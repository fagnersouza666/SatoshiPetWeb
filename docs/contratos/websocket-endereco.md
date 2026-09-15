# Contrato WebSocket — canal por endereço

Referências: PRD §16.3, FUND-05, CA-058.

## Conexão

```
wss://{host}/api/ws/address/{canonical}
```

- `{canonical}` é o endereço Bitcoin em forma canônica (bech32 em minúsculas).
- Não exige autenticação neste recorte FUND/BTC; dados são projeção pública
  redigida conforme [eventos-bitcoin-redaction.md](./eventos-bitcoin-redaction.md).

## Mensagens do servidor

Envelope JSON comum:

```json
{
  "type": "SNAPSHOT | EVENT | PING",
  "cursor": "42",
  "data": { }
}
```

| `type` | Quando | `data` |
| --- | --- | --- |
| `SNAPSHOT` | Abertura da conexão | Estado mínimo do endereço + cursor atual |
| `EVENT` | Evento outbox publicado | Payload redigido do evento |
| `PING` | Heartbeat (~30 s) | omitido |

O cursor é string monotônica por canal, atribuída por
`RealtimeEventCursorService`.

No backend, o envelope é dividido em DTOs explícitos: `WebSocketSnapshot`
(cursor + estado inicial), `WebSocketEvent` (cursor + payload JSON já redigido)
e `WebSocketPing` (somente `type`). O cursor é representado por
`WebSocketCursor`, mas continua serializado como string. Mensagens recebidas
usam `WebSocketClientMessage`; um cursor ausente em `RECONNECT` equivale a
`"0"`.

### Snapshot inicial

O snapshot usa o bloco público do pet (`PetPublicSnapshot`): `presentation`
e `state` (estado emocional da criatura). Sem pet no endereço, só o
`address` — não há `state` fictício.

```json
{
  "address": "bc1…",
  "presentation": "CREATURE",
  "state": "ALIMENTADO",
  "artworkVersion": "1",
  "atlasUrl": "/api/v1/public/addresses/bc1…/artwork/1/atlas.png"
}
```

Campos `artworkVersion` e `atlasUrl` aparecem somente com arte aprovada.
Evento incremental `PET_ARTWORK_READY` usa o schema
[`artwork-ready.schema.json`](./schemas/pet-events/v1/artwork-ready.schema.json).

## Mensagens do cliente

| JSON | Resposta |
| --- | --- |
| `{"type":"PONG"}` | silenciosa |
| `{"type":"RECONNECT","cursor":"N"}` | replay de `EVENT` com cursor > N |

O replay usa ring buffer em memória por endereço (suficiente para FUND).

## Integração outbox

`OutboxWebSocketConsumer` publica eventos cujo tipo começa com `BITCOIN_`,
`PET_` (payload público do motor do pet; ver [eventos-pet.md](./eventos-pet.md))
ou `TEST_` (testes). Quando `aggregate_type = "Address"`, o canal é o
`aggregate_id` canônico; eventos `PET_*` usam o campo `address` do payload.

O identificador `outbox_events.id` é a chave lógica de aplicação. Uma
reentrega do mesmo evento já aplicada no processo não cria outro cursor nem
outro broadcast; se a aplicação falhar, a chave permanece disponível para
retry. O registro acompanha a projeção em memória deste recorte FUND.

O `OutboxPublisher` usa uma trava persistida por agregado, formada por
`aggregate_type` e `aggregate_id`. Assim, duas réplicas não processam
concorrentemente eventos do mesmo agregado, enquanto eventos de agregados
distintos não compartilham a mesma trava. A trava só é liberada depois da
transação que atualiza `processed_at` terminar.

## Testes

- `OutboxWebSocketIntegrationTest` — outbox → WS end-to-end.
- `AddressWebSocketTest` — snapshot, PONG e RECONNECT.
