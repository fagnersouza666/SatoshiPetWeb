# Bug Report — Satoshi Pet Web / PWA Auth Flows

> Data: 12/09/2026 | Stack: Angular 22 + TypeScript 6 + Vitest 4
> Modo: **pr-review** (arquivos criados/modificados no épico CONTA)
> Arquivos analisados: 14

---

## Sumário

| Severidade | Quantidade |
|------------|------------|
| CRÍTICO    | 0          |
| ALTO       | 0          |
| MÉDIO      | 0          |
| BAIXO      | 1          |
| **Total**  | **1**      |

**Veredicto: APROVADO COM RESSALVAS** — Nenhum bug crítico ou alto. Um defeito de baixa severidade identificado e corrigido.

---

## BAIXO

### BUG-001: Regex de endereço Bitcoin com limites de comprimento incorretos

**Arquivo:** `apps/pwa/src/app/features/auth/cadastro/cadastro.component.ts`
**Linha(s):** 267 (antes da correção)
**Status:** ✅ **CORRIGIDO nesta análise**

**O que acontecia:**
O padrão regex usava `^bc1[a-z0-9]{6,87}$` para validar endereços bech32, permitindo endereços `bc1` com apenas 9 caracteres totais. O mínimo correto é 42 caracteres (P2WPKH). Para o ramo legacy, usava `{25,34}` após `[13]`, produzindo 26–35 caracteres totais em vez do correto 25–34.

**Por que é um problema:**
Strings curtas inválidas como `bc1qtest` passariam na validação do formulário e só seriam rejeitadas pelo servidor, gerando uma chamada de rede desnecessária e uma UX confusa de erro tardio.

**Código problemático (original):**
```typescript
// Total bc1: 9-90 chars (muito permissivo)
// Total legacy: 26-35 chars (off-by-one)
private readonly BITCOIN_ADDRESS_PATTERN = /^[13][a-km-zA-HJ-NP-Z1-9]{25,34}$|^bc1[a-z0-9]{6,87}$/;
```

**Correção aplicada:**
```typescript
// Total legacy P2PKH/P2SH: 25-34 chars  ✓
// Total bech32 P2WPKH/P2WSH/Taproot: 42-62 chars  ✓
private readonly BITCOIN_ADDRESS_PATTERN = /^[13][a-km-zA-HJ-NP-Z1-9]{24,33}$|^bc1[a-z0-9]{39,59}$/;
```

**Explicação:** O ramo `[13]{1} + {24,33}` = 25–34 chars totais. O ramo `bc1{3} + {39,59}` = 42–62 chars totais, cobrindo P2WPKH (42), P2WSH (62) e Taproot (62). Nota: a validação definitiva é sempre do servidor; este padrão é apenas o primeiro filtro de UX.

---

## Observações Gerais — Pontos Positivos

- **Zero subscriptions vazadas**: nenhum `.subscribe()` sem cleanup — todos os fluxos usam `firstValueFrom()` ou Observable descartado dentro de testes.
- **Catch blocks com propósito**: todos os `catch {}` no código de produção ou configuram um signal de erro de UI ou são intencionalmente silenciosos (logout com sessão expirada, Cache API indisponível) — nenhum swallow silencioso de erro crítico.
- **Sinais OnPush corretos**: todos os componentes usam `ChangeDetectionStrategy.OnPush` + signals, sem `Default` detection.
- **Estado sem mutação**: `session.account` exposto como `ReadonlySignal` — impossível de modificar externamente.
- **Logout seguro**: ordem correta (servidor → cache → memória → navegação) e resiliente a falha de rede.
- **Promise.allSettled no clearPrivateCaches**: garante que a falha em deletar um cache não impede a limpeza dos demais.
