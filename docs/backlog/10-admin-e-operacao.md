# Épico 10 — Administração e Operação

**Prioridade:** P4  
**Dependências:** FUND-12, todos os épicos de domínio  
**Referência PRD:** §17.3, §18

---

## Objetivo

Painel operacional, auditoria, exclusão de conta, backup/restore, restrições administrativas e preparação para abertura futura do código.

---

## Histórias

### ADMIN-01 — Painel de saúde das integrações

**Descrição:** Status Esplora, CoinGlass, cotação, clima, IA, e-mail; latência e última leitura válida.

**Regras de negócio:** §17.3, §18.2  
**Critérios de aceite:** —  
**Dependências:** FUND-12  

---

### ADMIN-02 — Monitoramento de jobs

**Descrição:** Atraso jobs 06/07/08h, monitor Bitcoin, geração IA, fila outbox, notificações pendentes.

**Regras de negócio:** §17.3, §18.2  
**Critérios de aceite:** CA-058 (observabilidade)  
**Dependências:** FUND-06, FUND-04  

---

### ADMIN-03 — Auditoria de ações privilegiadas

**Descrição:** Log imutável de ações admin, correções suporte autorizadas, regenerações, exclusões.

**Regras de negócio:** §17.3  
**Critérios de aceite:** —  
**Dependências:** FUND-02  

---

### ADMIN-04 — Restrições administrativas

**Descrição:** Garantir que admin **não** pode: trocar endereço pós-72h, alterar faixas DCA, acessar compras privadas em massa, reiniciar pet.

**Regras de negócio:** §17.3  
**Critérios de aceite:** CA-004, CA-007  
**Dependências:** CONTA-10  

---

### ADMIN-05 — Inspeção operacional por endereço/conta

**Descrição:** Suporte visualiza IDs operacionais, status sync, jobs, arte; sem expor dados privados desnecessários.

**Regras de negócio:** §17.3  
**Critérios de aceite:** —  
**Dependências:** ADMIN-01  

---

### ADMIN-06 — Fluxo exclusão conta (CC-22)

**Descrição:** Orquestrar CONTA-14; fila de remoção; prazo 24h dados ativos; instrução reaplicar em restore.

**Regras de negócio:** **CC-22**, §17.2  
**Critérios de aceite:** CA-070  
**Dependências:** CONTA-14  

---

### ADMIN-07 — Backup e restore

**Descrição:** Backup diário PostgreSQL + object storage; exercício de restore; RPO 24h, RTO 4h.

**Regras de negócio:** §18.1, §18.2  
**Critérios de aceite:** CA-070  
**Dependências:** FUND-02, FUND-03  

---

### ADMIN-08 — Restore com reconciliação Bitcoin

**Descrição:** Após restore, reconciliar rede sem duplicar alimentação/sugestão; reaplicar exclusões.

**Regras de negócio:** §18.2, §16.4  
**Critérios de aceite:** CA-070  
**Dependências:** ADMIN-07, BTC-08  

---

### ADMIN-09 — Métricas de custo e duplicidade

**Descrição:** Custo IA por endereço, chamadas API externas, duplicidades evitadas (idempotência).

**Regras de negócio:** §17.1, §18.2  
**Critérios de aceite:** —  
**Dependências:** FUND-12, ART  

---

### ADMIN-10 — Documentação operacional

**Descrição:** Runbooks: falha Esplora, falha IA, atraso DCA, incidente reorg, rotação VAPID/secrets.

**Regras de negócio:** §18, intenção open source §1  
**Critérios de aceite:** —  
**Dependências:** —  
**Notas técnicas:** Sem chaves, dumps prod ou dados usuário no repo.

---

## Definition of Done (épico)

- [ ] CA-070 verificável (restore + reconciliação + exclusões)
- [ ] Admin não bypassa regras CA-004/007
- [ ] Backup testado em staging
- [ ] Runbooks publicados em docs/
