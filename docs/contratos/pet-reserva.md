# Reserva do pet e correções on-chain

Referências: PRD §9.3, CC-10/11/14, CA-015..018; BUG-009/023.

- O relógio persistido é monotônico: avaliação anterior a `lastEvaluatedAt` não retrocede o cursor nem desconta novamente o mesmo intervalo.
- Invalidação ou alteração de um recebimento recompõe a reserva em ordem de aplicação, usando sats e porção congelada de cada alimentação elegível. O teto de 168h é aplicado em cada etapa, com consumo do tempo entre elas.
- Um recebimento limitado a zero pelo teto pode contribuir para a reserva recalculada se uma alimentação anterior for invalidada. Seu crédito originalmente aplicado permanece no histórico.
- Uma alimentação antiga já consumida não é subtraída diretamente da energia de outra alimentação posterior.
- `initialAmountSats`, `initialPortionSats` e `initialDurationHours` preservam o snapshot original; `creditEffectiveAt` registra o instante em que o recebimento se tornou elegível. Um pendente observado no ovo só começa a consumir seu crédito quando confirmado. A elegibilidade persistida evita descartar crédito provisório anterior ao retorno ao ovo.
- Perda da última alimentação válida aguarda saldo líquido atual quando o provedor está indisponível. Uma resposta antiga não sobrescreve a reconciliação mais recente nem a disponibilidade.
- Confirmações históricas usam o instante do bloco; o replay consome o tempo transcorrido até a projeção atual. Recebimentos anteriores à criação do pet têm origem histórica e não provocam animação de alimentação ao serem descobertos no backfill.
- Eventos de correção continuam separados da aplicação inicial. Recebimentos invalidados não contribuem para a projeção.
- Saldo vem do snapshot reconciliado de `AddressMonitorState`, nunca da soma histórica de recebimentos. Falha do provedor preserva o último saldo e marca indisponibilidade. Um tick não conclui a carência com evidência anterior ao tick; uma nova reconciliação de saldo zero conclui a carência quando cabível.
- Aprovação de arte chama o motor com saldo conhecido; ausência de saldo disponível não tira o pet do ovo.
- Escritores adquirem `Pet.lockForUpdate` antes de alterar campos. A primeira aquisição recarrega a linha; chamadas aninhadas que já detêm a trava preservam alterações próprias. Quando houver arte, a ordem é pet → arte; exclusão de conta desassocia o criador sem excluir a criatura.

Regressões: `ReserveClockTest.nowAntesDeLastEvaluatedNaoRetrocede`, `PetEngineTest.invalidarCreditoAnteriorAoTetoReaplicaRecebimentoQueFoiCapado` e `invalidarRecebimentoJaConsumidoNaoDescontaRecebimentoPosterior`.

A migration V12 preserva como snapshot inicial os valores disponíveis nas linhas legadas; ela não recupera valores anteriores a correções feitas por versões antigas.
