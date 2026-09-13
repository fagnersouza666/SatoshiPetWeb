# Contrato de sessão e CSRF

Referências: PRD §4, CONTA-07, CA-067.

## Cookie de sessão

| Atributo | Valor |
| --- | --- |
| Nome | `sp_session` |
| Conteúdo | Token bruto de sessão (256 bits, URL-safe) |
| HttpOnly | sim |
| SameSite | Strict |
| Path | `/` |
| Secure | habilitado em produção (HTTPS) |

O servidor persiste somente o hash SHA-256 do token (`Session.sessionTokenHash`).

## Header CSRF

| Atributo | Valor |
| --- | --- |
| Nome | `X-CSRF-Token` |
| Conteúdo | Token CSRF bruto entregue na criação da sessão |
| Obrigatório em | `POST`, `PATCH`, `DELETE` autenticados (inclui `POST /api/v1/account/pet/presentation/skip`) |

Endpoints públicos de magic link (`/api/v1/auth/magic-link*`) e requisições
sem sessão autenticada não exigem CSRF.

### Falha CSRF — `403 Forbidden`

```json
{
  "code": "csrf_invalid",
  "message": "Token CSRF inválido ou ausente."
}
```

## Criação de sessão

A sessão é criada e os tokens brutos são entregues em:

- `POST /api/v1/auth/magic-link/verify` (conta existente);
- `POST /api/v1/auth/register` (nova conta);
- `POST /api/v1/account/recovery/reset` (recuperação).

Resposta inclui cookie `sp_session` + header `X-CSRF-Token`.

## Revogação

- `POST /api/v1/auth/logout` — revoga a sessão atual e expira o cookie.
- Troca de sessão ou recuperação revogam sessões anteriores da conta conforme
  `SessionService`.

## PWA

A PWA deve:

1. Armazenar o CSRF bruto apenas em memória (não em `localStorage`).
2. Enviar `X-CSRF-Token` em toda mutation autenticada.
3. Limpar cache privado no logout (CA-067) via `PrivateCacheService`.
