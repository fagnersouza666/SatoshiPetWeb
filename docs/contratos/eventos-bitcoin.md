# Catálogo de eventos Bitcoin

**Versão do catálogo:** 1.0  \
**Escopo:** monitor Bitcoin  \
**Referências:** PRD §9.2–§9.4 e §16.3; definição técnica §5

Este catálogo registra os nomes dos eventos padronizados e as transições do
monitor que os originam. Os nomes são a referência única para backend,
WebSocket e relatórios. O catálogo não define payload JSON, envelope de
transporte, persistência de outbox ou contrato de consumidores.

## Eventos cobertos pelo monitor

| Evento | Transição coberta | Condição de emissão e consequência operacional |
| --- | --- | --- |
| `BITCOIN_TRANSACTION_OBSERVED` | Transação não registrada → transação observada | Emitir ao observar uma transação na mempool ou diretamente em um bloco. A observação direta em bloco é válida mesmo sem observação anterior na mempool. |
| `BITCOIN_TRANSACTION_CONFIRMED` | Mempool/observada ou descoberta direta → confirmada | Emitir quando a transação passa a ter confirmação. Consolidar o mesmo recebimento lógico sem reiniciar relógio nem duplicar alimentação. |
| `BITCOIN_TRANSACTION_REPLACED` | Transação observada → substituída | Emitir somente com evidência de conflito por entradas gastas e substituição (RBF). Atualizar o recebimento lógico; não criar uma segunda alimentação para o mesmo recebimento. |
| `BITCOIN_TRANSACTION_DROPPED` | Transação conhecida → descartada | Emitir somente após evidência e reconciliação suficientes de descarte ou invalidação. Ausência em uma resposta ou falha do provedor não é descarte. |
| `BITCOIN_CHAIN_REORG` | Bloco/confirmações observados → cadeia reorganizada | Emitir quando a reorganização alterar bloco, hash, confirmações ou saldo. Preservar a trilha e reavaliar o recebimento e a elegibilidade alimentar; a transação pode voltar à mempool, confirmar novamente ou ficar desconhecida. |
| `BITCOIN_BALANCE_RECONCILED` | Ponto seguro de reconciliação → estado reconciliado | Emitir ao concluir reconciliação idempotente de saldo e histórico a partir de cursor seguro com sobreposição. Falha de provedor não pode ser representada como saldo zero. |

## Estados e eventos

`MEMPOOL`, `CONFIRMED`, `REPLACED`, `DROPPED` e `UNKNOWN` são estados
operacionais da transação. `BITCOIN_CHAIN_REORG` é um evento histórico da
reorganização, não um estado atual adicional: depois dele, a situação deve
indicar se a transação voltou à mempool, confirmou novamente ou permanece
desconhecida.

Uma transação pode ser encontrada diretamente em bloco e, portanto, não precisa
passar pela transição observada na mempool. Reprocessamentos devem manter a
identidade da transação, da saída e do recebimento lógico, sem gerar eventos ou
alimentações duplicados.

## Limite de responsabilidade

O monitor emite somente os seis eventos `BITCOIN_*` acima. Eventos de pet,
como `PET_FEEDING_APPLIED`, `PET_FEEDING_REVISED` e
`PET_FEEDING_INVALIDATED`, pertencem ao motor do pet e não são substituídos
por eventos Bitcoin. Compra declarada, sugestão DCA e notificação também não
geram alimentação.

Os contratos de payload, correlação, ordenação e entrega pela outbox/WebSocket
serão definidos nas atividades próprias, sem alterar estes nomes ou as
transições catalogadas. A projeção pública e a redaction dos eventos estão em
[eventos-bitcoin-redaction.md](./eventos-bitcoin-redaction.md).
