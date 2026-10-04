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
| Desenvolvimento com `quarkus:dev` | `dev` | HTTP direto habilitado |
| Compose local (imagem fast-jar) | `prod` | HTTP interno, proxies `caddy,pwa`, cookies HTTP permitidos explicitamente pelo Compose |
| Staging | `staging` | HTTP interno habilitado, exige proxy HTTPS e aceita `X-Forwarded-*` somente do proxy confiável |
| Produção | `prod` | HTTP interno habilitado, exige proxy HTTPS e aceita `X-Forwarded-*` somente do proxy confiável |

O perfil Compose `--profile dev` seleciona serviços locais; ele não altera o
perfil Quarkus do JAR empacotado. Ative outro perfil da API com `QUARKUS_PROFILE`.
Os exemplos não secretos estão em
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

O Compose repassa `QUARKUS_HTTP_PROXY_TRUSTED_PROXIES` à API. Produção exige
valor explícito; na rede Compose, `caddy` identifica o proxy e o Quarkus resolve
seu endereço via DNS. IPs/CIDRs efetivos são preferíveis em instalações que
mantêm a rede estável. O nginx direto de desenvolvimento é `pwa`.

O perfil usa `X-Forwarded-*` (e não o cabeçalho RFC `Forwarded`) para manter um
contrato explícito com proxies comuns. O endereço confiável deve ser um IP,
uma lista de IPs ou CIDRs privados efetivamente usados pela borda; uma rede
ampla como `0.0.0.0/0` invalida a proteção contra falsificação de cabeçalhos.

O Caddy expande `{$DOMAIN}` antes de interpretar endereços e opções globais.
Ambos os Caddyfiles removem headers encaminhados fornecidos pelo cliente antes
de definir a identidade observada. No nginx direto, `/api/` e `/api/ws/` usam
prefixos protegidos contra as expressões regulares de assets estáticos, e o
segundo configura o upgrade WebSocket.

Cookies de sessão têm `Secure` em produção; o Compose local define
`SESSION_COOKIE_SECURE=false` para permitir o acesso HTTP documentado. Os
magic links de produção usam `https://${DOMAIN}/entrar/verificar`; o Compose
local permite `MAGIC_LINK_BASE_URL`, com padrão `http://localhost:4200`.

## Configuração e persistência do Compose

Desenvolvimento: `docker compose -f infra/docker-compose.yml --profile dev up --build`.
Produção: combinar `infra/docker-compose.yml` e `infra/docker-compose.prod.yml`
sem ativar `dev`. PostgreSQL, API, PWA e Caddy compõem a implantação; Mailpit,
Bitcoin Core regtest, MinIO local e seu inicializador pertencem somente ao
perfil de desenvolvimento. Somente Caddy publica portas em produção.

PostgreSQL 18 monta `postgres-data` em `/var/lib/postgresql`, contendo o
subdiretório versionado `18/docker`. Em uma instalação antiga, inspecionar o
layout do volume e fazer backup/restauração ou `pg_upgrade` compatível antes
de mudar a montagem; alterar o caminho não migra dados existentes. Nunca
usar `down -v` como procedimento de atualização.

Produção requer `OBJECT_STORAGE_ENDPOINT` HTTPS, `OBJECT_STORAGE_BUCKET`,
`OBJECT_STORAGE_STAGING_BUCKET`, `AWS_ACCESS_KEY_ID` e `AWS_SECRET_ACCESS_KEY`.
O Compose os mapeia para `MINIO_*`, os nomes consumidos pelo adaptador
`MinioObjectStorage`, também compatível com provedores externos S3. Provisionar
ambos os buckets privados e permissões de leitura, escrita e cópia; o adaptador
verifica os buckets ao iniciar. A API de produção não depende do MinIO local.

`node infra/scripts/deployment-config.test.mjs` usa o parser do Docker Compose
com valores fictícios para verificar as duas composições e os contratos dos
proxies. Exige o CLI Docker Compose, mas não um daemon. Complementar no ambiente
de implantação com `caddy adapt --config /etc/caddy/Caddyfile --validate`,
`nginx -t`, handshake WS, leitura de PNG e upload/leitura no storage de teste.

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
