# Relatório de Segurança — Satoshi Pet Web / PWA Foundation (FUND)

> Sistema: PWA Angular 22 — Fundação (épico FUND)  
> Data: 12/09/2026 | Versão: 0.2.0  
> Stack: Angular 22, TypeScript 6.0  
> Baseado em: OWASP Top 10:2021, LGPD (Lei 13.709/2018), CWE  
> Modo: **quick** — arquivos criados/modificados nesta entrega

---

## Sumário de Segurança

- CRÍTICOS: **0**
- ALTOS: **0**
- MÉDIOS/BAIXOS: **0**
- Pontos positivos: veja observações
- Veredicto: **APROVADO**

---

## Evidências dos Scans Executados

### PLAY-05: XSS — Angular

| Pattern                        | Resultado        |
|-------------------------------|------------------|
| `bypassSecurityTrustHtml/Url` | ✅ Nenhum match   |
| `[innerHTML]`                 | ✅ Nenhum match   |
| `eval()`                      | ✅ Nenhum match   |
| `document.write`              | ✅ Nenhum match   |

### PLAY-08: Authentication / Token Storage

| Pattern                            | Resultado       |
|------------------------------------|-----------------|
| `localStorage.setItem.*token`      | ✅ Nenhum match  |
| `sessionStorage.setItem.*token`    | ✅ Nenhum match  |

### PLAY-03: Cryptographic Failures

| Pattern                  | Resultado       |
|--------------------------|-----------------|
| Secrets hardcoded        | ✅ Nenhum match  |
| API keys no código       | ✅ Nenhum match  |

### PLAY-09: Subscription / Memory Leaks

| Pattern                   | Resultado                                                                 |
|---------------------------|---------------------------------------------------------------------------|
| `addEventListener`        | ⚠️ Encontrado em `offline.service.ts:20-21` — **falso positivo verificado** |
| `subscribe(`              | ✅ Nenhum match                                                            |

**Análise do falso positivo:** Os listeners `online`/`offline` em `OfflineService` são adicionados no construtor e explicitamente removidos em `ngOnDestroy` (linhas 27-30). Não há memory leak.

---

## Pontos de Segurança Positivos

1. **Sem secrets no código**: O `environment.ts` contém apenas URLs; nenhum token, senha ou API key hardcoded.

2. **API_BASE_URL via InjectionToken**: URL base da API injetada via token — facilita mocking e evita magic strings.

3. **Angular sanitiza interpolações**: Todo output de dados de usuário (ex: `:address` em `EnderecoComponent`) usa `{{ address() }}` — Angular sanitiza automaticamente, prevenindo XSS.

4. **Sem `bypassSecurityTrust*`**: Nenhum bypass de sanitização do Angular presente nos arquivos criados.

5. **CSP-ready**: A PWA não usa `innerHTML` nem `eval()`, facilitando uma CSP estrita em deploy futuro.

6. **Tokens de ambiente separados**: `environment.ts` (dev) vs `environment.prod.ts` (prod) com `fileReplacements` no `angular.json` — sem URL de dev em produção.

7. **Service Worker em produção apenas**: `provideServiceWorker` usa `enabled: !isDevMode()` — SW não intercepta requests em desenvolvimento.

8. **ngsw-config.json** sem cache de dados sensíveis: dataGroup da API usa `strategy: freshness` com `timeout: 10s` — nunca serve dados de API stale como se fossem frescos.

---

## Recomendações Futuras (não bloqueantes)

- **CSP header**: Ao implementar o servidor Nginx/Caddy, adicionar `Content-Security-Policy: default-src 'self'` como baseline.
- **Subresource Integrity**: Para assets de terceiros futuros, usar SRI.
- **Auth guards**: Quando o épico CONTA for implementado, adicionar `canActivate` na rota `/conta` protegida por JWT; validação **sempre** repetida no backend.

---

_Revisado em 12/09/2026 — Nenhuma vulnerabilidade identificada nos arquivos desta entrega._
