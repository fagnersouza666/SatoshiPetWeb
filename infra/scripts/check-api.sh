#!/usr/bin/env bash
#
# Verificação da API: versão alinhada, compilação, testes unitários e
# testes de integração com Testcontainers quando habilitados.
#
set -euo pipefail

RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

# PWA e API precisam estar na mesma versão — ver o cabeçalho de versao.sh.
# Vem antes do Docker porque falha em um segundo e não exige nada instalado.
"$RAIZ/infra/scripts/versao.sh" verificar

cd "$RAIZ/services/api"

if ! docker info >/dev/null 2>&1; then
    echo "ERRO: o Docker precisa estar rodando — os testes de integração usam Testcontainers." >&2
    exit 1
fi

echo "==> Verificando a API em $RAIZ/services/api"
./mvnw --batch-mode verify
echo "==> API verificada com sucesso."
