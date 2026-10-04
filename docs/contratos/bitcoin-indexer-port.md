# Contrato da porta `BitcoinIndexerPort`

Referências: PRD §4, BTC-01, CA-031, CC-05.

## Objetivo

Abstrair o indexador on-chain (Blockstream Esplora em produção) para que o
monitor Bitcoin (`BitcoinMonitorService`) não dependa de HTTP direto.

Implementações:

| Classe | Uso |
| --- | --- |
| `EsploraBitcoinIndexer` | Produção/dev com Esplora |
| `StubBitcoinIndexer` | Testes determinísticos |

## Operações

### `getBalance(String canonical)`

Retorna `BalanceResult`:

| Campo | Descrição |
| --- | --- |
| `state` | `CONFIRMED`, `UNKNOWN` ou `PROVIDER_FAILURE` |
| `confirmedSats` | Saldo confirmado (0 se indisponível) |
| `pendingSats` | Saldo mempool |

**Invariante CA-031:** falha de provedor → `PROVIDER_FAILURE`; o monitor **não**
interpreta como saldo zero nem descarta transações locais.

### `getTransactions(String canonical, String cursor, int limit)`

Transações confirmadas paginadas. `cursor` = último `txid` visto (`null` = início).

### `getMempool(String canonical)`

Transações pendentes na mempool para o endereço.

## `TransactionInfo`

| Campo | Descrição |
| --- | --- |
| `txid` | Hash hex (64 chars) |
| `amountSats` | Total recebido pelo endereço monitorado |
| `status` | `PENDING` ou `CONFIRMED` |
| `observedAt` | Primeira observação |
| `confirmedAt` | Confirmação (null se pendente) |
| `blockHeight` / `blockHash` | Bloco (null se pendente) |
| `outputs` | Saídas relevantes (`vout`, `address`, `amountSats`) |
| `inputs` | Outpoints consumidos (`txid`, `vout`), usados para provar conflitos |

## Erros

A porta **nunca lança exceção** para falhas de rede ou HTTP do provedor;
retorna `available=false`, `LookupState.UNAVAILABLE` ou `PROVIDER_FAILURE` conforme a operação; listas vazias disponíveis significam somente ausência de registros.

## Configuração

```properties
quarkus.rest-client.esplora.url=${ESPLORA_URL:https://blockstream.info/api}
```

## Testes

- `StubBitcoinIndexerTest` — comportamento determinístico.
- `BitcoinMonitorServiceTest` — integração com stub e regras CA-031.
- `BitcoinReconciliationTest` — conflitos reais por input, restauração, reorg, paginação e falhas.
- `EsploraBitcoinIndexerTest` — payloads/status/outspends, 404, falhas e endereço Base58 sensível a maiúsculas.

## Reconciliação persistida (auditoria de 04/10/2026)

- `TransactionPage` distingue resposta disponível/vazia de falha. O monitor não encerra backfill nem aceita saldo como fresco se uma página/mempool falhar.
- `lastSeenTxid` guarda a cabeça recente; `cursor` guarda o fim do backfill, com `backfillComplete` separado. A recuperação recente pagina até o marco anterior, inclusive quando houve mais de 25 transações durante interrupção.
- A observação de transação é única por `(address_id, txid)`: a mesma transação pode pagar vários endereços acompanhados.
- `AddressMonitorState` guarda saldo confirmado líquido, pendente e instante da consulta. Um gasto não apaga recebimentos históricos, mas reduz esse saldo reconciliado.
- A resposta pública usa `balanceKnown`, `balanceFresh` e `balanceCheckedAt`. Valores de saldo ausentes significam desconhecido; o último valor conhecido permanece disponível com `balanceFresh=false` após falha ou mais de 120s sem consulta bem-sucedida. Nunca apresentar soma de recebimentos como saldo.
- O histórico público limita a consulta SQL a 20 registros e informa a contagem total separadamente. Arte pública é enriquecida somente quando aprovada.
- Polls com saldos idênticos não criam outro `BITCOIN_BALANCE_RECONCILED`; o instante da consulta é persistido sem produzir evento de negócio redundante.

## Reconciliação por evidência (auditoria 2026-10-04)

`TransactionInfo.inputs` identifica os outpoints (`txid`, `vout`) consumidos.
`getTransaction` distingue transação encontrada, ausente (404) e provedor
indisponível. `getOutspend` informa o txid que atualmente consome um outpoint.
Uma ausência na listagem ou um 404 nunca invalida um recebimento. RBF exige
conflito de inputs comprovado; a transação substituta mantém o UUID do recebimento
lógico, revisando valor e status sem uma segunda alimentação. Se o conflito
comprovado remove a saída ao endereço, o recebimento e sua alimentação são
invalidados, preservando histórico. Consultas com falha preservam a projeção
anterior e sinalizam indisponibilidade.

O adaptador usa os contratos oficiais de
[transações e outspends do Esplora](https://github.com/Blockstream/esplora/blob/master/API.md#transactions).
Transações confirmadas continuam sendo reconciliadas individualmente, inclusive
fora da primeira página; uma confirmação que retorna à mempool conserva o mesmo
recebimento. Saldos de endereço vêm de snapshots do provedor, nunca da soma de
recebimentos históricos.

A migration V11 adiciona `bitcoin_inputs` e `bitcoin_transactions.logical_receipt_id`.
O vínculo é preenchido para os recebimentos anteriores; inputs ausentes no banco
são obtidos na próxima consulta que retornar a transação completa. Se a transação
legada já desapareceu do provedor antes de seus inputs serem registrados, sua
identidade é preservada até surgir evidência suficiente; endereço e valor nunca
são usados como atalho para inferir uma substituição.

O monitor serializa as escritas por pet/endereço e só avança cabeça/backfill após
consultar todas as páginas necessárias com sucesso. Uma transação sem saída ao
endereço é persistida, mas não cria alimentação de valor zero. A redução por RBF
revisa a alimentação existente; a remoção invalida, e uma substituição posterior
que restaure a saída pode reativar a mesma identidade.

Eventos `BITCOIN_CHAIN_REORG` usam snapshots de saldo. Os campos de saldo podem
ser `null` quando não há consulta confiável; isso nunca significa saldo zero.
Tips, altura do fork e profundidade também são `null` quando a evidência disponível
é somente o status individual da transação; metadados de chain nunca são inventados.
