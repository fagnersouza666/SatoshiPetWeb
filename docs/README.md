# Documentação — Satoshi Pet Web

## Documentos

| Documento | Descrição |
|-----------|-----------|
| [PRD-Satoshi-Pet-Web-v2.0.md](./PRD-Satoshi-Pet-Web-v2.0.md) | Especificação funcional completa (produto, regras, arquitetura) |
| [backlog/README.md](./backlog/README.md) | Backlog de implementação — índice, convenções e ordem de entrega |
| [../AGENTS.md](../AGENTS.md) | Guia para agentes de IA — stack, invariantes e convenções do projeto |

## Backlog de implementação

O backlog está organizado em épicos em [backlog/](./backlog/):

- **Produto principal:** PWA Angular 22 (substitui React do PRD §16.1 — ver ADR sugerido em `00-definicao-tecnica.md`)
- **Backend:** Java 25 + Quarkus 3.33 LTS + PostgreSQL 18.6
- **Escopo:** produto completo conforme PRD v2.0 (sem MVP)

Comece por [backlog/README.md](./backlog/README.md) e [backlog/00-definicao-tecnica.md](./backlog/00-definicao-tecnica.md).

O [`.gitignore`](../.gitignore) na raiz do repositório exclui artefatos de build, segredos, volumes Docker e dados de Bitcoin de teste; o que entra ou não no Git está em [backlog/00-definicao-tecnica.md](./backlog/00-definicao-tecnica.md) §6.1.
