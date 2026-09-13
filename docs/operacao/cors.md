# Origens permitidas pela API

## Política

A API habilita o filtro CORS do Quarkus e aceita somente as origens listadas em
`CORS_ALLOWED_ORIGINS`, separadas por vírgula. O valor deve conter URLs
completas, incluindo esquema e porta quando aplicável. Não usar `*`: a sessão
da aplicação usa cookies e não deve ser compartilhada com origens arbitrárias.

Em desenvolvimento e nos testes, o padrão é `http://localhost:4200` e
`https://localhost`, que correspondem ao acesso direto à PWA e ao Caddy local.
Em staging e produção, configure a origem pública da PWA no ambiente da
implantação.

O filtro permite apenas os métodos e headers necessários à API, expõe os
headers `X-CSRF-Token` e `X-Correlation-Id` ao navegador e mantém credenciais
habilitadas para a sessão por cookie. Origens não autorizadas recebem `403`
tanto em requisições CORS normais quanto em preflight `OPTIONS`.

## Verificação

```bash
ORIGIN='https://atacante.example'
curl -i -H "Origin: ${ORIGIN}" http://localhost:8080/api/v1/hello

curl -i -X OPTIONS http://localhost:8080/api/v1/hello \
  -H "Origin: ${ORIGIN}" \
  -H 'Access-Control-Request-Method: GET'
```

Os dois comandos devem retornar `403` sem `Access-Control-Allow-Origin`.
O cenário permitido é coberto por `CorsOriginTest`.
