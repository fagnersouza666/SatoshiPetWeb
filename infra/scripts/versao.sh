#!/usr/bin/env bash
#
# Lê e incrementa a versão do produto — PWA e API juntos.
#
# Por que este script existe
# --------------------------
# A versão vive em três arquivos (package.json da raiz, apps/pwa/package.json
# e services/api/pom.xml) e precisa ser a mesma nos três: PWA e API sobem
# juntos, e um número que identifica só metade do produto não serve para nada
# num relato de problema.
#
# Bumpar à mão é justamente como o projeto chegou a ter versões diferentes
# (raiz 0.1.0, PWA 0.0.0 e API 0.1.0-SNAPSHOT). Este script cuida dos três
# de uma vez. O POM pode usar o sufixo -SNAPSHOT; a versão de produto é X.Y.Z.
#
# Uso
# ---
#   versao.sh atual                 mostra as versões e avisa se divergirem
#   versao.sh verificar             falha se divergirem (roda nas verificações)
#   versao.sh corrigir              1.0.3 -> 1.0.4   (correção de problema)
#   versao.sh funcionalidade        1.0.3 -> 1.1.0   (funcionalidade nova)
#   versao.sh grande                1.4.2 -> 2.0.0   (só quando o Mestre pedir)
#
set -euo pipefail

raiz="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
npm_raiz="$raiz/package.json"
npm_pwa="$raiz/apps/pwa/package.json"
pom="$raiz/services/api/pom.xml"

for arquivo in "$npm_raiz" "$npm_pwa" "$pom"; do
    if [[ ! -f "$arquivo" ]]; then
        echo "ERRO: arquivo não encontrado: $arquivo" >&2
        exit 1
    fi
done

# `"version": "1.0.0"` -> `1.0.0` (primeira ocorrência). awk + exit evita
# SIGPIPE do `sed | head` com `set -o pipefail`.
versao_do_npm() {
    awk '
        /"version"/ {
            if (match($0, /"version"[[:space:]]*:[[:space:]]*"[^"]+"/)) {
                val = substr($0, RSTART, RLENGTH)
                sub(/"version"[[:space:]]*:[[:space:]]*"/, "", val)
                sub(/"$/, "", val)
                print val
                exit
            }
        }
    ' "$1"
}

# Primeiro <version>X.Y.Z(-SNAPSHOT)? do pom — o do projeto, antes de dependências.
versao_bruta_do_backend() {
    awk '
        match($0, /<version>[0-9]+\.[0-9]+\.[0-9]+(-SNAPSHOT)?<\/version>/) {
            val = substr($0, RSTART, RLENGTH)
            sub(/^<version>/, "", val)
            sub(/<\/version>$/, "", val)
            print val
            exit
        }
    ' "$pom"
}

# `0.1.0-SNAPSHOT` -> `0.1.0`
versao_do_backend() {
    local bruta
    bruta="$(versao_bruta_do_backend)"
    echo "${bruta%-SNAPSHOT}"
}

exigir_semver() {
    if [[ ! "$1" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
        echo "ERRO: versão '$1' não está no formato maior.menor.correção" >&2
        exit 1
    fi
}

versoes_alinhadas() {
    local raiz_v pwa_v api_v
    raiz_v="$(versao_do_npm "$npm_raiz")"
    pwa_v="$(versao_do_npm "$npm_pwa")"
    api_v="$(versao_do_backend)"
    [[ "$raiz_v" == "$pwa_v" && "$pwa_v" == "$api_v" ]]
}

mostrar() {
    printf '  %-42s %s\n' "workspace (package.json)" "$(versao_do_npm "$npm_raiz")"
    printf '  %-42s %s\n' "PWA (apps/pwa/package.json)" "$(versao_do_npm "$npm_pwa")"
    printf '  %-42s %s\n' "API (services/api/pom.xml)" "$(versao_bruta_do_backend)"
    versoes_alinhadas
}

substituir_arquivo() {
    local origem="$1"
    local destino="$2"
    local tmp
    tmp="$(mktemp)"
    cat "$origem" > "$tmp"
    mv "$tmp" "$destino"
}

gravar_npm() {
    local arquivo="$1"
    local nova="$2"
    local tmp
    tmp="$(mktemp)"
    awk -v nova="$nova" '
        !done && /"version"/ {
            sub(/"version"[[:space:]]*:[[:space:]]*"[^"]+"/, "\"version\": \"" nova "\"")
            done = 1
        }
        { print }
    ' "$arquivo" > "$tmp"
    substituir_arquivo "$tmp" "$arquivo"
    rm -f "$tmp"
}

gravar_pom() {
    local nova="$1"
    local tmp
    tmp="$(mktemp)"
    awk -v nova="$nova" '
        !done && /<version>[0-9]+\.[0-9]+\.[0-9]+(-SNAPSHOT)?<\/version>/ {
            sub(/<version>[^<]+<\/version>/, "<version>" nova "-SNAPSHOT</version>")
            done = 1
        }
        { print }
    ' "$pom" > "$tmp"
    substituir_arquivo "$tmp" "$pom"
    rm -f "$tmp"
}

case "${1:-atual}" in
    atual)
        echo "Versão do produto:"
        if mostrar; then
            echo
            echo "==> PWA e API estão na mesma versão."
        else
            echo
            echo "AVISO: PWA e API estão em versões diferentes." >&2
        fi
        ;;

    verificar)
        if versoes_alinhadas; then
            echo "==> Versão coerente entre PWA e API: $(versao_do_npm "$npm_raiz")"
        else
            echo "FALHA: raiz ($(versao_do_npm "$npm_raiz")), PWA ($(versao_do_npm "$npm_pwa")) e API ($(versao_do_backend)) divergem." >&2
            echo "       Os três sobem juntos — use ./infra/scripts/versao.sh para incrementar." >&2
            mostrar >&2
            exit 1
        fi
        ;;

    corrigir | funcionalidade | grande)
        atual_raiz="$(versao_do_npm "$npm_raiz")"
        atual_pwa="$(versao_do_npm "$npm_pwa")"
        atual_api="$(versao_do_backend)"
        exigir_semver "$atual_raiz"
        exigir_semver "$atual_pwa"
        exigir_semver "$atual_api"

        if [[ "$atual_raiz" != "$atual_pwa" || "$atual_pwa" != "$atual_api" ]]; then
            echo "ERRO: raiz ($atual_raiz), PWA ($atual_pwa) e API ($atual_api) já estão divergentes." >&2
            echo "      Alinhe os três à mão antes de incrementar, para não escolher por você." >&2
            exit 1
        fi

        IFS='.' read -r maior menor correcao <<< "$atual_raiz"
        case "$1" in
            corrigir)       correcao=$((correcao + 1)) ;;
            funcionalidade) menor=$((menor + 1)); correcao=0 ;;
            grande)         maior=$((maior + 1)); menor=0; correcao=0 ;;
        esac
        nova="$maior.$menor.$correcao"

        gravar_npm "$npm_raiz" "$nova"
        gravar_npm "$npm_pwa" "$nova"
        gravar_pom "$nova"

        echo "$atual_raiz -> $nova"
        echo
        mostrar || {
            echo "ERRO: a substituição deixou os arquivos divergentes — revise o diff." >&2
            exit 1
        }
        echo
        echo "Lembre de incluir a mudança de versão NO MESMO commit da alteração"
        echo "que a motivou: versão em commit separado desgarra do que ela descreve."
        ;;

    *)
        echo "Uso: versao.sh [atual|verificar|corrigir|funcionalidade|grande]" >&2
        exit 1
        ;;
esac
