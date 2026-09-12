# Épico 06 — DCA e Contabilidade

**Prioridade:** P2  
**Dependências:** CONTA-02, FUND-06, integrações mercado  
**Referência PRD:** §10, §11, §12

---

## Objetivo

Plano DCA individual por conta: 11 faixas Fear & Greed, ciclo 06/07/08h local, sugestão única, orçamento mensal acumulado, compromissos, reserva virtual informativa, compras declaradas e porção alimentar de referência do pet.

---

## Histórias

### DCA-01 — Configuração do plano (DcaPlanVersion)

**Descrição:** Valor-base diário, orçamento mensal, teto diário, ativo/pausado, fuso efetivo, notificações; versionamento com vigência.

**Regras de negócio:** §10.1  
**Critérios de aceite:** CA-057  
**Dependências:** CONTA-02  

---

### DCA-02 — Tabela de 11 faixas Fear & Greed

**Descrição:** Implementar multiplicadores fixos 0–4 até 96–100 conforme §10.2; não editável por usuário/admin comum.

**Regras de negócio:** §10.2  
**Critérios de aceite:** CA-041, CA-042  
**Dependências:** DCA-01  

---

### DCA-03 — Integração CoinGlass Fear & Greed

**Descrição:** Coletar índice; validar 0–100; persistir `MarketSnapshot` com fonte, referência, timestamps.

**Regras de negócio:** §11.1, §23  
**Critérios de aceite:** CA-058, CA-059  
**Dependências:** FUND-06  

---

### DCA-04 — Integração cotação BTC/BRL

**Descrição:** Provedor configurável; validar preço positivo; persistir snapshot com timestamp efetivo.

**Regras de negócio:** §10.4, §11.1  
**Critérios de aceite:** CA-059, CA-060  
**Dependências:** FUND-06  

---

### DCA-05 — Coletas 06h, 07h, 08h (cache)

**Descrição:** Jobs às 06h e 07h aquecem cache sem sugestão/notificação; às 08h gerar sugestão se dados válidos.

**Regras de negócio:** §11.1  
**Critérios de aceite:** CA-058  
**Dependências:** DCA-03, DCA-04, FUND-06  

---

### DCA-06 — Validade de dados (CC-17)

**Descrição:** Aceitar leitura desde 06h do dia civil anterior no fuso; rejeitar mais antiga; exibir idade efetiva.

**Regras de negócio:** **CC-17**, §11.1  
**Critérios de aceite:** CA-059, CA-060  
**Dependências:** DCA-05  

---

### DCA-07 — Cálculo da sugestão diária

**Descrição:** `valorTeorico × limites`; arredondar BRL para baixo em centavos; compras do dia consomem teto (CC-16).

**Regras de negócio:** §10.3, **CC-16**  
**Critérios de aceite:** CA-042, CA-043, CA-044  
**Dependências:** DCA-02, DCA-08  

---

### DCA-08 — Orçamento mensal acumulado (CC-19)

**Descrição:** Sobra/excesso carrega para mês seguinte; mês civil no fuso contábil congelado; alteração de orçamento no mês corrente aplica diferença.

**Regras de negócio:** §12.1, **CC-19**  
**Critérios de aceite:** CA-051, CA-052  
**Dependências:** DCA-01  

---

### DCA-09 — Compromissos de sugestão positiva

**Descrição:** Sugestão positiva compromete valor; COMPREI/NÃO COMPREI/expiração 08h liberam; atravessa mês sem duplicar.

**Regras de negócio:** §12.2  
**Critérios de aceite:** CA-045, CA-046, CA-047, CA-053  
**Dependências:** DCA-07  

---

### DCA-10 — Reserva virtual informativa (CC-20)

**Descrição:** Fechamento diário: base − compras atribuídas; acumula entre meses; dias pausados sem base.

**Regras de negócio:** §12.3, **CC-20**  
**Critérios de aceite:** CA-054  
**Dependências:** DCA-12  

