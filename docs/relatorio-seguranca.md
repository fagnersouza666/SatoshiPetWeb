# Relatório de Segurança — Satoshi Pet Web / PWA Auth Flows

> Sistema: PWA Angular 22 — épico CONTA (autenticação + conta + endereço)
> Data: 12/09/2026 | Versão: 1.1.0
> Stack: Angular 22, TypeScript 6, Vitest 4
> Baseado em: OWASP Top 10:2021, LGPD (Lei 13.709/2018), CWE

---

## Sumário de Segurança

- CRÍTICOS: 0
- ALTOS: 0
- MÉDIOS/BAIXOS: 2
- Pontos positivos: 8
- Veredicto: **APROVADO COM RESSALVAS**

---

## MÉDIO/BAIXO — Backlog de segurança

### SEC-001: Guard de rota apenas no frontend sem sessão hidratada do servidor

**Localização:** `apps/pwa/src/app/core/guards/auth.guard.ts:13`
**Descrição:** O `authGuard` verifica `session.isAuthenticated()`, que é um signal em memória inicializado como `null` a cada carregamento. Na primeira carga da PWA, mesmo um usuário autenticado no servidor é tratado como não autenticado até que algum endpoint popule a sessão.
**Referência:** OWASP A01:2021 — Broken Access Control | CWE-306

**Evidência:**

**auth.guard.ts:13–18** — verificação puramente em memória:
```typescript
export const authGuard: CanActivateFn = () => {
  const session = inject(SessionService);
  const router = inject(Router);

  if (session.isAuthenticated()) {
    return true;
  }
  return router.createUrlTree(['/entrar']);
};
```

**Impacto:** Após um refresh, um usuário com sessão válida é redirecionado para `/entrar` mesmo tendo cookie de sessão ativo. Isso é um problema de UX, não um bypass de segurança — a proteção real está sempre no backend (CA-009 e invariante §5 do PRD).

**Recomendação (próxima iteração):**
Implementar um resolver ou efeito de bootstrap que consulte `GET /v1/auth/session` para hidratar o `SessionService` antes de ativar as rotas privadas. O guard atual é correto como segurança complementar ao backend; o problema é apenas UX em refresh de página.

---

### SEC-002: Header CSRF ausente em GET requests (design esperado — documetado)

**Localização:** `apps/pwa/src/app/core/api-client.service.ts:37–43`
**Descrição:** O header `X-XSRF-TOKEN` é adicionado a todas as requisições, incluindo GETs. GETs não devem modificar estado e o header CSRF em GETs é redundante, mas não é um problema de segurança. O ponto de atenção é que se o cookie `XSRF-TOKEN` não existir e o meta tag também não, o header CSRF não é enviado — a API deve rejeitar isso.
**Referência:** OWASP A01:2021 | CWE-352

**Evidência:**

**api-client.service.ts:37–43**:
```typescript
private buildHeaders(): HttpHeaders {
  let headers = new HttpHeaders({ 'Content-Type': 'application/json' });
  const token = this.csrfToken();
  if (token) {
    headers = headers.set('X-XSRF-TOKEN', token);
  }
  return headers;
}
```

**Impacto:** Se o servidor exigir o header CSRF para mutações e o cookie/meta não estiver presente (ex: ambiente de CI, SSR sem cookie), as requisições POST/PUT/DELETE falharão com 403 — comportamento correto de segurança, mas pode ser confuso durante desenvolvimento.

**Recomendação:** Documentar no `AGENTS.md` que o servidor deve retornar o cookie `XSRF-TOKEN` em toda resposta autenticada. O comportamento atual está correto — falhar sem token é o caminho seguro.

---

## Pontos Positivos

| Controle | Status | Evidência |
|---|---|---|
| Tokens nunca em `localStorage` | ✅ CONFORME | Sem nenhum acesso a `localStorage` em toda a PWA |
| `withCredentials: true` em todas as requisições | ✅ CONFORME | `api-client.service.ts:54,62,69,76` |
| CSRF via cookie + meta tag fallback | ✅ CONFORME | `api-client.service.ts:23–35` |
| Sem `bypassSecurityTrust*` | ✅ CONFORME | Grep confirmou ausência total |
| Sem `[innerHTML]` com dados externos | ✅ CONFORME | Grep confirmou ausência total |
| Sem segredos hardcoded | ✅ CONFORME | Grep confirmou ausência total |
| Logout limpa cache antes de memória (CA-067) | ✅ CONFORME | `auth.service.ts:97–106` |
| Labels WCAG em todos os campos de formulário | ✅ CONFORME | `for/id` associados em todos os inputs |
| Dados privados nunca logados | ✅ CONFORME | Nenhum `console.*` em código de produção |

---

## Conformidade LGPD (parcial — fluxo frontend)

| Requisito LGPD | Status | Detalhes |
|---|---|---|
| Minimização de dados coletados | CONFORME | Apenas e-mail + endereço Bitcoin + nome do pet |
| Mascaramento em logs | CONFORME | Sem logs de dados pessoais no frontend |
| Dado sensível fora de `localStorage` | CONFORME | Sessão apenas em memória (signal) |
| Consentimento explícito | NÃO VERIFICÁVEL | Escopo do frontend; deve ser implementado no backend |
| Direito de exclusão | NÃO VERIFICÁVEL | Funcionalidade de conta futura (épicos CONTA-08+) |
