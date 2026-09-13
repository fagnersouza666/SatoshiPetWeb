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

## Erros

A porta **nunca lança exceção** para falhas de rede ou HTTP do provedor;
retorna estados vazios ou `PROVIDER_FAILURE` conforme a operação.

## Configuração

```properties
satoshi.bitcoin.indexer=esplora|stub
satoshi.bitcoin.esplora.base-url=…
```

## Testes

- `StubBitcoinIndexerTest` — comportamento determinístico.
- `BitcoinMonitorServiceTest` — integração com stub e regras CA-031.
