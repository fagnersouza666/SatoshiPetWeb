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

O cursor é string numérica de 64 bits atribuída por sequência persistente, monotônica
na ordem de confirmação das projeções. `RealtimeEventCursorService` mantém um
recibo único por `outbox_events.id`; a sequência e os recibos sobrevivem ao reinício
da API e são compartilhados pelas réplicas. Uma linha de controle bloqueada até
commit serializa alocação/visibilidade: um cursor maior não ultrapassa uma transação
anterior ainda não confirmada. Lacunas após rollback são permitidas.

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
  "state": "ALIMENTADO"
}
```

Este snapshot é mínimo; a PWA lê o DTO completo pelo endpoint HTTP público.
Nele, `artworkVersion` e `atlasUrl` aparecem somente com arte aprovada.
O evento incremental `PET_ARTWORK_READY` usa o schema
[`artwork-ready.schema.json`](./schemas/pet-events/v1/artwork-ready.schema.json).

## Mensagens do cliente

| JSON | Resposta |
| --- | --- |
| `{"type":"PONG"}` | silenciosa |
| `{"type":"RECONNECT","cursor":"N"}` | replay de `EVENT` com cursor > N |

O replay consulta os últimos 200 recibos do endereço pelo índice
`(canonical, cursor)`, sem carregar o histórico inteiro nem reter buffers por canal.
Todos os frames seguem pelo mesmo caminho de envio em ordem crescente; falha
interrompe a sequência e não avança o cursor daquele frame. O cliente pode receber
novamente um frame cujo envio ficou ambíguo e deve deduplicar pelo cursor.

Cursor inválido, adiantado, de outra instância antiga ou anterior à janela disponível
resulta em `SNAPSHOT` com **cursor atual do servidor**, nunca com o cursor fornecido
pelo cliente. Nenhum replay parcial é apresentado como recuperação completa.
`resumedFrom` preserva apenas a origem solicitada e não é cursor autoritativo.

## Canal privado por conta

O canal privado separado usa:

```
wss://{host}/api/ws/account/{accountId}
```

O navegador envia o cookie de sessão `sp_session` no handshake. O servidor só
aceita a conexão quando a sessão está ativa e pertence ao mesmo `accountId` da
rota. Cookie ausente, expirado, inválido ou pertencente a outra conta encerra
o canal com código WebSocket 1008; nenhum `SNAPSHOT` é enviado nesses casos.

O snapshot privado contém somente o identificador da própria conta. Eventos
privados futuros devem ter contrato e DTO próprios; o consumidor da outbox não
faz fan-out de eventos públicos `PET_*` para esse canal. Assim, o canal por
endereço permanece anônimo e público, enquanto dados por conta não podem ser
obtidos apenas alterando o `accountId` da URL.

## Integração outbox

`OutboxWebSocketConsumer` publica eventos cujo tipo começa com `BITCOIN_`,
`PET_` (payload público do motor do pet; ver [eventos-pet.md](./eventos-pet.md))
ou `TEST_` (testes). Quando `aggregate_type = "Address"`, o canal é o
`aggregate_id` canônico; eventos `PET_*` usam o campo `address` do payload.

O identificador `outbox_events.id` é a chave lógica de aplicação. O consumidor grava
`realtime_event_receipts` na mesma transação; a restrição única e a trava transacional
impedem novo cursor na reentrega, mesmo após reinício ou em outra réplica. Rollback
remove o efeito e permite retry. Não existem conjuntos ou mapas ilimitados de
chaves/locks por evento em memória.

O consumidor não envia pela rede antes do commit. `AddressWebSocketFanout` consulta
a projeção confirmada a cada segundo em **cada réplica**, entregando às conexões
locais, inclusive quando outra réplica consumiu a outbox. Cada conexão guarda só
seu cursor e sincronização em `UserData`; o fechamento remove esse estado. O
snapshot inicial é enviado antes de habilitar a conexão para o fan-out.

Os recibos no banco acompanham a retenção da outbox: FK com `ON DELETE CASCADE`.
Não há expiração isolada que permita reaplicar um evento ainda retido. O limite de
200 vale para o replay/heap por consulta, **não** para o total de linhas históricas.
Uma futura política de expurgo precisa remover apenas outbox já processada e seu
recibo conjuntamente, fora da janela de entrega/retry definida; esta correção não
introduz descarte automático de histórico. Uma identidade apagada da outbox não
pode ser projetada diretamente por um objeto reconstruído em memória.

O `OutboxPublisher` usa uma trava persistida por agregado, formada por
`aggregate_type` e `aggregate_id`. Assim, duas réplicas não processam
concorrentemente eventos do mesmo agregado, enquanto eventos de agregados
distintos não compartilham a mesma trava. A trava só é liberada depois da
transação que atualiza `processed_at` terminar.

## Testes

- `OutboxWebSocketIntegrationTest` — outbox → projeção durável e reentrega por nova instância.
- `AddressWebSocketTest` — conexão WebSocket real, snapshot e RECONNECT.
- `AddressWebSocketReplayRegressionTest` — ordem de frames, falha parcial, fan-out em duas réplicas e limpeza da conexão.
- `RealtimeEventCursorServiceTest` — janela, rollback, concorrência e persistência compartilhada.

## Cliente PWA

A PWA invalida a leitura pública em `SNAPSHOT` e eventos `PET_*`/`BITCOIN_*`,
consultando o endpoint HTTP com bypass do SW; respostas HTTP anteriores em voo
não substituem um estado mais recente. O snapshot mínimo não é tratado como DTO
completo do endereço. O saldo desconhecido tem aviso explícito; falha posterior
preserva o último saldo e mostra que está desatualizado.

O cliente guarda cursor em memória por conexão lógica, compara a sequência sem
perda de precisão e ignora replay duplicado ou regressivo. Após fechamento,
reconecta com espera exponencial de 1 a 30 segundos e envia `RECONNECT` com o
último cursor. O primeiro snapshot de uma nova conexão prevalece sobre cursores
antigos, inclusive após reinício do servidor. A saída da página cancela a espera;
mensagens ou fechamentos de sockets substituídos não alteram a conexão atual.
