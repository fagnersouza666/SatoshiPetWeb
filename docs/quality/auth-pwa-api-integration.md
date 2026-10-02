# Regressão HTTP de autenticação PWA/API

O gate executa os `AuthService`, `ApiClientService` e `SessionService` reais da PWA contra o servidor HTTP Quarkus, com migrações e banco H2 efêmero. Não usa `HttpTestingController` nem respostas HTTP simuladas. É complementar aos testes unitários e aos testes de API/PostgreSQL já existentes.

## Execução local

Pré-requisitos: Java 25, a versão de Node do `.nvmrc` e dependências instaladas com `npm --prefix apps/pwa ci`.

```sh
npm run test:auth:integration
```

O script aceita argumentos adicionais do Maven, por exemplo `npm run test:auth:integration -- -o -Dmaven.repo.local=/caminho/cache`. Não exige Docker, contas externas, envio de e-mail ou fundos. O mailer e os agendadores usam a configuração de testes da API. O endereço Bitcoin determinístico é apenas dado de validação, sem chamadas a provedores.

O `PwaAuthContractTest` cria magic links sintéticos no banco e inicia um processo Node que compila os serviços Angular de produção com esbuild. As fixtures temporárias são removidas ao final. O teste é opt-in (`-Dpwa.auth.integration=true`) para manter os testes isolados da API executáveis sem dependências Node.

## Cenários bloqueantes

1. Prefixo `/api` nos ambientes de desenvolvimento/produção e solicitação de magic link (202)
2. Verificação de novo usuário, cadastro com token e `bitcoinAddress`, leitura real de `/account/me` com `ngsw-bypass=true` e `cache: no-store`, mapeamento de endereço e rejeição de token consumido
3. Token inválido sem sessão ou navegação indevida
4. Login existente, leitura da conta, consumo do token e uso do CSRF recebido
5. Rejeição de CSRF ausente, inválido ou enviado no header incorreto; sucesso pelo `ApiClientService`
6. Recuperação com código gerado pela API, nova sessão, revogação da antiga e rejeição de código usado/inválido
7. Logout efetivo no servidor, cookie expirado e repetição com 401
8. Logout rejeitado com 403 preserva sessão, cache e navegação; nova tentativa funciona
9. Timeout de uma conexão HTTP real no logout preserva sessão/cache e permite nova tentativa

A etapa `Executar contrato real de autenticação PWA/API` faz parte de `verificar-api`. A falha bloqueia os gates existentes e o build de imagens. `npm run test:ci` também verifica que essa etapa não se torne apenas informativa.

## Limites intencionais

O adaptador `HttpBackend` só transporta as requisições produzidas pelos serviços para HTTP usando `fetch`, guarda os cookies de uma única origem e converte respostas reais em respostas Angular. Router e limpeza de cache são substituídos por observadores para validar as decisões do serviço. Os testes não simulam renderização, navegação do browser, políticas SameSite/CORS, Service Worker ou cache do browser; esses itens continuam sob os testes unitários e a QA de navegador. H2 também não substitui a integração existente com PostgreSQL.

## Regressões da interface e do estado de sessão

Os testes Angular também cobrem DTOs e payloads via `HttpTestingController`, token de cadastro somente em memória, código recusado, limpeza do CSRF, falhas de logout (403, 500 e rede), cliques repetidos e acesso ao cadastro depois de uma interrupção sem token. Falha temporária na leitura de `/account/me` depois de autenticação confirmada mantém apenas a conclusão pendente em memória: uma nova tentativa repete a leitura, sem reenviar um magic-link/código já consumido. A verificação mostra um botão de nova tentativa, e a recuperação diferencia indisponibilidade de código inválido.

A leitura de identidade usa `ngsw-bypass=true` e `cache: no-store`, evitando que o fallback offline do Service Worker autentique localmente os dados de uma conta anterior. Assim que uma nova sessão é confirmada no servidor, os dados anteriores são removidos do `SessionService`. Os testes de URL também verificam os ambientes de desenvolvimento e produção do WebSocket, ambos com um único prefixo `/api`.

A correção mantém o comportamento atual da API de recuperação: código válido inicia sessão e revoga as antigas. Não implementa a ampliação do fluxo CC-02/CA-006 (verificar novo e-mail e emitir novo código), nem a restauração automática de sessão após recarregar a PWA. O estado temporário não é persistido em storage. Uma recarga ainda requer novo magic-link; isso não deve ser apresentado como jornada completa do produto.
