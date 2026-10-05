# Execução exclusiva de jobs

Os jobs de Bitcoin, relógio do pet e geração de arte criam um `ownerId` por invocação. A concessão inicial continua sendo adquirida por upsert atômico em `job_locks`; uma invocação antiga não pode renovar ou liberar a concessão de outro ciclo, mesmo na mesma réplica.

Cada endereço, pet ou arte passa por `JobLockService.runWhileOwned`. Em uma transação própria, o método trava a linha com `PESSIMISTIC_WRITE`, relê proprietário e vencimento e só então executa a unidade. Se a concessão já venceu ou mudou de proprietário, retorna `false` sem executar e o job interrompe o ciclo. O TTL é renovado antes e depois do trabalho; a trava de linha dura até o commit, impedindo uma assunção concorrente durante uma unidade que ultrapasse o TTL.

As mutações da unidade devem participar da transação da concessão (`REQUIRED`), incluindo outbox. Não abrir `REQUIRES_NEW` dentro do callback. Falhas revertem somente a unidade atual; os itens anteriores já confirmados são preservados. A geração mantém a transação individual por arte tanto no job quanto nas chamadas diretas do pipeline.

O TTL de 120 segundos limita o intervalo ocioso entre unidades, não a duração do lote. Os timeouts das integrações e da transação continuam obrigatórios: falha de conexão/rollback interrompe a unidade, e o próximo ciclo revalida seu estado persistido. Chamadas externas não transacionais devem manter as próprias chaves idempotentes; a concessão protege a execução no banco e não transforma um provedor externo em participante da transação.

Regressões cobrem proprietário antigo, concessão ausente/expirada, rollback da unidade, aquisição concorrente enquanto o trabalho atravessa o TTL e identidade diferente a cada ciclo.

O publicador de outbox usa a mesma proteção por evento, mantendo concessões por agregado. A leitura do lote apenas coleta IDs; o evento é relido dentro da transação protegida e ignorado se outra execução já o concluiu. Perda de concessão ou falha interrompe os próximos eventos desse agregado no lote. Outros agregados continuam independentes. As concessões são liberadas somente depois dos commits das unidades, e a projeção durável do consumidor participa da mesma transação do evento.
