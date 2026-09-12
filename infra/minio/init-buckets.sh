#!/usr/bin/env sh
# Provisiona os buckets de assets do ambiente local.
#
# `mc mb --ignore-existing` torna a operação segura para reinícios do
# Compose e para recriações do container sem apagar os objetos existentes.
set -eu

: "${MINIO_ROOT_USER:?MINIO_ROOT_USER precisa ser informado}"
: "${MINIO_ROOT_PASSWORD:?MINIO_ROOT_PASSWORD precisa ser informado}"

MINIO_ENDPOINT="${MINIO_ENDPOINT:-http://minio:9000}"

mc alias set local "$MINIO_ENDPOINT" "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD"

for bucket in pet-artwork pet-artwork-staging; do
    mc mb --ignore-existing "local/$bucket"
done

mc anonymous set download local/pet-artwork-staging
