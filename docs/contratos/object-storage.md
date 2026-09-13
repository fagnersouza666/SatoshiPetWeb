# Configuração do object storage local

Referências: PRD §8.2 e §16.1, FUND-03, ADR-002.

## Escopo

O ambiente local usa [MinIO](https://min.io/) como implementação S3-compatível
para os assets do pet. A API acessa o storage por meio de
`ObjectStoragePort`/`MinioObjectStorage`; a PWA não recebe as credenciais do
storage.

O Compose cria os buckets de forma idempotente quando o serviço `minio-init`
termina sua inicialização. A criação não apaga objetos existentes.

## Endpoints e buckets

| Recurso | Acesso a partir do host | Acesso entre containers | Observação |
| --- | --- | --- | --- |
| API S3 do MinIO | `http://localhost:9000` | `http://minio:9000` | Endpoint usado pela API no Compose |
| Console do MinIO | `http://localhost:9001` | — | Interface administrativa local |
| Bucket principal | `pet-artwork` | — | Assets aprovados; usado pelo adaptador |
| Bucket de staging | `pet-artwork-staging` | — | Pré-visualizações; bootstrap libera download anônimo |

As portas do host são configuráveis por `MINIO_API_PORT` e
`MINIO_CONSOLE_PORT`. O volume Docker `minio-data` preserva os objetos entre
reinícios e recriações dos containers.

## Variáveis de configuração

### API

Estas variáveis são lidas pelo adaptador `MinioObjectStorage`:

| Variável | Uso | Padrão local |
| --- | --- | --- |
| `MINIO_ENDPOINT` | Endpoint HTTP do servidor S3 | `http://localhost:9000` fora do Compose; `http://minio:9000` no Compose |
| `MINIO_ACCESS_KEY` | Usuário/identificador da credencial | fornecido pelo Compose a partir de `MINIO_ROOT_USER` |
| `MINIO_SECRET_KEY` | Segredo da credencial | fornecido pelo Compose a partir de `MINIO_ROOT_PASSWORD` |
| `MINIO_BUCKET` | Bucket principal | `pet-artwork` |
| `MINIO_STAGING_BUCKET` | Bucket de staging | `pet-artwork-staging` |

### Serviço MinIO e portas locais

Estas variáveis são consumidas pelo Compose e pelo bootstrap dos buckets:

| Variável | Uso | Padrão local |
| --- | --- | --- |
| `MINIO_ROOT_USER` | Usuário administrativo do MinIO | definido no `.env.example` |
| `MINIO_ROOT_PASSWORD` | Senha administrativa do MinIO | definida no `.env.example` |
| `MINIO_API_PORT` | Porta da API S3 publicada no host | `9000` |
| `MINIO_CONSOLE_PORT` | Porta do console publicada no host | `9001` |
| `MINIO_BUCKET` | Nome do bucket principal criado no bootstrap | `pet-artwork` |
| `MINIO_STAGING_BUCKET` | Nome do bucket de staging criado no bootstrap | `pet-artwork-staging` |

O Compose encaminha `MINIO_ROOT_USER`/`MINIO_ROOT_PASSWORD` para
`MINIO_ACCESS_KEY`/`MINIO_SECRET_KEY` da API. Por isso, uma alteração das
credenciais no `.env` vale para os dois containers.

Não registre valores reais de credenciais neste documento, em commits ou em
logs. Use `.env.example` apenas como modelo para criar o `.env` local; o `.env`
não deve ser commitado. Em ambientes compartilhados ou de produção, use
credenciais próprias e não os valores de desenvolvimento.

## Inicialização local

Na raiz do repositório:

```bash
cp .env.example .env
docker compose -f infra/docker-compose.yml up --build
```

Depois que o healthcheck do MinIO passar, `minio-init` verifica/cria os dois
buckets e encerra com sucesso. A API usa `http://minio:9000` dentro da rede
Docker; não configure `localhost` para a API quando ela estiver em container.

Para executar a API diretamente no host, use `MINIO_ENDPOINT=http://localhost:9000`
e forneça `MINIO_ACCESS_KEY`/`MINIO_SECRET_KEY` correspondentes ao `.env` do
MinIO.

Verificações rápidas, sem expor credenciais:

```bash
docker compose -f infra/docker-compose.yml config --quiet
curl --fail http://localhost:9000/minio/health/live
docker compose -f infra/docker-compose.yml ps minio minio-init
docker compose -f infra/docker-compose.yml logs minio-init
```

Para apagar também os objetos persistidos e recriar o ambiente do zero:

```bash
docker compose -f infra/docker-compose.yml down -v
```

Esse último comando remove o volume local `minio-data`.
