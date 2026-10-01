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
