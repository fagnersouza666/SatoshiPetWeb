#!/usr/bin/env sh
# Wrapper fino — delega para Node.js (funciona em Linux, macOS e Windows).
exec node "$(dirname "$0")/versao.mjs" "$@"
