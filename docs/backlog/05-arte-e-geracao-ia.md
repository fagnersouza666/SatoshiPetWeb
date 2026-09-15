# Épico 05 — Arte e Geração por IA

**Prioridade:** P1  
**Dependências:** FUND-03, PET-07, CONTA-11  
**Referência PRD:** §8

---

## Objetivo

Gerar criatura original em pixel art 2D por IA no nascimento, produzir conjunto coerente de sprites para todos os estados, fluxo de aprovação/regeneração única, persistência versionada e guardrails de conteúdo.

---

## Histórias

### ART-01 — Entidade PetArtwork e metadados

**Descrição:** Persistir contexto congelado (endereço seed, localização, clima, fuso), prompt privado, tentativas, versão assets, status aprovação.

**Regras de negócio:** §8.1, §8.2, §16.2  
**Critérios de aceite:** —  
**Dependências:** FUND-02, FUND-03  

---

### ART-02 — Pipeline de geração IA

**Descrição:** Job assíncrono: montar prompt com endereço, localização, clima, fauna/flora regional; chamar provedor IA; validar saída técnica.

**Regras de negócio:** §8.1  
**Critérios de aceite:** CA-036  
**Dependências:** ART-01, CLIMA (contexto), integração IA  
**Notas técnicas:** Prompt nunca exposto publicamente (§8.1).

---

### ART-03 — Referência visual principal e derivação de poses

**Descrição:** Gerar referência principal; derivar sprites: idle, olhar, sorriso, alimentação, comemoração, pensando, chateado, faminto, crítico, hibernação, sono, nascimento, retorno, movimento reduzido.

**Regras de negócio:** §8.2  
**Critérios de aceite:** CA-036  
**Dependências:** ART-02  
**Notas técnicas:** Mesma paleta/proporção; atlas PNG versionado.

---

### ART-04 — Validação técnica de sprites

**Descrição:** Verificar coerência, transparência, alinhamento, legibilidade; rejeitar conjunto inválido sem consumir regeneração voluntária.

**Regras de negócio:** §8.2, §8.3  
**Critérios de aceite:** CA-039  
**Dependências:** ART-03  

---

### ART-05 — Fluxo de aprovação e regeneração única

**Descrição:** Criador vinculado pode 1 regeneração antes de aprovar; segunda geração válida é definitiva; bloqueio pós-aprovação.

**Regras de negócio:** §8.3  
**Critérios de aceite:** CA-037  
**Dependências:** ART-03, CONTA-11  

---

### ART-06 — Preservar geração ao fechar app (CC-13)

**Descrição:** Primeira geração aguardando confirmação persiste; reabrir não dispara nova geração.

**Regras de negócio:** **CC-13**  
**Critérios de aceite:** CA-038  
**Dependências:** ART-05, PWA  

---

### ART-07 — Aprovação automática sem criador ativo

**Descrição:** Se nenhum criador vinculado, aprovar automaticamente primeiro conjunto válido para não bloquear pet compartilhado.

**Regras de negócio:** **CC-13**  
**Critérios de aceite:** —  
**Dependências:** ART-04, CONTA-13  

---

### ART-08 — Retentativa em falha técnica

**Descrição:** Falha de IA: manter ovo, status de geração, retentar com backoff; alertar operação em falhas persistentes.

**Regras de negócio:** §8.3  
**Critérios de aceite:** CA-039  
**Dependências:** ART-02, ADMIN (alertas)  

---

### ART-09 — Guardrails de conteúdo

**Descrição:** Filtrar/bloquear caricaturas ofensivas, sexual, violência explícita, marcas, pessoas reais; permitir estereótipos regionais leves.

**Regras de negócio:** §8.1  
**Critérios de aceite:** —  
**Dependências:** ART-02  

---

### ART-10 — Entrega de assets ao PWA

**Descrição:** URLs versionadas via object storage; cache `ngsw` por versão; `PET_ARTWORK_READY` via WebSocket.

**Regras de negócio:** §8.2, §14.6  
**Critérios de aceite:** CA-036, CA-040  
**Dependências:** FUND-03, FUND-11, ART-03  
**Notas técnicas:** Mudança de cidade não altera criatura (CA-040).

---

## Definition of Done (épico)

- [x] Nascimento dispara geração uma vez por endereço
- [x] CA-036 a CA-040 verificáveis
- [x] Regeneração voluntária limitada a 1
- [x] Falha técnica não consome sorteio
- [x] Sprites servidos ao PWA com cache versionado

**Recorte atual:** provedor `ImageGenerationPort` via stub determinístico (`stub-v1`);
adaptador pago entra em história futura. Clima real (épico CLIMA) enriquece o
contexto congelado sem regenerar arte aprovada.
