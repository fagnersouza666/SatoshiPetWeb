#!/usr/bin/env bash
#
# Testes de infra/scripts/versao.sh — PWA, API e package.json da raiz juntos.
#
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
versao_sh="$script_dir/versao.sh"
falhas=0

falhar() {
    echo "FALHA: $*" >&2
    falhas=$((falhas + 1))
}

assert_eq() {
    local esperado="$1"
    local obtido="$2"
    local rotulo="$3"
    if [[ "$obtido" != "$esperado" ]]; then
        falhar "$rotulo: esperado '$esperado', obtido '$obtido'"
    fi
}

assert_exit() {
    local esperado="$1"
    local rotulo="$2"
    shift 2
    local codigo=0
    "$@" >/dev/null 2>&1 || codigo=$?
    if [[ "$codigo" -ne "$esperado" ]]; then
        falhar "$rotulo: esperado exit $esperado, obtido $codigo"
    fi
}

if [[ ! -f "$versao_sh" ]]; then
    echo "FALHA: $versao_sh não existe." >&2
    exit 1
fi

montar_arvore() {
    local dest="$1"
    local ver_npm="$2"
    local ver_pom="$3"
    mkdir -p "$dest/infra/scripts" "$dest/apps/pwa" "$dest/services/api"
    printf '%s\n' "{\"version\": \"$ver_npm\"}" > "$dest/package.json"
    printf '%s\n' "{\"version\": \"$ver_npm\"}" > "$dest/apps/pwa/package.json"
    cat > "$dest/services/api/pom.xml" <<EOF
<project>
    <artifactId>satoshi-pet-api</artifactId>
    <version>${ver_pom}</version>
    <properties>
        <compiler-plugin.version>3.15.0</compiler-plugin.version>
    </properties>
    <dependencyManagement>
        <dependencies>
            <dependency>
                <version>3.33.3.2</version>
            </dependency>
        </dependencies>
    </dependencyManagement>
</project>
EOF
    cp "$versao_sh" "$dest/infra/scripts/versao.sh"
    chmod +x "$dest/infra/scripts/versao.sh"
}

ler_npm() {
    sed -nE 's/.*"version"[[:space:]]*:[[:space:]]*"([^"]+)".*/\1/p' "$1" | head -1
}

ler_pom() {
    sed -nE 's/.*<version>([0-9]+\.[0-9]+\.[0-9]+(-SNAPSHOT)?)<\/version>.*/\1/p' "$1" | head -1
}

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

# verificar aceita 0.1.0 e 0.1.0-SNAPSHOT como a mesma versão de produto
montar_arvore "$tmp/ok" "1.2.3" "1.2.3-SNAPSHOT"
assert_exit 0 "verificar alinhado" "$tmp/ok/infra/scripts/versao.sh" verificar
cp "$tmp/ok/services/api/pom.xml" "$tmp/ok/pom.bak"
"$tmp/ok/infra/scripts/versao.sh" atual >/dev/null
"$tmp/ok/infra/scripts/versao.sh" verificar >/dev/null
if ! diff -q "$tmp/ok/services/api/pom.xml" "$tmp/ok/pom.bak" >/dev/null; then
    falhar "atualizar/verificar alterou o pom (devem ser somente leitura)"
fi

# atual avisa (exit 0) quando divergem; verificar falha
montar_arvore "$tmp/drift" "1.2.3" "9.9.9-SNAPSHOT"
assert_exit 0 "atual com divergência" "$tmp/drift/infra/scripts/versao.sh" atual
assert_exit 1 "verificar divergente" "$tmp/drift/infra/scripts/versao.sh" verificar

# bump recusa divergência
assert_exit 1 "corrigir com divergência" "$tmp/drift/infra/scripts/versao.sh" corrigir

# corrigir 1.2.3 -> 1.2.4, pom permanece -SNAPSHOT, dependências intactas
montar_arvore "$tmp/patch" "1.2.3" "1.2.3-SNAPSHOT"
"$tmp/patch/infra/scripts/versao.sh" corrigir >/dev/null
assert_eq "1.2.4" "$(ler_npm "$tmp/patch/package.json")" "raiz após corrigir"
assert_eq "1.2.4" "$(ler_npm "$tmp/patch/apps/pwa/package.json")" "pwa após corrigir"
assert_eq "1.2.4-SNAPSHOT" "$(ler_pom "$tmp/patch/services/api/pom.xml")" "pom após corrigir"
if grep -q '<version>3.33.3.2</version>' "$tmp/patch/services/api/pom.xml"; then
    :
else
    falhar "corrigir alterou versão de dependência do pom"
fi

# funcionalidade 1.2.3 -> 1.3.0
montar_arvore "$tmp/minor" "1.2.3" "1.2.3-SNAPSHOT"
"$tmp/minor/infra/scripts/versao.sh" funcionalidade >/dev/null
assert_eq "1.3.0" "$(ler_npm "$tmp/minor/package.json")" "raiz após funcionalidade"
assert_eq "1.3.0-SNAPSHOT" "$(ler_pom "$tmp/minor/services/api/pom.xml")" "pom após funcionalidade"

# grande 1.2.3 -> 2.0.0
montar_arvore "$tmp/major" "1.2.3" "1.2.3-SNAPSHOT"
"$tmp/major/infra/scripts/versao.sh" grande >/dev/null
assert_eq "2.0.0" "$(ler_npm "$tmp/major/package.json")" "raiz após grande"
assert_eq "2.0.0" "$(ler_npm "$tmp/major/apps/pwa/package.json")" "pwa após grande"
assert_eq "2.0.0-SNAPSHOT" "$(ler_pom "$tmp/major/services/api/pom.xml")" "pom após grande"

# verbo desconhecido
assert_exit 1 "uso inválido" "$tmp/ok/infra/scripts/versao.sh" foobar

if [[ "$falhas" -ne 0 ]]; then
    echo "versao.test.sh: $falhas falha(s)" >&2
    exit 1
fi

echo "versao.test.sh: ok"
