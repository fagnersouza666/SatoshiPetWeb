# Correlação operacional

A API aceita o header `X-Correlation-Id`. Quando o valor é seguro e tem até
36 caracteres, ele é preservado; caso contrário, a API gera um UUID opaco e
devolve o valor efetivo no mesmo header da resposta.

O ID acompanha o MDC durante a requisição, o ciclo de jobs, a conexão
WebSocket e a publicação do outbox. O formato de log inclui
`correlationId=<valor>` para permitir a busca da cadeia operacional sem
registrar payloads, tokens ou outros dados privados.

Eventos persistidos no outbox e envelopes públicos Bitcoin recebem o mesmo ID
da operação que os produziu. Operações iniciadas fora de uma requisição
recebem um novo UUID; o valor não deve conter e-mail, endereço ou outro dado
pessoal.
