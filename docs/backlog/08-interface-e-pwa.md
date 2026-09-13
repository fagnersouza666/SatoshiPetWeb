# Épico 08 — Interface e PWA (Produto Principal)

**Prioridade:** P0 → P3 (incremental)  
**Dependências:** FUND-10, FUND-11, demais épicos alimentam telas  
**Referência PRD:** §14

---

## Objetivo

Entregar a **experiência completa do produto** no PWA Angular instalável: tela principal, dashboard, históricos, QR, página pública, modo display, som, offline, responsividade e acessibilidade WCAG 2.2 AA.

> Este épico **é** o Satoshi Pet Web na visão do usuário. Backend e integrações existem para alimentar estas telas.

---

## Histórias — Base PWA (P0)

### PWA-01 — Shell instalável e rotas

**Descrição:** App shell com rotas: `/` (home autenticada), `/public/:address`, `/login`, `/register`, `/dashboard`, `/settings`, `/display`.

**Regras de negócio:** §14.1  
**Critérios de aceite:** CA-068  
**Dependências:** FUND-10  

---

### PWA-02 — Banner de instalação e fallback

**Descrição:** Detectar `beforeinstallprompt`; instruções quando instalação indisponível (iOS: adicionar à tela inicial).

**Regras de negócio:** §14.6, §23  
**Critérios de aceite:** CA-068  
**Dependências:** PWA-01  

---

### PWA-03 — Cliente WebSocket e reconexão

**Descrição:** Serviço Angular conecta WS; ao reconectar, busca snapshot + cursor; atualiza signals/estado.

**Regras de negócio:** §16.1  
**Critérios de aceite:** CA-025  
**Dependências:** FUND-05, PWA-01  

---

### PWA-04 — Página pública por endereço

**Descrição:** Visitante acessa `/public/:address`: pet, saldo, histórico on-chain, QR, 10 compras (data/R$), sugestão referência, cidade/clima — sem login.

**Regras de negócio:** §6.4, §14.1  
**Critérios de aceite:** CA-009  
**Dependências:** BTC-13, DCA-16, DCA-17, CLIMA-10  

---

## Histórias — Tela principal (P1)

### PWA-05 — Componente Pet (ovo/criatura)

**Descrição:** Renderizar sprites pixel art por estado; animações idle, alimentação, sono overlay; indicadores operacionais (sync, pendência).

**Regras de negócio:** §14.2, §7.6  
**Critérios de aceite:** CA-011, CA-012, CA-028  
**Dependências:** ART-10, PET, PWA-03  

---

### PWA-06 — Painel de status do pet

**Descrição:** Nome, estado emocional, reserva alimentar, tempo até próxima faixa, última alimentação, motivo ovo/carência.

**Regras de negócio:** §14.2  
**Critérios de aceite:** CA-021, CA-022  
**Dependências:** PWA-05, PET  

---

### PWA-07 — Painel Bitcoin na home

**Descrição:** Saldo confirmado sats, pendências separadas, situação sync, último recebimento.

**Regras de negócio:** §14.2, §9.1  
**Critérios de aceite:** CA-041 (exibição)  
**Dependências:** BTC-03, PWA-03  

---

### PWA-08 — Fila de comemorações e "Pular animações"

**Descrição:** Reproduzir recebimentos novos em ordem; botão pular atualiza cursor sem alterar energia.

**Regras de negócio:** §9.5, **CC-15**  
**Critérios de aceite:** CA-034, CA-035  
**Dependências:** PET-15, PWA-05  

---

### PWA-09 — Fluxo de aprovação de arte

**Descrição:** UI regenerar (1x) e aprovar; preservar estado ao fechar; loading ovo durante geração.

**Regras de negócio:** §8.3  
**Critérios de aceite:** CA-037, CA-038, CA-039  
**Dependências:** ART-05, PWA-05  

---

## Histórias — DCA e configuração (P2)

### PWA-10 — Card sugestão DCA do dia

**Descrição:** Valor sugerido, multiplicador, limites aplicados, horários dos dados, COMPREI / NÃO COMPREI / OUTRO VALOR.

**Regras de negócio:** §10.5, §14.2  
**Critérios de aceite:** CA-042, CA-043, CA-048  
**Dependências:** DCA-07, DCA-12  

---

### PWA-11 — Distinção sugestão pessoal vs porção referência

**Descrição:** Contas secundárias veem claramente "Sua sugestão DCA" e "Porção 24h deste pet".

**Regras de negócio:** §6.2, **CC-05**  
**Critérios de aceite:** CA-002  
**Dependências:** PWA-10, PET-02  

---

### PWA-12 — Configuração do plano DCA

**Descrição:** Formulário valor-base, orçamento, teto, pausar; localização; preferências notificação.

