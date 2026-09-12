# Revisão de segurança — infra/scripts (versão e prova local)

> Sistema: Satoshi Pet Web / scripts de versão e prova local
> Data: 12/09/2026 | Versão: 0.1.0 |
> Stack: Bash
> Baseado em: OWASP Top 10:2021, CWE
> Modo: **quick** (somente arquivos criados nesta entrega)

## Sumário de Segurança

- CRÍTICOS: 0
- ALTOS: 0
- MÉDIOS/BAIXOS: 0
- Pontos positivos: sem `eval`; verbo restrito a `case`; caminhos derivados de `BASH_SOURCE` e entre aspas; versão validada como `X.Y.Z` antes de escrever arquivos.
- Veredicto: **APROVADO**

Playbook OWASP nos quatro scripts: sem SQL, sem auth, sem XSS, sem deserialização. PLAY-06 (command injection): `$1` só entra no `case`; `nova` só após `exigir_semver`; `npm`/`mvnw`/`docker` sem concatenar input do usuário.
