# Épico 04 — Motor do Pet

**Prioridade:** P1  
**Dependências:** BTC-05..11, CONTA-04, DCA (porção referência — parcial)  
**Referência PRD:** §6.2, §7

---

## Objetivo

Motor compartilhado de alimentação, reserva (máx 168h), estados emocionais, nascimento/ovo, carência de saldo zero, fonte alimentar única (CC-05), reconstrução histórica e fila de apresentação por conta.

---

## Histórias

### PET-01 — Entidade Pet e estado base

**Descrição:** `Pet` único por endereço: criador, nome, `zeroBalanceSince`, fonte referência alimentar, timestamps nascimento.

**Regras de negócio:** §16.2, §6.1  
**Critérios de aceite:** CA-002  
**Dependências:** FUND-02, CONTA-02  
**Notas técnicas:** Colunas do motor em `V5__create_pet_engine.sql` (reserva NUMERIC, estado emocional, apresentação ovo/criatura, fonte alimentar CC-05). `Pet.create` inicia ovo, reserva 0 e fonte = criador.  

---

### PET-02 — Fonte alimentar única (CC-05)

**Descrição:** Porção 24h do pet vem do plano da conta criadora; fallback para conta vinculada há mais tempo; manter última porção positiva.

**Regras de negócio:** §6.2, **CC-05**  
**Critérios de aceite:** CA-002 (estado compartilhado)  
**Dependências:** PET-01, DCA-15 (porção referência)  
**Notas técnicas:** Conta secundária vê "Sua sugestão" vs "Porção 24h do pet". Histórico de porção em `pet_reference_portions` (`V5__create_pet_engine.sql`). Resolução via `PetReferencePortionPort` / `PersistentPetReferencePortionPort`: porção vigente é da fonte alimentar ativa (criador, depois vínculo mais antigo); snapshot de outra conta é histórico imutável e não atualiza `lastPositive*` nem vira current enquanto a fonte original estiver vinculada; sem snapshot da nova fonte (após troca ou sem vínculos) usa `lastPositivePortionSats` com a conta que gerou essa porção (CA-055); sem porção positiva da fonte ativa devolve vazio (CC-11 / CA-026). `refreshFoodSource` não reescreve snapshots nem transfere direitos de nome.

---

### PET-03 — Cálculo de reserva e duração

**Descrição:** `horasAcrescentadas = 24 × satsRecebidos / porcaoReferenciaSats`; cap 168h; precisão decimal CC-10; agregar vouts antes do cálculo.

**Regras de negócio:** §7.2, **CC-10**  
**Critérios de aceite:** CA-015, CA-016, CA-018  
**Dependências:** PET-02, BTC-05  
**Notas técnicas:** `PetEngine` aplica `ReserveMath.hoursAdded` + `applyCap` (168h) ao creditar `PetFeeding`. `BitcoinMonitorService` notifica `PetLifecyclePort` na mesma transação do `LogicalReceipt` (`onReceiptObserved`/`Confirmed`/`Invalidated`, `onBalanceKnown`, `onProviderFailure`); sem pet no endereço, o monitor segue sem lançar.

---

### PET-04 — Consumo temporal da reserva

**Descrição:** Antes de cada evento, consumir reserva pelo tempo transcorrido; esgotada não acumula "dívida de fome".

**Regras de negócio:** §7.2  
**Critérios de aceite:** CA-018, CA-019  
**Dependências:** PET-03  
**Notas técnicas:** `PetEngine.tick` e os handlers de recebimento (quando há porção) consomem via `ReserveClock.consume` antes de creditar; fome não gera dívida (CA-018).

---

### PET-05 — Estados emocionais

**Descrição:** ALIMENTADO, PENSANDO (0-24h esgotado), CHATEADO (24-48h), FAMINTO (48-72h), CRÍTICO (72-96h), HIBERNANDO (96h+).

**Regras de negócio:** §7.1  
**Critérios de aceite:** CA-019  
**Dependências:** PET-04  

---

### PET-06 — Persistência PetFeeding e revisões

**Descrição:** Registrar alimentação por recebimento lógico: valor, porção, duração, instante, versão regra, status provisório/válido/invalidado.

**Regras de negócio:** §7.2, §9.3  
**Critérios de aceite:** CA-017, CA-029, CA-030  
**Dependências:** PET-03, BTC-05  
**Notas técnicas:** Tabela `pet_feedings` em `V5__create_pet_engine.sql`; UNIQUE `(pet_id, logical_receipt_id)` (CA-017). `PetEngine` (`PetLifecyclePort`) persiste LIVE idempotente; RBF via `onReceiptRevised` (delta de duração); invalidação marca `INVALIDATED` e devolve horas creditadas sem apagar a linha. Sem porção positiva (CC-11) não cria alimentação.  

---

### PET-07 — Primeiro cadastro: ovo e condições de nascimento

**Descrição:** Saldo zero → ovo padrão; pendência move ovo; nascimento exige saldo confirmado positivo + confirmação (CC-14).

**Regras de negócio:** §7.3, **CC-14**  
**Critérios de aceite:** CA-011, CA-012, CA-013  
**Dependências:** BTC-06, ART (geração)  