**Regras de negócio:** §10.1  
**Critérios de aceite:** CA-057  
**Dependências:** DCA-01  

---

### PWA-13 — Troca de endereço (72h)

**Descrição:** UI countdown prazo; confirmação destrutiva CC-03; bloqueio após deadline.

**Regras de negócio:** §5, §14.1  
**Critérios de aceite:** CA-003, CA-004, CA-005  
**Dependências:** CONTA-08, CONTA-09  

---

## Histórias — Dashboard e históricos (P2)

### PWA-14 — Dashboard estatísticas completo

**Descrição:** Métricas §14.3: recebimentos, alimentações, idade pet, tempo por estado, índice histórico, orçamento, reserva, decisões DCA, gráficos.

**Regras de negócio:** §14.3, **CC-21**  
**Critérios de aceite:** —  
**Dependências:** PET-17, DCA, BTC  

---

### PWA-15 — Histórico on-chain

**Descrição:** Lista completa entradas/saídas: status, txid, bloco, confirmações, valor, taxa, link explorador.

**Regras de negócio:** §14.3  
**Critérios de aceite:** CA-009 (privado completo autenticado)  
**Dependências:** BTC-13  

---

### PWA-16 — Histórico compras declaradas (privado)

**Descrição:** CRUD compras com correção/exclusão; separado de on-chain e sugestões.

**Regras de negócio:** §14.3, §10.5  
**Critérios de aceite:** CA-049, CA-050, CA-056  
**Dependências:** DCA-12  

---

## Histórias — QR, ambiente, display (P3)

### PWA-17 — Gerador QR Code

**Descrição:** QR endereço e URI Bitcoin; atalhos porção 24h e valor custom; impedir sat fracionário.

**Regras de negócio:** §14.4  
**Critérios de aceite:** —  
**Dependências:** DCA-11  

---

### PWA-18 — Ambiente visual (clima e ciclo solar)

**Descrição:** Cenário dinâmico: clima, temperatura, hora, transições solares; efeitos visuais sem impacto em alimentação.

**Regras de negócio:** §13, §14  
**Critérios de aceite:** CA-040  
**Dependências:** CLIMA-07, CLIMA-08  

---

### PWA-19 — Modo display tela cheia

**Descrição:** Landscape, pet em destaque, clima, hora, saldo, estado; wake lock quando suportado; saída acessível.

**Regras de negócio:** §14.5  
**Critérios de aceite:** CA-068  
**Dependências:** PWA-05, PWA-18  

---

### PWA-20 — Som opcional

**Descrição:** Desligado por padrão; sons alimentação, nascimento, ambiente; habilitação explícita.

**Regras de negócio:** §14.5  
**Critérios de aceite:** —  
**Dependências:** PWA-05  

---

## Histórias — Offline, a11y, responsividade (P3)

### PWA-21 — Modo offline

**Descrição:** Último estado em cache com aviso; desabilitar mutações financeiras offline; não inventar sync.

**Regras de negócio:** §14.6  
**Critérios de aceite:** —  
**Dependências:** FUND-11, PWA-03  

---

### PWA-22 — Responsividade mobile-first

**Descrição:** Breakpoints 320–767, 768–1199, 1200+ px; portrait e landscape; alvos de toque adequados.

**Regras de negócio:** §14.6  
**Critérios de aceite:** CA-068  
**Dependências:** PWA-01  

---

### PWA-23 — Acessibilidade WCAG 2.2 AA

**Descrição:** Contraste, teclado, foco, live regions para estado pet, `prefers-reduced-motion`, descrições textuais.

**Base do shell:** o layout fornece landmarks de cabeçalho, conteúdo principal e navegação,
link de salto para teclado, foco no conteúdo após troca de rota e estado ativo anunciado
por `aria-current`. A política global de `prefers-reduced-motion: reduce` desliga
animações e transições sem remover os estados textuais.

**Regras de negócio:** §14.7  
**Critérios de aceite:** CA-069  
**Dependências:** PWA-05, PWA-06  

---

### PWA-24 — Jornadas de onboarding

**Descrição:** Registro: verificar e-mail → endereço/nome → aceitar publicação → localizar → código recuperação → sync → ovo/plano.

**Regras de negócio:** §14.1  
**Critérios de aceite:** CA-001, CA-006  
**Dependências:** CONTA, CLIMA, DCA-01  

---

## Definition of Done (épico)

- [ ] PWA instalável e utilizável como app standalone
- [ ] Tela principal reflete estado servidor em tempo real
- [ ] Página pública CA-009 completa
- [ ] Dashboard e históricos separados (on-chain vs declaradas)
- [ ] Modo display funcional em landscape
- [ ] Offline leitura + a11y CA-069
- [ ] Logout limpa cache privado CA-067
