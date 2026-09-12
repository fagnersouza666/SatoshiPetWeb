# Redaction dos eventos Bitcoin

**Versão da política:** 1  \
**Escopo:** projeção pública dos seis eventos `BITCOIN_*` do monitor  \
**Referências:** PRD §6.4, §16.3 e §17.1; CA-009; catálogo
[eventos-bitcoin.md](./eventos-bitcoin.md)

Este contrato define quais dados podem sair do domínio interno no envelope dos
eventos Bitcoin. Ele se aplica ao HTTP público, ao canal WebSocket público e a
relatórios. O catálogo de eventos continua sendo a fonte dos nomes e das
transições; esta política é a fonte da projeção pública.

## Regra de projeção

Redaction é feita por **allowlist**. O produtor constrói um DTO público
explícito a partir do fato interno e só então serializa o evento. Uma denylist é
mantida como defesa adicional e para revisão, mas nunca substitui a allowlist.

O resultado da projeção deve obedecer simultaneamente a estas regras:

- propriedades desconhecidas ou privadas são omitidas antes da serialização;
- a validação do evento já projetado rejeita propriedades desconhecidas;
- campo público obrigatório ausente faz a projeção falhar de forma fechada, sem
  publicar um payload parcial;
- dado privado não é substituído por `null`, texto como `REDACTED`, hash ou
  valor mascarado: ele não aparece no payload;
- a projeção preserva sem alteração os fatos on-chain permitidos, o
  `eventId`, o tipo, a versão, os instantes e os IDs opacos de correlação;
- retransmissões usam a mesma projeção e não reintroduzem dados do objeto
  interno.

A serialização direta de entidade, mapa ou objeto interno é proibida. O
redactor não deve copiar propriedades por reflexão ou por `toJson` genérico. A
política machine-readable está em
[`schemas/bitcoin-events/v1/redaction-policy.json`](./schemas/bitcoin-events/v1/redaction-policy.json).

## Allowlist do envelope

Os únicos campos permitidos no envelope público são:

| Campo | Regra |
| --- | --- |
| `eventId` | Identificador opaco e imutável da ocorrência lógica. |
| `eventType` | Um dos seis nomes `BITCOIN_*` do catálogo. |
| `schemaVersion` | Versão inteira do contrato público. |
| `occurredAt` | Instante da transição, em formato UTC. |
| `correlationId` | Identificador opaco da operação de monitoramento. |
| `causationId` | Opcional; identificador opaco do evento causador imediato. |
| `payload` | Objeto com os campos permitidos para o `eventType`. |

IDs de correlação não são `accountId`, `petId`, `bindingId`,
`logicalReceiptId`, `outboxId` ou `jobId`. Eles não carregam e-mail, endereço
de conta, nome ou outra informação pessoal.

## Allowlist dos payloads

Somente os campos abaixo podem aparecer. Campos dentro de `[]` pertencem a
cada item da coleção; `block` e `tip` usam apenas `hash`, `height` e, quando
previsto, `confirmations`.

| Evento | Campos permitidos |
| --- | --- |
| `BITCOIN_TRANSACTION_OBSERVED` | `network`, `address`, `txid`, `status`, `source`, `outputs[]` (`vout`, `address`, `amountSats`), `totalReceivedSats`, `block` (`hash`, `height`, `confirmations`) opcional |
| `BITCOIN_TRANSACTION_CONFIRMED` | `network`, `address`, `txid`, `previousStatus`, `status`, `source`, `outputs[]` (`vout`, `address`, `amountSats`), `totalReceivedSats`, `block` (`hash`, `height`, `confirmations`) |
| `BITCOIN_TRANSACTION_REPLACED` | `network`, `address`, `replacedTxid`, `replacementTxid`, `replacedStatus`, `replacementStatus`, `conflictInputs[]` (`txid`, `vout`), `replacedReceivedSats`, `replacementReceivedSats` |
| `BITCOIN_TRANSACTION_DROPPED` | `network`, `address`, `txid`, `previousStatus`, `status`, `reason`, `evidence` (`kind`, `reconciledAt`, `tip` (`hash`, `height`), `replacementTxid`), `invalidatedReceivedSats` |
| `BITCOIN_CHAIN_REORG` | `network`, `address`, `oldTip` (`hash`, `height`), `newTip` (`hash`, `height`), `forkHeight`, `depth`, `affectedTransactions[]` (`txid`, `previousStatus`, `currentStatus`, `previousBlock`, `currentBlock`), `previousConfirmedBalanceSats`, `currentConfirmedBalanceSats` |
| `BITCOIN_BALANCE_RECONCILED` | `network`, `address`, `balanceStatus`, `confirmedBalanceSats`, `pendingIncomingSats`, `pendingOutgoingSats`, `transactionsReconciled`, `reconciliationTip` (`hash`, `height`) |

Endereço, txid, vout, hash de bloco, altura, confirmações e quantidades em
satoshis são fatos públicos on-chain. Satoshis são inteiros; BRL, ponto
flutuante e valores derivados de orçamento não pertencem a estes payloads.

## Campos que nunca atravessam a projeção

A lista abaixo é uma denylist mínima de segurança. Ela inclui nomes do domínio
e classes de dados que podem chegar ao objeto interno por composição de
serviços:

- identidade e vínculo: `accountId`, `petId`, `bindingId`,
  `logicalReceiptId`, e-mail, nome ou identificador do autor;
- infraestrutura: `outboxId`, `jobId`, `notificationId`, cursor de apresentação,
  cabeçalhos, URL interna, stack trace e dados de requisição do provedor;
- conta e finanças privadas: corretora, orçamento, plano DCA individual,
  compromisso, reserva financeira, compra declarada, observação privada e
  qualquer valor BRL pessoal;
- localização e arte: coordenada precisa, histórico de deslocamento, prompt,
  contexto privado, semente de geração e metadados não públicos do pet;
- segurança: token, código de recuperação, cookie, chave, seed, xprv, segredo,
  credencial ou conteúdo de autorização.

O conjunto completo e os nomes usados pela validação automatizada ficam no
arquivo JSON da política. Adicionar um campo novo ao payload exige atualizar
allowlist, schema e teste de contrato; não é permitido incluí-lo apenas porque
existe na entidade de origem.

## Canais, logs e falhas

- HTTP público, WebSocket público e relatório devem emitir a mesma projeção
  pública para o mesmo evento. Autenticação não amplia esse payload com
  metadados da conta.
- Um futuro evento privado precisa de contrato e DTO próprios; não se deve
  reutilizar o evento público adicionando campos privados.
- Logs registram, no máximo, `eventId`, `eventType`, `schemaVersion`,
  `correlationId` e um código operacional. Não registrar o JSON integral nem o
  objeto interno.
- Se a redaction ou a validação falhar, o evento não é enviado nem convertido
  em saldo zero, descarte ou alimentação. A falha fica disponível apenas como
  diagnóstico operacional sem dados privados.

Esta política não altera nomes, transições, idempotência ou elegibilidade
alimentar dos eventos. Em particular, nenhum evento de compra, sugestão DCA ou
notificação é transformado em `PET_FEEDING_APPLIED`.
