# Schemas de eventos Bitcoin

**Versão do contrato:** 1  \
**Schema machine-readable:** [`schemas/bitcoin-events/v1/event.schema.json`](./schemas/bitcoin-events/v1/event.schema.json)  \
**Escopo:** payload público dos seis eventos `BITCOIN_*` do monitor  \
**Referências:** PRD §9.2–§9.4 e §16.3; definição técnica §5; catálogo [eventos-bitcoin.md](./eventos-bitcoin.md)

Este documento complementa o catálogo de nomes e transições. Ele define o
envelope comum, os payloads públicos e a correlação operacional. Não define
persistência de outbox, entrega, protocolo WebSocket, cursores ou ordenação;
essas responsabilidades pertencem aos contratos de infraestrutura e
transporte.

## Envelope comum

Todo evento usa o mesmo envelope, independentemente de backend, WebSocket ou
relatório:

```json
{
  "eventId": "018f0c8e-6b7e-7abc-8f12-123456789abc",
  "eventType": "BITCOIN_TRANSACTION_CONFIRMED",
  "schemaVersion": 1,
  "occurredAt": "2026-09-12T16:00:00Z",
  "correlationId": "018f0c8e-6b7e-7abc-8f12-abcdef012345",
  "causationId": "018f0c8e-6b7e-7abc-8f12-001122334455",
  "payload": {}
}
```

| Campo | Regra |
| --- | --- |
| `eventId` | UUID opaco e imutável da ocorrência lógica. Uma retransmissão mantém o mesmo ID; não gerar um novo evento para cada tentativa de entrega. |
| `eventType` | Um dos seis nomes `BITCOIN_*` do catálogo T1, sem alias ou renomeação entre camadas. |
| `schemaVersion` | Inteiro `1`. A versão acompanha o envelope e o payload. |
| `occurredAt` | Instante UTC em que a transição foi observada ou concluída, não o instante de uma tentativa de transporte. |
| `correlationId` | UUID opaco da execução de polling, observação ou reconciliação que originou o evento. Deve ser propagado a eventos derivados e nunca conter conta, e-mail ou outro dado pessoal. |
| `causationId` | `eventId` da causa imediata, quando existir. É opcional para a raiz da operação e não substitui `correlationId`. |
| `payload` | Objeto discriminado por `eventType`, conforme o schema. Propriedades desconhecidas são rejeitadas. |

`eventId`, `correlationId` e `causationId` não são IDs de conta, pet, vínculo,
recebimento lógico ou outbox. Eles podem aparecer em uma projeção pública como
metadados técnicos porque são opacos e não identificam pessoas.

## Campos públicos por evento

Os campos abaixo são o conjunto permitido pelo schema. Quantidades são inteiros
em satoshis; o contrato não usa ponto flutuante, BTC decimal ou BRL.

| Evento | Campos públicos do payload |
| --- | --- |
| `BITCOIN_TRANSACTION_OBSERVED` | `network`, `address`, `transactionId`, `status` (`MEMPOOL`/`CONFIRMED`), `source` (`MEMPOOL`/`BLOCK`), `outputs[]` (`vout`, `amountSats`), `totalReceivedSats` e `block` quando já houver confirmação. |
| `BITCOIN_TRANSACTION_CONFIRMED` | `network`, `address`, `transactionId`, `previousStatus`, `status=CONFIRMED`, `source`, `outputs[]`, `totalReceivedSats` e `block` (`hash`, `height`, `confirmations`). A descoberta direta em bloco usa `previousStatus=UNKNOWN` e `source=BLOCK`. |
| `BITCOIN_TRANSACTION_REPLACED` | `network`, `address`, `replacedTransactionId`, `replacementTransactionId`, `replacedStatus=REPLACED`, `replacementStatus`, `conflictInputs[]` (`transactionId`, `vout`), `replacedReceivedSats` e `replacementReceivedSats`. A evidência de conflito é obrigatória. |
| `BITCOIN_TRANSACTION_DROPPED` | `network`, `address`, `transactionId`, `previousStatus`, `status=DROPPED`, `reason`, `evidence` e `invalidatedReceivedSats`. Ausência em uma resposta não é evidência válida: `RECONCILED_ABSENCE` exige `evidence.kind=RECONCILIATION`, `reconciledAt` e `tip`. |
| `BITCOIN_CHAIN_REORG` | `network`, `address`, `oldTip`, `newTip`, `forkHeight`, `depth`, `affectedTransactions[]` (`transactionId`, estados anterior/atual e blocos quando conhecidos), `previousConfirmedBalanceSats` e `currentConfirmedBalanceSats`. `REORGED` não é estado de transação; o estado atual é `MEMPOOL`, `CONFIRMED` ou `UNKNOWN`. |
| `BITCOIN_BALANCE_RECONCILED` | `network`, `address`, `balanceStatus=KNOWN`, `confirmedBalanceSats`, `pendingIncomingSats`, `pendingOutgoingSats`, `transactionsReconciled` e `reconciliationTip`. Uma falha de provedor não deve produzir este evento com saldo artificialmente zero. |

