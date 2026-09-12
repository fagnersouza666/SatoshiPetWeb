#!/usr/bin/env bash
#
# Aceitação local da PWA: versao.sh verificar e testes Angular (Vitest).
# Falha ao primeiro erro.
#
set -euo pipefail

RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

# Antes dos testes: PWA e API precisam estar na mesma versão. A conferência
# é barata e vem primeiro porque uma divergência aqui não aparece em teste
# nenhum — foi assim que o projeto já chegou a ter três versões diferentes.
"$RAIZ/infra/scripts/versao.sh" verificar

cd "$RAIZ"

echo "==> Verificando a PWA em $RAIZ/apps/pwa"
npm run test:pwa
echo "==> PWA verificada com sucesso."