---

### DCA-11 — Conversão BRL → sats e travamento

**Descrição:** Fixar cotação na geração; equivalência decimal interna; QR com sats inteiros; sugestão zero mantém última porção pet.

**Regras de negócio:** §10.4  
**Critérios de aceite:** CA-045, CA-055  
**Dependências:** DCA-04, DCA-07  

---

### DCA-12 — Compras declaradas

**Descrição:** COMPREI, NÃO COMPREI, COMPREI OUTRO VALOR, avulsa, correção/exclusão com auditoria; data real de compra.

**Regras de negócio:** §10.5  
**Critérios de aceite:** CA-048, CA-049, CA-050, CA-056  
**Dependências:** DCA-09  
**Notas técnicas:** Compra manual nunca alimenta pet.

---

### DCA-13 — Guardas de fuso e anti-duplicidade (CC-18)

**Descrição:** Próxima sugestão: primeiro 08h cuja data local > última sugestão; nunca duplicar por viagem/fuso.

**Regras de negócio:** **CC-18**, §11.3  
**Critérios de aceite:** CA-062, CA-063  
**Dependências:** DCA-05, CLIMA  

---

### DCA-14 — Falha às 08h e sugestão tardia

**Descrição:** Sem dados: aguardar, retentar, uma sugestão tardia no ciclo; não recalcular sugestão já emitida.

**Regras de negócio:** §11.2  
**Critérios de aceite:** CA-061  
**Dependências:** DCA-06  

---

### DCA-15 — PetReferencePortion (porção compartilhada)

**Descrição:** Snapshot de porção 24h para pet; origem (conta referência); vigência; integração PET-02.

**Regras de negócio:** §6.2, §10.4  
**Critérios de aceite:** CA-055, CA-027  
**Dependências:** DCA-11, PET-02  

---

### DCA-16 — Projeção pública: sugestão de referência (CC-09)

**Descrição:** Publicar sugestão da fonte alimentar como "Sugestão de referência do pet"; sem orçamento/reserva/identidade.

**Regras de negócio:** **CC-09**, §6.4  
**Critérios de aceite:** CA-009  
**Dependências:** DCA-15, PWA  

---

### DCA-17 — Projeção pública: 10 compras declaradas (CC-08)

**Descrição:** Dez compras mais recentes de contas vinculadas; só data e R$; sem identificar conta; desempate estável.

**Regras de negócio:** **CC-08**, §6.4  
**Critérios de aceite:** CA-009  
**Dependências:** DCA-12, CONTA-09  

---

### DCA-18 — Extrato reprodutível e snapshots

**Descrição:** Separar snapshot de cálculo, projeção atual pós-correções e porções alimentares aplicadas.

**Regras de negócio:** §12.4  
**Critérios de aceite:** CA-050, CA-057  
**Dependências:** DCA-07, DCA-12  

---

### DCA-19 — Eventos DCA padronizados

**Descrição:** `DCA_RECOMMENDATION_GENERATED`, `DCA_RECOMMENDATION_EXPIRED`, `PURCHASE_*`.

**Regras de negócio:** §16.3  
**Critérios de aceite:** —  
**Dependências:** FUND-04  

---

### DCA-20 — Déficit orçamentário

**Descrição:** Correção tardia negativa impede novas sugestões positivas até capacidade; não reescrever sugestão histórica.

**Regras de negócio:** §12.2  
**Critérios de aceite:** CA-048  
**Dependências:** DCA-08, DCA-12  

---

## Definition of Done (épico)

- [ ] CA-041 a CA-057 verificáveis (exceto CA-058..063 parcialmente com CLIMA)
- [ ] Uma sugestão por ciclo/dia/conta
- [ ] Compras declaradas isoladas de alimentação on-chain
- [ ] Porção referência alimenta pet via PET-02
- [ ] Projeções públicas CC-08/09 sem dados privados
