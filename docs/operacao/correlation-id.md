# Correlação operacional

A API aceita o header `X-Correlation-Id`. Quando o valor é seguro e tem até
36 caracteres, ele é preservado; caso contrário, a API gera um UUID opaco e
devolve o valor efetivo no mesmo header da resposta.

O ID acompanha o MDC durante a requisição, o ciclo de jobs, a conexão
WebSocket e a publicação do outbox. O log JSON mantém o campo `mdc.correlationId` para permitir a busca
da cadeia operacional sem
registrar payloads, tokens ou outros dados privados.

Eventos persistidos no outbox e envelopes públicos Bitcoin recebem o mesmo ID
da operação que os produziu. Operações iniciadas fora de uma requisição
recebem um novo UUID; o valor não deve conter e-mail, endereço ou outro dado
pessoal.

O canal privado continua validando a sessão da conta antes de enviar snapshots
ou aceitar mensagens. A correlação não substitui a autorização e seu contexto
é restaurado ao encerrar cada callback, inclusive em rejeições.

Nos jobs de monitoramento Bitcoin, tique do pet e geração de arte, um novo
ID abrange a tentativa de adquirir a trava, o processamento, o registro de
erros e a liberação. Ciclos sem trava também têm um ID próprio. Ao terminar,
o contexto anterior é restaurado, inclusive se a aquisição, o processamento
ou a liberação falharem.

Mensagens JSON inválidas no WebSocket de endereço geram aviso com o ID da
conexão e o MDC correspondente; o conteúdo bruto recebido não é registrado.
