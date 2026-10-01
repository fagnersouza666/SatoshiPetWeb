# Cache da PWA

Referências: FUND-11, PRD §14.6, CA-067.

## Shell e arquivos visuais

O service worker faz prefetch do shell, de todos os ícones publicados em
`apps/pwa/public/icons` e do ovo padrão. Os demais arquivos estáticos são
carregados sob demanda. O contrato `npm run test:ngsw` valida o shell e
a inclusão de cada ícone; ele também participa de `npm test` e `check:pwa`.
