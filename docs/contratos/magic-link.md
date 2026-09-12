# Contrato de solicitação de magic link

Este documento define o contrato público para iniciar o acesso por e-mail,
conforme PRD §4.1 e CC-01. A verificação do link e a persistência do token são
etapas posteriores do fluxo.

## Endpoint

`POST /api/v1/auth/magic-link`

O endpoint recebe somente o e-mail. Endereço Bitcoin e nome do pet pertencem à
etapa posterior à verificação, e não são aceitos nesta solicitação.

### Request — `application/json`

```json
{
  "email": "pessoa@example.com"
}
```

Regras do payload:

- `email` é obrigatório, não pode ser vazio e deve ter formato de e-mail;
- o tamanho máximo do endereço completo é 254 caracteres;
- propriedades adicionais não fazem parte do contrato;
- o valor do e-mail nunca é repetido em uma resposta de erro.

### Sucesso — `202 Accepted`

```json
{
  "status": "accepted",
  "message": "Se o e-mail informado puder ser usado, você receberá um link para continuar."
}
```

A resposta é uniforme para e-mail novo ou já associado, não permitindo
enumeração de contas. Ela não inclui token, URL, `expiresAt`, TTL, fornecedor de
e-mail ou outro segredo. `202` significa que a solicitação foi aceita para
processamento; não é uma confirmação de entrega.

## Erros

Todos os erros usam JSON com `code` e `message`. O campo opcional `fieldErrors`
é enviado somente em `invalid_request`:

```json
{
  "code": "invalid_request",
  "message": "A solicitação contém dados inválidos.",
  "fieldErrors": [
    {
      "field": "email",
      "code": "invalid_format",
      "message": "Informe um e-mail válido."
    }
  ]
}
```

| HTTP | `code` | Uso |
| --- | --- | --- |
| `400` | `invalid_request` | JSON ausente/malformado ou e-mail ausente, inválido ou longo demais. |
| `429` | `rate_limited` | Limite de frequência atingido; a política de limite e retry permanece configurável. |
| `503` | `request_unavailable` | A solicitação não pôde ser colocada em processamento. |
| `500` | `internal_error` | Falha inesperada, sem detalhes internos. |

Mensagens e códigos não informam se uma conta existe. Logs e respostas não
contêm token, segredo, conteúdo do link, prompts ou credenciais de provedor.

## Decisões deliberadamente pendentes

Este contrato não escolhe fornecedor de e-mail, não fixa TTL, formato ou
tamanho do token, nem define limiares de rate limiting. Essas decisões devem
ser aplicadas pela implementação sem alterar o payload público. O token deve
ser armazenado somente como verificador seguro e consumido uma única vez,
conforme CONTA-01 e CC-01.
