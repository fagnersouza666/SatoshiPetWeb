#!/usr/bin/env sh
# Teste unitário do bootstrap dos buckets, sem exigir Docker local.
set -eu

script_dir="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
init_script="$script_dir/init-buckets.sh"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

mkdir -p "$tmp/bin"
cat > "$tmp/bin/mc" <<'EOF'
#!/usr/bin/env sh
printf '%s\n' "$*" >> "$MC_CALLS"
EOF
chmod +x "$tmp/bin/mc"

export MC_CALLS="$tmp/mc.calls"
export MINIO_ENDPOINT="http://storage.test:9000"
export MINIO_ROOT_USER="test-user"
export MINIO_ROOT_PASSWORD="test-password"
PATH="$tmp/bin:$PATH" "$init_script"
PATH="$tmp/bin:$PATH" "$init_script"

esperado=$(cat <<'EOF'
alias set local http://storage.test:9000 test-user test-password
mb --ignore-existing local/pet-artwork
mb --ignore-existing local/pet-artwork-staging
anonymous set download local/pet-artwork-staging
alias set local http://storage.test:9000 test-user test-password
mb --ignore-existing local/pet-artwork
mb --ignore-existing local/pet-artwork-staging
anonymous set download local/pet-artwork-staging
EOF
)

obtido="$(cat "$MC_CALLS")"
if [ "$obtido" != "$esperado" ]; then
    printf 'FALHA: chamadas ao mc divergentes.\nEsperado:\n%s\nObtido:\n%s\n' \
        "$esperado" "$obtido" >&2
    exit 1
fi

printf '%s\n' 'init-buckets.test.sh: ok'
