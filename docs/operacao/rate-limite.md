# Rate limit da API

O filtro `RateLimitFilter` limita requisições em uma janela fixa mantida no
processo da API:

| Tipo de requisição | Chave | Padrão |
|--------------------|-------|--------|
| Sem sessão válida | IP do cliente | 60 requisições por 1 minuto |
| Com sessão válida | UUID da conta | 300 requisições por 1 minuto |

O filtro executa depois da autenticação da sessão. Assim, uma sessão válida
usa o limite da conta; um cookie ausente, inválido ou expirado usa o limite do
IP. O IP vem do primeiro hop de `X-Forwarded-For` quando o proxy confiável o
fornece e, sem proxy, do endereço remoto da conexão.

As sondas `/q/health` e `/q/metrics` ficam fora do limite para que a
observabilidade não seja interrompida por tráfego de usuários. Quando o limite
é atingido, a API responde `429` com JSON `{ "code": "rate_limited", ... }` e
`Retry-After` em segundos.

## Configuração

As propriedades podem ser alteradas por ambiente:

| Propriedade | Variável | Padrão |
|-------------|----------|--------|
| `satoshi-pet.rate-limit.ip-limit` | `RATE_LIMIT_IP` | `60` |
| `satoshi-pet.rate-limit.account-limit` | `RATE_LIMIT_ACCOUNT` | `300` |
| `satoshi-pet.rate-limit.window` | `RATE_LIMIT_WINDOW` | `PT1M` |

O contador é local ao processo. Em uma implantação com múltiplas réplicas, a
política deve ser mantida em um armazenamento compartilhado antes de se
considerar o limite global.
