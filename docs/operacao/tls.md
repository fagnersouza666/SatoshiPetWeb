# TLS por ambiente

## Decisão de implantação

O TLS termina no proxy reverso ou ingress à frente dos containers. A API e a
PWA permanecem em HTTP somente na rede privada dos containers; o certificado e
a chave privada são responsabilidade da plataforma de implantação. Assim,
nenhum certificado real, chave ou segredo é armazenado no repositório ou
copiado para as imagens.

Os perfis da API são:

| Ambiente | Perfil Quarkus | Configuração |
| --- | --- | --- |
| Desenvolvimento local | `dev` (padrão) | HTTP habilitado para `docker compose` |
| Staging | `staging` | HTTP interno habilitado, exige proxy HTTPS e aceita `X-Forwarded-*` somente do proxy confiável |
| Produção | `prod` | HTTP interno habilitado, exige proxy HTTPS e aceita `X-Forwarded-*` somente do proxy confiável |

Ative o perfil com `QUARKUS_PROFILE`. Os exemplos não secretos estão em
[`infra/tls/staging.env.example`](../../infra/tls/staging.env.example) e
[`infra/tls/production.env.example`](../../infra/tls/production.env.example).

## Contrato do proxy/ingress

Para staging e produção, a borda deve:

- escutar HTTPS na porta pública e redirecionar HTTP para HTTPS;
- obter o certificado de uma secret store ou do recurso de secrets do
  orquestrador, montando-o apenas no proxy;
- remover `Forwarded` e todos os cabeçalhos `X-Forwarded-*` recebidos do
  cliente;
- definir `X-Forwarded-Proto`, `X-Forwarded-Host` e `X-Forwarded-For` com os
  valores observados na borda;
- encaminhar a API somente pela rede privada e a partir de um endereço incluído
  em `QUARKUS_HTTP_PROXY_TRUSTED_PROXIES`.

O perfil usa `X-Forwarded-*` (e não o cabeçalho RFC `Forwarded`) para manter um
contrato explícito com proxies comuns. O endereço confiável deve ser um IP,
uma lista de IPs ou CIDRs privados efetivamente usados pela borda; uma rede
ampla como `0.0.0.0/0` invalida a proteção contra falsificação de cabeçalhos.

`insecure-requests=enabled` é intencional: a API não abre um listener TLS nem
recebe material de certificado, porque o proxy é o único terminador TLS. Usar
`redirect` no Quarkus sem habilitar um listener TLS local faria o container
falhar na inicialização. O redirecionamento de HTTP público é responsabilidade
da borda.

## Rotação e verificação

A rotação deve atualizar o secret no proxy e recarregar a configuração conforme
o procedimento da plataforma, sem commit. Depois da publicação, verificar:

1. `QUARKUS_PROFILE` corresponde ao ambiente;
2. `QUARKUS_HTTP_PROXY_TRUSTED_PROXIES` contém somente a borda privada;
3. acesso público por HTTPS chega à aplicação com `X-Forwarded-Proto: https`;
4. acesso HTTP público é redirecionado para HTTPS pela borda;
5. uma requisição direta com `X-Forwarded-Proto` forjado não altera o esquema
   reconhecido pela API.
