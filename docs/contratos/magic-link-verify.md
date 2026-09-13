# Contrato de verificação de magic link

Define a etapa posterior à solicitação descrita em [magic-link.md](./magic-link.md).
Referências: PRD §4.1, CONTA-01, CC-01.

## Endpoint

`POST /api/v1/auth/magic-link/verify`

### Request — `application/json`

```json
{
  "token": "token-bruto-recebido-no-link"
}
```

Regras:

- `token` é obrigatório e não pode ser vazio;
- propriedades adicionais não fazem parte do contrato;
- o token nunca é repetido em respostas de erro.

### Sucesso — conta existente — `200 OK`

Cookie `sp_session` (HttpOnly, SameSite=Strict, path `/`) e header `X-CSRF-Token`
com o token CSRF bruto da sessão recém-criada.

```json
{
  "status": "ok",
  "registrationRequired": false,
  "verifiedEmail": "pessoa@example.com"
}
```

O token de magic link é **consumido** nesta resposta.

### Sucesso — nova conta — `200 OK`

Sem cookie de sessão. O token **não** é consumido; o cliente deve reutilizá-lo
em `POST /api/v1/auth/register`.

```json
{
  "status": "ok",
  "registrationRequired": true,
  "verifiedEmail": "pessoa@example.com"
}
```

### Erro opaco — `401 Unauthorized`

```json
{
  "code": "unauthorized",
  "message": "Acesso não autorizado."
}
```

Usado para token ausente, inválido, expirado, já consumido ou corrida entre
requisições. A resposta não distingue o motivo (anti-enumeração).

## Decisões de implementação

- TTL e formato do token: ver `MagicLinkTokenService` e configuração
  `satoshi.magic-link.*`.
- Consumo único do token na verificação de conta existente e no registro.
- IP de origem obtido de `X-Forwarded-For` (primeiro hop) quando presente.
