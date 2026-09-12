# Épico 09 — Notificações

**Prioridade:** P3  
**Dependências:** PWA-01, CONTA-07, PET, BTC, DCA  
**Referência PRD:** §15

---

## Objetivo

Web Push com 4 categorias habilitadas por padrão, mensagens contextualmente corretas, idempotência, expiração e revogação de subscriptions inválidas.

---

## Histórias

### PUSH-01 — Entidades Notification e Subscription

**Descrição:** Persistir destinatário, categoria, evento, envio, expiração, device endpoint; VAPID no servidor.

**Regras de negócio:** §15, §16.2  
**Critérios de aceite:** —  
**Dependências:** FUND-02  

---

### PUSH-02 — Registro de subscription no PWA

**Descrição:** Após consentimento do navegador, registrar subscription; associar à conta; instruções quando negado.

**Regras de negócio:** §15  
**Critérios de aceite:** CA-066  
**Dependências:** PUSH-01, PWA-01  

---

### PUSH-03 — Preferências por categoria

**Descrição:** 4 categorias habilitadas inicialmente: recomendação diária, recebimento pendente, confirmação, mudança de estado; toggles individuais.

**Regras de negócio:** §15  
**Critérios de aceite:** CA-065  
**Dependências:** PUSH-02  

---

### PUSH-04 — Notificação recomendação DCA

**Descrição:** Uma vez por sugestão gerada (08h ou tardia); não notificar coletas 06h/07h; abrir registro correspondente.

**Regras de negócio:** §15  
**Critérios de aceite:** CA-058, CA-061  
**Dependências:** DCA-05, PUSH-03  

---

### PUSH-05 — Notificações recebimento pendente/confirmado

**Descrição:** Mensagens com valor sats; ovo pendente não anunciado como nascido.

**Regras de negócio:** §15  
**Critérios de aceite:** CA-028 (UX complementar)  
**Dependências:** BTC-06, PUSH-03  

---

### PUSH-06 — Notificações mudança de estado do pet

**Descrição:** Fome progressiva, retorno ao ovo, etc.; respeitar estado real (ovo vs criatura).

**Regras de negócio:** §15  
**Critérios de aceite:** —  
**Dependências:** PET-05, PUSH-03  

---

### PUSH-07 — Idempotência e fila de envio

**Descrição:** Chave idempotente por conta+evento+categoria; retry controlado; expirar mensagens obsoletas; sem replay em massa pós-reconciliação.

**Regras de negócio:** §15  
**Critérios de aceite:** —  
**Dependências:** FUND-04, PUSH-01  

---

### PUSH-08 — Aviso corretivo único

**Descrição:** Correção de recebimento já notificado pode gerar um aviso corretivo; não duplicar celebração.

**Regras de negócio:** §15  
**Critérios de aceite:** CA-030 (complementar)  
**Dependências:** BTC-10, PUSH-07  

---

## Definition of Done (épico)

- [ ] CA-065, CA-066 verificáveis
- [ ] Push nunca inclui e-mail, observações, corretora ou código recuperação
- [ ] Subscription revogada em logout e exclusão conta
- [ ] Sugestão DCA notificada no máximo uma vez por ciclo
