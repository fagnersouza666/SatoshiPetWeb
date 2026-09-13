# ADR-001 — Frontend: Angular 22 em vez de React

**Status:** Aceito  
**Data:** 2026-09-12  
**Decisores:** Responsável pelo produto  
**Contexto:** PRD v2.0 §16.1, `docs/backlog/00-definicao-tecnica.md` §1.2

---

## Contexto

O PRD v2.0 §16.1 especificou React + TypeScript como tecnologia de frontend. Antes do início da implementação, o responsável pelo produto decidiu substituir o framework por Angular 22. A definição técnica (`docs/backlog/00-definicao-tecnica.md` §1.2) registra o desvio e aponta para este ADR como fonte de verdade.

## Decisão

Adotar **Angular 22 + TypeScript** como framework de frontend, em lugar de React.

Stack resultante:

| Componente | Versão | Notas |
|------------|--------|-------|
| Angular | **22.1.6** | Suporte ativo até jun/2028 |
| TypeScript | **~6.0.3** | Matriz angular.dev; `≥6.0.0 <6.1.0` |
| Node.js dev | **22.23.2** | Conforme `.nvmrc`; faixa `^22.22.3 \|\| ^24.15.0 \|\| >=26.0.0` |
| `@angular/pwa` | incluso no CLI | Service worker `ngsw`, manifest, ícones |
| Angular CDK | 22.x | Focus management, live announcer, overlay |
| Vitest | ^4 | Testes unitários com jsdom |
| Standalone components | — | Sem NgModule; arquitetura moderna |

## Motivação

| Critério | Avaliação |
|----------|-----------|
| **PWA nativa** | `@angular/pwa` integrado ao CLI: service worker, manifest, Web Push — sem configuração manual |
| **Signals** | Reatividade granular sem zona de detecção extra; alinhado com requisito de tempo real (WebSocket) |
| **TypeScript first** | Integração de primeira classe desde a geração do projeto |
| **Acessibilidade** | Angular CDK cobre WCAG 2.2 AA: focus trap, live announcer, `prefers-reduced-motion` |
| **LTS até 2028** | Janela de suporte compatível com o roadmap do produto |
| **Standalone components** | Menos boilerplate, tree-shaking mais eficiente, melhor DX |

## Consequências positivas

- Service worker gerado e atualizado automaticamente pelo CLI Angular (`ng build --configuration production` já inclui o SW).
- Web Push com suporte oficial para iOS 16.4+ e requisitos PRD §23 cobertos pela biblioteca.
- Acessibilidade (WCAG 2.2 AA) suportada pelo Angular CDK sem dependências externas.
- Menor superfície de configuração para PWA instalável em comparação com soluções baseadas em Vite/Workbox.

## Consequências negativas / mitigações

| Consequência | Mitigação |
|-------------|-----------|
| PRD §16.1 menciona React | Este ADR documenta o desvio; o PRD não é reescrito retroativamente — este arquivo é a fonte de verdade |
| Curva de aprendizado para devs familiarizados só com React | Documentação e exemplos na base de código; Angular CLI acelera a criação de componentes |
| Build da PWA é mais lento que Vite puro | Cache de camadas Docker mitiga; `ng build` produção com cache de layers é < 2 min |

## Alternativas descartadas

| Opção | Motivo de descarte |
|-------|-------------------|
| React + Vite + Workbox | PWA manual mais complexa; ecossistema fragmentado; sem integração nativa de SW |
| Vue 3 + Vite | Boa opção técnica, mas menor adoção interna e sem pedido explícito |
| Svelte/SvelteKit | Ecossistema PWA menos maduro em produção; sem suporte LTS definido |
| Next.js (React SSR) | Modelo SSR contradiz o requisito PWA instalável puro (PRD §1) |

## Referências

- [Angular releases e suporte LTS](https://angular.dev/reference/releases)
- [PRD v2.0 §16.1](../PRD-Satoshi-Pet-Web-v2.0.md)
- [Definição técnica §1.2](../backlog/00-definicao-tecnica.md)
- [ADR-002 — execução containerizada](./ADR-002-execucao-containerizada.md)
