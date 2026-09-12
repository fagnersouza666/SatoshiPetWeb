# Épico 02 — Contas e Vínculos

**Prioridade:** P0  
**Dependências:** FUND-01, FUND-02, FUND-07, FUND-10  
**Referência PRD:** §4, §5, §6 (parcial), §17

---

## Objetivo

Registro por e-mail (magic link), recuperação por código, vínculo conta↔endereço com janela de 72h, troca atômica, permissões e isolamento de dados privados entre contas do mesmo endereço.

---

## Histórias

### CONTA-01 — Registro com magic link

**Descrição:** Fluxo de registro: e-mail → magic link de uso único e curta validade → verificação antes de qualquer escrita.

**Regras de negócio:** §4.1, **CC-01**  
**Critérios de aceite:** CA-001  
**Dependências:** FUND-02, integração e-mail (stub em dev)  
**Notas técnicas:** Token hash armazenado; link expira em minutos configuráveis.
O contrato de persistência usa `magic_link_tokens` com `token_hash`, `issued_at`,
`expires_at` e `consumed_at`; o consumo é uma atualização condicional atômica
(`consumed_at IS NULL` e `expires_at > agora`). O TTL é fornecido pela chave
`satoshi-pet.magic-link.ttl`, sem valor padrão nesta atividade. O token bruto
fica somente no fluxo emissor e nunca é persistido.

---

### CONTA-02 — Cadastro com endereço Bitcoin e nome do pet

**Descrição:** Após verificação, coletar endereço Mainnet válido e nome (se endereço novo); recuperar nome/identidade se pet existente.

**Regras de negócio:** §4.1, §6.1  
**Critérios de aceite:** CA-001, CA-002  
**Dependências:** CONTA-01, BTC-01 (validação endereço)  
**Notas técnicas:** Não exigir prova de propriedade do endereço (§4.1).

---

### CONTA-03 — Prazo imutável de troca de endereço (72h)

**Descrição:** Ao criar conta, persistir `addressChangeDeadline = accountCreatedAt + 72h`; rejeitar troca no instante exato do limite.

**Regras de negócio:** §5  
**Critérios de aceite:** CA-004  
**Dependências:** CONTA-02  
**Notas técnicas:** Tempo absoluto UTC; fuso não reinicia prazo.

---

### CONTA-04 — Múltiplas contas no mesmo endereço

**Descrição:** Permitir N contas vinculadas ao mesmo endereço; dados privados isolados por conta.

**Regras de negócio:** §4.1, §6.1  
**Critérios de aceite:** CA-002  
**Dependências:** CONTA-02  
**Notas técnicas:** Login não expõe dados privados de outras contas.

---

### CONTA-05 — Código de recuperação do aplicativo

**Descrição:** Gerar código aleatório alta entropia, copiável/baixável na emissão; armazenar apenas verificador (hash).

**Regras de negócio:** §4.3, **CC-02**  
**Critérios de aceite:** CA-006  
**Dependências:** CONTA-02  
**Notas técnicas:** Nunca logar o segredo; renovar código após uso.

---

### CONTA-06 — Recuperação sem e-mail antigo

**Descrição:** Validar código → verificar novo e-mail → revogar sessões → substituir código de recuperação.

**Regras de negócio:** §4.3, **CC-02**  
**Critérios de aceite:** CA-006  
**Dependências:** CONTA-05  
**Notas técnicas:** Preservar `addressChangeDeadline` original.

---

### CONTA-07 — Sessões e logout seguro

**Descrição:** Sessão HttpOnly; logout revoga sessão e limpa cache privado no PWA.

**Regras de negócio:** §4, §14.6  
**Critérios de aceite:** CA-067  
**Dependências:** CONTA-01, FUND-11  
**Notas técnicas:** CSRF token em mutações.

---

### CONTA-08 — Troca de endereço dentro da janela

**Descrição:** Permitir várias trocas antes de 72h; confirmação explícita na UI sobre efeitos.

**Regras de negócio:** §5  
**Critérios de aceite:** CA-003, CA-005  
**Dependências:** CONTA-03  
**Notas técnicas:** Operação atômica no backend.

---

### CONTA-09 — Isolamento na troca (CC-03)

**Descrição:** Ao trocar: encerrar vínculo anterior; apagar plano, compras, compromissos, reserva, preferências da config anterior; revogar avisos; remover compras da projeção pública; criar nova config; recuperar pet do endereço destino.

**Regras de negócio:** §5, **CC-03**  
**Critérios de aceite:** CA-003  
**Dependências:** CONTA-08, DCA (parcial), PUSH (revogação)  
**Notas técnicas:** Não apagar pet/sprites/transações públicas de terceiros.

---

### CONTA-10 — Bloqueio de troca após 72h (inclusive admin)

**Descrição:** API, UI e ferramentas admin rejeitam troca quando `agora >= addressChangeDeadline`.

**Regras de negócio:** §5, §17.3  
**Critérios de aceite:** CA-004  
**Dependências:** CONTA-03  
**Notas técnicas:** HTTP 403 com código de erro explícito.

---

### CONTA-11 — Permissão de renomear (somente criador)

**Descrição:** Apenas conta criadora vinculada pode alterar nome do pet; demais contas leem apenas.

**Regras de negócio:** §4.2, §6.3, **CC-06**  
**Critérios de aceite:** CA-007, CA-008  
**Dependências:** CONTA-04, PET (pet entity)  
**Notas técnicas:** Nome único por endereço.

---

### CONTA-12 — Preservação quando criador desvincula

**Descrição:** Se criador sai, nome permanece; direito de editar não transfere automaticamente; retorno do criador restaura permissão.

**Regras de negócio:** **CC-06**  
**Critérios de aceite:** CA-007  
**Dependências:** CONTA-11  

---

### CONTA-13 — Pet sem contas vinculadas (CC-04)

**Descrição:** Quando última conta desvincula, pet e dados públicos permanecem; suspender jobs exclusivos de conta; reconciliar sob demanda.

**Regras de negócio:** §5, **CC-04**  
**Critérios de aceite:** CA-010  
**Dependências:** PET, BTC  
**Notas técnicas:** Nova conta recupera mesmo pet.

---

### CONTA-14 — Exclusão de dados privados da conta (CC-22)

**Descrição:** Fluxo de exclusão: remover compras da projeção pública, revogar sessões/subscriptions, apagar associações pessoais; preservar fatos públicos e snapshots técnicos do pet.

**Regras de negócio:** §17.2, **CC-22**  
**Critérios de aceite:** — (CA-070 restore reaplica exclusões)  
**Dependências:** CONTA-07, PUSH, DCA  
**Notas técnicas:** Prazo operacional 24h dados ativos; backups 30 dias.

---

## Definition of Done (épico)

- [ ] CA-001 a CA-010 verificáveis em ambiente de teste
- [ ] Magic link e recuperação funcionam sem senha tradicional
- [ ] Troca atômica CC-03 com rollback em falha
- [ ] Admin não consegue trocar endereço após 72h
