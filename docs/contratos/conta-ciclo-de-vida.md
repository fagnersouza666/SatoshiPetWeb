# Ciclo de vida e privacidade da conta

Referências: CA-004, CA-006, CA-008, CC-02, CC-03, CC-22.

## Nome, endereço e concorrência

`PATCH /api/v1/account/pet/name` persiste a renomeação em transação e verifica
criador ainda vinculado sob a mesma trava do pet usada pelo motor/arte.
`POST /api/v1/account/address/change` aceita `bitcoinAddress` e `petName`
opcional. Para destino novo, usa o nome informado ou o nome anterior (Satoshi
quando não houver pet anterior); valida 1–100 caracteres. Destino conhecido
preserva nome, criador e arte. A operação cria/reutiliza pet e vínculo atomicamente.
Trocar para o endereço atual é no-op. O prazo original de 72 horas nunca muda.

Cadastro, login por magic link, troca, recuperação e exclusão adquirem uma trava transacional curta
na linha `account_mutation_locks/accounts`. Quando necessário, travam pets por
UUID em ordem crescente; a arte adquire Pet antes de PetArtwork. A serialização
global reduz throughput dessas mutações administrativas e impede corrida de
criação de endereços ainda inexistentes. Não é usada por consultas, monitor
Bitcoin ou motor do pet, nem deve envolver chamada a SMTP/IA/indexador.

## Exclusão

`DELETE /api/v1/account` só responde 204 depois da exclusão transacional efetiva.
Elimina conta, sessões, vínculos, códigos de recuperação, verificações de novo
e-mail, magic links do e-mail e cursor privado. A conta excluída deixa de ser
encontrada pelo login. Uma futura inscrição com o mesmo e-mail cria outra conta.

Pets, endereços, fatos públicos on-chain, arte, alimentações e valores congelados
das porções permanecem. As referências pessoais de criador, fonte alimentar e
origem de porção ficam nulas. O identificador opaco da conta e a data de exclusão
ficam em `account_deletion_tombstones`, sem e-mail ou endereço Bitcoin.

A troca de endereço usa `AccountConfigurationWipeService`, separado da exclusão:
limpa o cursor da configuração anterior e preserva conta/credenciais. Planos,
compras e subscriptions ainda não possuem tabelas neste recorte; seus adaptadores
futuros devem participar da mesma limpeza antes de expor essas funcionalidades.

## Restauração de backup

Exportar o diário `account_deletion_tombstones` após cada exclusão para a rotina
de backup e preservar o diário mais recente fora do backup que será restaurado.
Antes de liberar tráfego em uma restauração, importar seus registros com UPSERT.
Na inicialização, o adaptador reaplica o diário idempotentemente às contas
restauradas, preservando novamente os pets e os snapshots públicos. Restaurar
apenas um backup anterior sem incorporar o diário não preserva exclusões recentes.

## Fuso

Cadastro aceita somente identificadores presentes no catálogo IANA do runtime.
Offsets e entradas malformadas retornam 422 `invalid_timezone`; não geram 500.

## Recuperação completa — CC-02 / CA-006

`POST /api/v1/account/recovery/email` recebe `{code,email}`. Com código válido,
persiste um desafio de uso único associado ao código/conta e envia ao novo e-mail
um link `/recuperar?token=...`. Responde 202; não consome o código, não altera a
conta e não cria sessão. E-mail malformado retorna 400, e-mail de outra conta 409,
código inválido 401. Falha SMTP retorna 503 e permite novo envio.

`POST /api/v1/account/recovery/reset` exige `{code,token}`. O token é exclusivo
deste fluxo; um magic link de login não o substitui. Ambos devem corresponder à
mesma conta/código e estar válidos. Em uma transação serializada, o servidor
consome ambos com UPDATE condicional, troca o e-mail, invalida links do e-mail
antigo, revoga todas as sessões, substitui o código e cria uma nova sessão.
Preserva ID, endereço e prazo original da conta. Responde `{status:"ok",
recoveryCode:"..."}`, cookie e CSRF, com `Cache-Control: no-store`.

Código sozinho ou verificação expirada/reutilizada retorna 401. O novo código
bruto é entregue somente nessa resposta e não fica em logs/banco; a PWA pede
que o usuário o guarde antes de abrir a conta. Tokens têm o TTL curto configurado
para magic links. A chamada SMTP ocorre depois de a transação do desafio terminar.
Ver também [jornada da PWA](./pwa-recuperacao.md).

A verificação de login relê token e conta depois de adquirir a mesma trava da
recuperação. Consumo do magic link e criação da sessão pertencem a uma transação
única. Se um login antigo terminar primeiro, a recuperação revoga essa sessão;
se a recuperação terminar primeiro, o link antigo já não autoriza login. Nenhuma
sessão do e-mail anterior pode nascer no intervalo entre consumo e revogação.
