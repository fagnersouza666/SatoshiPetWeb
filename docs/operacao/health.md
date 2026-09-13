# Health checks da API

A API usa a extensão SmallRye Health do Quarkus. O endpoint agregado é
`/q/health` e as sondas especializadas ficam em `/q/health/live`,
`/q/health/ready` e `/q/health/started`.

## Contrato

As respostas são JSON com o status geral e, quando aplicável, a lista de
checks registrados pelas extensões do Quarkus:

```json
{
  "status": "UP",
  "checks": [
    { "name": "...", "status": "UP" }
  ]
}
```

`/q/health/live` indica que o processo está vivo. `/q/health/ready` também
considera dependências registradas pelo runtime, como o datasource. O endpoint
agregado permite uma consulta única para sondas e operação.

O campo `status` pode ser `UP` ou `DOWN`; sondas devem considerar qualquer
resposta HTTP diferente de `200` ou status diferente de `UP` como indisponível.
Quando uma dependência falha, a resposta preserva o status de falha sem
retornar URL JDBC, usuário, senha, segredo ou detalhes de exceção
(`quarkus.smallrye-health.include-problem-details=false`).

## Acesso e operação

As rotas ficam fora do rate limit para que sondas não sejam bloqueadas. Em
produção, o Caddy restringe `/q/health` à rede interna; a borda pública não
deve publicar esses endpoints.

Exemplo de verificação local:

```bash
curl --fail http://localhost:8080/q/health
curl --fail http://localhost:8080/q/health/ready
```
