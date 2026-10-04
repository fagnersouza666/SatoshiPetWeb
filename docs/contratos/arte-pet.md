# Geração e aprovação da arte do pet

Referências: PRD §8.3, CC-12, CC-13; ART-05, ART-07; CA-037 a CA-040.

O pipeline conserva uma arte por pet e tentativas numeradas. O contexto é
congelado na primeira geração; mudanças posteriores de fuso/localização não
alteram a aparência. Se a conta criadora tiver sido excluída antes de congelar
o contexto, o fuso de fallback é a zona IANA `Etc/UTC`.

## Concorrência e autorização

Todo escritor da arte adquire a mesma trava pessimista do agregado `Pet`
utilizada pelo saldo e pelas operações de vínculo/exclusão; depois bloqueia
`PetArtwork`. Dados lidos antes de esperar pela trava são recarregados antes
de tomar decisões. A ordem é sempre pet → arte. Aprovação e regeneração
revalidam o criador ativo somente depois de adquirir a trava do pet.

O processo agendado seleciona candidatos e processa cada arte em transação
independente. O status é reavaliado sob trava, incluindo uma geração que
outro escritor já concluiu. Uma falha em um pet não reverte os anteriores.
Nenhum worker pode regenerar arte aprovada nem sobrescrever uma aprovação
com dados carregados antes de uma alteração concorrente.

## Criador ausente

O job também reconcilia `AWAITING_APPROVAL`. Se o criador tiver trocado de
endereço ou excluído sua conta, aprova o último conjunto válido já persistido
sem gerar novamente. Não transfere o direito de regenerar/renomear a outra
conta. Criador nulo significa ausência, preservando a arte e o pet compartilhado.

## Aprovação e apresentação

Aprovar `APPROVED` é idempotente: não promove objetos outra vez, não altera
versão nem apresentação e não emite novo `PET_ARTWORK_READY`. Sem conjunto
aguardando confirmação, a operação devolve o erro de domínio `not_ready`.

Na primeira aprovação, o pipeline promove o conjunto, atualiza os metadados
na transação e chama `PetLifecyclePort.onArtworkApproved`. O motor decide a
apresentação pelo saldo reconciliado disponível. `bornAt` isoladamente nunca
autoriza sair do ovo. Saldo desconhecido não vira zero nem autoriza
reaparecimento; aprovação não cria alimentação nem altera o saldo on-chain.

O evento `PET_ARTWORK_READY` é gravado após essa decisão, com a apresentação
efetiva (`EGG` ou `CREATURE`) e a URL versionada. Estado e outbox compartilham
a transação; cópias para o storage precisam ser repetíveis se houver rollback.

## Regressões

`ArtworkPipelineTest` cobre criador que sai depois da geração ou é excluído,
aprovação repetida após retorno ao ovo, saldos positivo/zero/desconhecido e
provedor indisponível, operação antes de existir conjunto válido e corridas
nos dois sentidos entre aprovação e regeneração com entidades antigas.
A execução da API é centralizada no gate do projeto.
