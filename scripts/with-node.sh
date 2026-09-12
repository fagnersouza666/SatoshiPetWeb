#!/bin/sh

set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
repository_root=$(CDPATH= cd -- "$script_dir/.." && pwd)
node_version=$(awk 'NF { print $1; exit }' "$repository_root/.nvmrc")

if [ -z "$node_version" ]; then
    echo "A versão do Node não está definida em .nvmrc." >&2
    exit 1
fi

if [ "$#" -eq 0 ]; then
    echo "Uso: with-node.sh <comando> [argumentos...]" >&2
    exit 64
fi

exec npm exec --yes --package="node@$node_version" -- "$@"
