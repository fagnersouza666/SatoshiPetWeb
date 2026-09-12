# Bug Report — infra/scripts (versão e prova local)

> Data: 12/09/2026 | Stack: Bash (Git Bash/WSL)
> Modo: **quick**
> Arquivos analisados: 4 (`versao.sh`, `versao.test.sh`, `check-pwa.sh`, `check-api.sh`)

---

## Sumário

| Severidade | Quantidade |
|------------|------------|
| CRITICO    | 0          |
| ALTO       | 0          |
| MEDIO      | 0          |
| BAIXO      | 0          |
| **Total**  | **0**      |

**Veredicto:** APROVADO

Corrigido nesta entrega, antes do relatório:

- `sed | head` com `set -o pipefail` (SIGPIPE) — leitores passaram a `awk` + `exit`.
- `awk -v nova="$nova-SNAPSHOT"` — o sufixo vai no literal do script, não no `-v`.
- `pom.xml` vivo voltou para `0.1.0-SNAPSHOT`; `atualizar`/`verificar` agora têm teste de imutabilidade.

---

## Observações Gerais

- Caminhos entre aspas (`Pet Web` tem espaço).
- `exigir_semver` antes de interpolar em `awk`.
- `check-api.sh` exige Docker como o `check-backend.sh` do acertoapp; `skipITs` no pom ainda pode pular ITs.
