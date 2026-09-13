# Métricas operacionais da API

A API expõe métricas no formato Prometheus/OpenMetrics em `/q/metrics`. O
endpoint é fornecido pelo Micrometer e usa somente métricas operacionais do
processo e do servidor HTTP; não deve receber dados privados, cookies ou
segredos.

## Acesso por ambiente

| Ambiente | URL | Acesso |
| --- | --- | --- |
| API direta local | `http://localhost:8080/q/metrics` | Desenvolvimento e testes locais |
| Caddy local | `https://localhost/q/metrics` | Proxy local, com o certificado de desenvolvimento |
| Produção | `https://<domínio>/q/metrics` | Somente redes internas permitidas pelo Caddy |

Em produção, o Caddy aplica a mesma restrição de redes internas usada por
`/q/health`. O endpoint não substitui autenticação da aplicação: sua proteção
é de rede e deve ser complementada pela política de firewall ou ingress da
implantação.

## Verificação

Com a API em execução, a resposta deve ser `200`, ter content type
`application/openmetrics-text` e conter as diretivas `# HELP` e `# TYPE`:

```bash
curl --fail http://localhost:8080/q/metrics | head
```

As sondas de `/q/metrics` ficam fora do rate limit da API para que a coleta não
seja interrompida pelo tráfego de usuários.