---

### PET-08 — Reconstrução histórica no cadastro

**Descrição:** Com saldo positivo e histórico antigo: nascer, reconstruir estado, sem comemorar txs antigas na fila.

**Regras de negócio:** §7.4  
**Critérios de aceite:** CA-014, CA-045 (histórico)  
**Dependências:** PET-07, BTC-07  

---

### PET-09 — Aguardando referência do plano (CC-11)

**Descrição:** Sem porção positiva: armazenar recebimentos, exibir "Aguardando referência"; reconstruir quando houver primeira porção.

**Regras de negócio:** **CC-11**  
**Critérios de aceite:** CA-026, CA-027  
**Dependências:** PET-02, DCA  
**Notas técnicas:** `PetReferencePortionPort.currentPositivePortion` devolve vazio enquanto não houver snapshot da fonte nem `lastPositivePortionSats` > 0; não inventa 10.000 sats nem plano DCA. `PetEngine` em recebimento sem porção não cria `PetFeeding` nem altera reserva (CA-026 lite); reconstrução histórica é Task 7.

---

### PET-10 — Referência técnica para período pré-plano

**Descrição:** Usar primeira porção positiva calculada para reconstruir histórico anterior; marcar origem; sem DCA fictício.

**Regras de negócio:** §7.4  
**Critérios de aceite:** CA-027  
**Dependências:** PET-09  

---

### PET-11 — Carência saldo zero 24h e retorno ao ovo

**Descrição:** `retornarAoOvo` após 24h contínuas saldo confirmado zero; reserva continua consumindo; arte preservada.

**Regras de negócio:** §7.5, **CC-12**  
**Critérios de aceite:** CA-020, CA-021, CA-022, CA-023  
**Dependências:** BTC-03, PET-05  

---

### PET-12 — Reaparecimento da mesma criatura

**Descrição:** Novo recebimento confirmado → mesma aparência; não chamar IA novamente.

**Regras de negócio:** §7.5  
**Critérios de aceite:** CA-024  
**Dependências:** PET-11, ART  

---

### PET-13 — Reorg e exceção primeiro nascimento

**Descrição:** Reorg reavalia carência; exceção ovo imediato se único fundamento de nascimento invalidado.

**Regras de negócio:** §7.5  
**Critérios de aceite:** CA-032 (parcial pet)  
**Dependências:** BTC-11, PET-11  

---

### PET-14 — Mempool: alimentação provisória vs ovo

**Descrição:** Pet visível: provisório na mempool, consolidado na confirmação. Ovo: energia vale na confirmação (CC-14).

**Regras de negócio:** **CC-14**  
**Critérios de aceite:** CA-028  
**Dependências:** BTC-06, PET-07  
**Notas técnicas:** `PetEngine` (CC-14 lite): ovo + pendente → `PROVISIONAL` com `durationHours=0` e `presentable=false`; confirmação credita horas e marca `VALID` (apresentação permanece EGG até EggPolicy). Criatura + pendente credita já em `PROVISIONAL`; confirmação só promove a `VALID` — não recalcula `amount`/`portion`/`duration` nem aplica `creditDelta` (CA-028), mesmo se a porção de referência mudar entre mempool e confirmação.

---

### PET-15 — Fila de apresentação e cursor (CC-15)

**Descrição:** `PresentationCursor` por conta; reproduzir comemorações desde última visita; "Pular animações" marca cursor sem alterar energia.

**Regras de negócio:** §9.5, **CC-15**  
**Critérios de aceite:** CA-034, CA-035  
**Dependências:** FUND-05, PWA  
**Notas técnicas:** Visitante usa cursor local; sincronizar entre dispositivos da mesma conta. Persistência em `presentation_cursors` (`V5__create_pet_engine.sql`), um cursor por conta.

---

### PET-16 — Eventos de pet padronizados

**Descrição:** Emitir `PET_BORN`, `PET_RETURNED_TO_EGG`, `PET_REAPPEARED`, `PET_STATE_CHANGED`, `PET_FEEDING_*`.

**Regras de negócio:** §16.3  
**Critérios de aceite:** —  
**Dependências:** FUND-04  

---

### PET-17 — Estatísticas de pet (idade, tempo por estado)

**Descrição:** Idade não reinicia no ovo; distinguir período reconstruído vs observado (CC-21).

**Regras de negócio:** §14.3, **CC-21**  
**Critérios de aceite:** — (dashboard PWA)  
**Dependências:** PET-05, PET-08  

---

### PET-18 — Estado correto com app fechado

**Descrição:** Consumo de reserva e carência baseados em relógio do servidor; reconciliar ao reabrir.

**Regras de negócio:** §7, §2 princípio 5  
**Critérios de aceite:** CA-025  
**Dependências:** PET-04, PET-11  

---

## Definition of Done (épico)

- [ ] CA-011 a CA-027, CA-034, CA-035 verificáveis
- [ ] Duas contas veem mesmo estado emocional e reserva
- [ ] Porção referência CC-05 consistente entre contas
- [ ] Invalidação RBF/reorg reconstrói reserva corretamente
- [ ] Ovo/carência CC-12 conforme saldo confirmado
