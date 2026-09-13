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

### Snapshot inicial (FUND)

Enquanto o motor do pet não estiver implementado, o snapshot inclui:

```json
{
  "address": "bc1…",
  "state": "HIBERNANDO"
}
```

## Mensagens do cliente

| JSON | Resposta |
| --- | --- |
| `{"type":"PONG"}` | silenciosa |
| `{"type":"RECONNECT","cursor":"N"}` | replay de `EVENT` com cursor > N |

O replay usa ring buffer em memória por endereço (suficiente para FUND).

## Integração outbox

`OutboxWebSocketConsumer` publica eventos cujo tipo começa com `BITCOIN_` (e
`TEST_` em testes). Quando `aggregate_type = "Address"`, o canal é o
`aggregate_id` canônico.

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
