# Cache da PWA

Referências: FUND-11, PRD §14.6, CA-067.

## Shell e arquivos visuais

O service worker faz prefetch do shell, de todos os ícones publicados em
`apps/pwa/public/icons` e do ovo padrão. Os demais arquivos estáticos são
carregados sob demanda. O contrato `npm run test:ngsw` valida o shell e
a inclusão de cada ícone; ele também participa de `npm test` e `check:pwa`.

## Respostas da API

O grupo `api-freshness` mantém a política network-first do Angular
(`freshness`) para `/api/**`, com timeout de 10 segundos, no máximo
100 respostas e validade de um dia. O fallback permite leitura offline;
não autoriza alterações financeiras offline nem transforma dados vencidos
em dados atuais. `npm run test:pwa:api-cache` protege esses limites e
participa de `npm test` e `check:pwa`.

## Isolamento e logout

O logout remove buckets privados e respostas privadas de buckets compartilhados.
As URLs públicas de arte de duas criaturas ou de versões diferentes continuam
distintas e preservadas, mesmo quando compartilham um bucket com previews
autenticados. O teste da PWA exercita `open`, `keys` e a exclusão por `Request`,
sem depender de um erro silenciado em um mock incompleto. O contrato do storage
também compara o conteúdo de duas chaves e verifica que excluir uma não altera
a outra.

## Arte pública aprovada

A API publica a arte em
`/api/v1/public/addresses/{address}/artwork/{version}/...`; identidade da criatura
e versão permanecem na URL usada como chave do cache. A PWA não acessa diretamente
o bucket MinIO nem recebe suas credenciais.

O data group `pet-artwork` precede `api-freshness` e usa a estratégia Angular
`performance` (cache-first), limitada a 50 respostas e 365 dias. O nome
`cacheFirst` não é uma estratégia válida do Angular. Previews autenticados de
`/api/v1/account/pet/artwork/preview/...` não entram nesse grupo público.

`npm run test:pwa:pet-assets` confere as estratégias aceitas pelo schema do
Angular instalado e gera um manifesto com o próprio `Generator` do Angular.
Verifica precedência, limites, preservação das queries e separação entre arte
pública, preview privado e URLs diretas do storage. Também participa de
`npm test` e `check:pwa`.

## Recuperação dos PRs antigos

- **#27:** recupera o contrato de shell/ícones e inclui o ícone de 32×32 no
  prefetch, preservando os gates atuais.
- **#30:** recupera os testes do contrato network-first da API, ausentes na main.
- **#38:** recupera o teste de duas chaves no storage e adapta a cobertura PWA
  para o contrato atual de CacheStorage, incluindo duas criaturas e duas versões.
- **#34:** substituído pelo contrato same-origin introduzido na versão 1.8.0
  (`0c3440e`), com a estratégia válida corrigida nesta recuperação. A proposta
  antiga cacheava `https://**/pet-artwork/**` como asset group sem TTL próprio;
  essa rota não é a rota pública entregue atualmente pela API. Restaurá-la não
  corrigiria o cache dos sprites atuais. O histórico original permanece no PR.
