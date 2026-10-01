# MinIO real nos testes da API

`MinioObjectStorageTest` continua validando upload/leitura e isolamento entre
arte aprovada e staging com um servidor MinIO real. Porta, credenciais efêmeras,
healthcheck e assertivas permanecem iguais. Não há alteração no serviço de
produção, no Compose, em permissões ou nos gates da CI.

## Imagem reproduzível de teste

A imagem antiga do Quay deixou de estar disponível para acesso público: o
pull da CI retornou `unauthorized` antes de iniciar os testes. O repositório
[oficial do MinIO](https://github.com/minio/minio#source-only-distribution)
orienta a distribuição Community por código-fonte.

O Testcontainers agora constrói uma imagem local usando a receita versionada
`services/api/src/test/resources/minio/Dockerfile`. A receita:

- Obtém somente o repositório oficial `https://github.com/minio/minio.git`
- Fixa e confere o commit `07c3a429bfed433e49018cb0f78a52145d4bedeb`, da mesma
  [release setembro](https://github.com/minio/minio/releases/tag/RELEASE.2025-09-07T16-13-09Z)
  que já era usada pelo teste
- Usa o compilador oficial `golang:1.24.6-bookworm`, com digest imutável; a
  versão é compatível com o `go.mod` desse fonte (`go 1.24.0`, toolchain 1.24.2)
- Desativa troca automática de toolchain e CGO, verifica os módulos pelo
  `go.sum`, compila em modo readonly e usa o gerador de metadados do upstream
- Mantém SHA/release nos labels, além de LICENSE e CREDITS, no runtime scratch
- Não acessa imagens MinIO remotas, binários legados, credenciais de registry
  nem mirrors de terceiros

A primeira execução com Docker precisa obter a imagem oficial Go, o fonte e
seus módulos públicos. As camadas de download e compilação podem ser
reaproveitadas pelo cache Docker existente. A imagem final de teste é removida
pelo Testcontainers ao encerrar a execução.

`MinioTestImageContractTest` valida os pins, proveniência, verificação de módulos
e runtime sem precisar de Docker. O teste do storage ainda exige Docker e o
build real continua obrigatório quando esse ambiente está disponível.

## Pendência de atualização separada

Esta correção preserva deliberadamente a release testada. A release seguinte,
`RELEASE.2025-10-15T17-29-55Z`, corrige a
[CVE-2025-62506](https://github.com/minio/minio/security/advisories/GHSA-jjjj-jwhf-8rgr)
(escalada de privilégios em contas de serviço/STS). Avaliar essa atualização e
a situação de manutenção da Community Edition separadamente, sobretudo antes
de qualquer uso de produção. A imagem construída aqui é exclusiva dos testes.