`address`, txids, vouts, hashes de bloco, alturas, confirmações e valores
on-chain são fatos públicos da rede. A validação semântica de endereço e
checksum continua sendo responsabilidade de `BitcoinIndexerPort`; o schema
apenas restringe a forma pública do valor.

## Invariantes do payload

- `outputs` contém somente saídas destinadas ao `address` do payload. Cada
  `vout` aparece no máximo uma vez e `totalReceivedSats` é exatamente a soma
  de `amountSats` dessas saídas.
- Em uma substituição, `replacedTransactionId` e
  `replacementTransactionId` identificam transações distintas; a evidência de
  conflito por entradas gastas é obrigatória. A troca atualiza o recebimento
  lógico e não representa uma segunda alimentação.
- `BITCOIN_CHAIN_REORG` é um evento histórico. Nenhum payload usa `REORGED`
  como estado atual; a situação posterior é `MEMPOOL`, `CONFIRMED` ou
  `UNKNOWN`.
- `BITCOIN_BALANCE_RECONCILED` somente representa um saldo conhecido após uma
  reconciliação concluída. Falha, indisponibilidade ou resposta incompleta do
  provedor não pode ser convertida em saldo zero.

## Versionamento e compatibilidade

- O identificador estável da versão é o `schemaVersion` do envelope e o
  diretório `schemas/bitcoin-events/v1/`.
- O mesmo `eventType` mantém o mesmo significado em backend, WebSocket e
  relatórios.
- É compatível adicionar uma propriedade opcional que não altere o significado
  existente. Propriedades novas obrigatórias, remoção, mudança de tipo ou
  mudança semântica exigem `schemaVersion=2` e um novo diretório.
- Consumidores devem rejeitar uma versão maior que a suportada e não devem
  reinterpretar uma versão desconhecida como v1.
- A versão do schema não é a versão do produto; portanto, esta entrega não
  altera a versão única da PWA/API.

## Limites de privacidade

O schema é uma lista explícita de campos públicos. Não incluir no envelope ou
em seus payloads: `accountId`, `petId`, `bindingId`, `logicalReceiptId`,
`outboxId`, `jobId`, e-mail, coordenadas precisas, corretora, observações
privadas, prompt de IA, tokens, chaves ou segredos.

O contrato não publica eventos de compra, sugestão DCA ou notificação e não
transforma nenhum deles em `PET_FEEDING_APPLIED`. Relações internas necessárias
para idempotência alimentar permanecem fora deste schema público.

## Exemplos mínimos

### Recebimento pendente

```json
{
  "eventId": "018f0c8e-6b7e-7abc-8f12-123456789abc",
  "eventType": "BITCOIN_TRANSACTION_OBSERVED",
  "schemaVersion": 1,
  "occurredAt": "2026-09-12T16:00:00Z",
  "correlationId": "018f0c8e-6b7e-7abc-8f12-abcdef012345",
  "payload": {
    "network": "mainnet",
    "address": "bc1qexampleaddress000000000000000000000000",
    "transactionId": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
    "status": "MEMPOOL",
    "source": "MEMPOOL",
    "outputs": [{"vout": 0, "amountSats": 3000}],
    "totalReceivedSats": 3000
  }
}
```

### Confirmação descoberta diretamente em bloco

```json
{
  "eventId": "018f0c8e-6b7e-7abc-8f12-223456789abc",
  "eventType": "BITCOIN_TRANSACTION_CONFIRMED",
  "schemaVersion": 1,
  "occurredAt": "2026-09-12T16:10:00Z",
  "correlationId": "018f0c8e-6b7e-7abc-8f12-abcdef012345",
  "payload": {
    "network": "mainnet",
    "address": "bc1qexampleaddress000000000000000000000000",
    "transactionId": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
    "previousStatus": "UNKNOWN",
    "status": "CONFIRMED",
    "source": "BLOCK",
    "outputs": [{"vout": 0, "amountSats": 3000}],
    "totalReceivedSats": 3000,
    "block": {
      "hash": "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
      "height": 900000,
      "confirmations": 1
    }
  }
}
```

Os exemplos são ilustrativos; os identificadores devem ser gerados pelo
produtor e os endereços/txids devem corresponder a fatos reais do ambiente.
