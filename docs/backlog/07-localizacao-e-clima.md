# Épico 07 — Localização e Clima

**Prioridade:** P2  
**Dependências:** CONTA-02, FUND-06  
**Referência PRD:** §13, §6.3 (CC-07)

---

## Objetivo

Geolocalização automática/manual, fusos IANA, clima e transições solares para ambiente autenticado e projeção pública; mudanças aplicadas no dia seguinte.

---

## Histórias

### CLIMA-01 — Entidade Location por conta

**Descrição:** Cidade, coordenadas arredondadas, fonte (auto/manual), fuso ativo, mudança agendada.

**Regras de negócio:** §13.1, §16.2  
**Critérios de aceite:** —  
**Dependências:** FUND-02  

---

### CLIMA-02 — Geolocalização automática (PWA)

**Descrição:** Solicitar permissão após ação do usuário; enviar coordenadas arredondadas ao backend; fallback última válida.

**Regras de negócio:** §13.1  
**Critérios de aceite:** CA-064  
**Dependências:** CLIMA-01, PWA  

---

### CLIMA-03 — Seleção manual de cidade

**Descrição:** Busca cidade/estado/país; distinguir homônimos; manual permanece até retorno ao automático.

**Regras de negócio:** §13.1  
**Critérios de aceite:** CA-064  
**Dependências:** CLIMA-01  

---

### CLIMA-04 — Cadeia de fallback de fuso

**Descrição:** Fuso da localização → dispositivo → `America/Sao_Paulo`.

**Regras de negócio:** §13.1, resposta 120 PRD  
**Critérios de aceite:** CA-064  
**Dependências:** CLIMA-01  

---

### CLIMA-05 — Mudança agendada para dia seguinte

**Descrição:** Alteração de localização/fuso registrada como pendente; aplicar no dia seguinte; não alterar contexto congelado de nascimento.

**Regras de negócio:** §13.1, §11.3  
**Critérios de aceite:** CA-062  
**Dependências:** CLIMA-01, DCA-13  

---

### CLIMA-06 — Integração provedor de clima

**Descrição:** Temperatura °C, condição, min/max, umidade, sensação; mapear códigos para representações PRD §13.2.

**Regras de negócio:** §13.2  
**Critérios de aceite:** —  
**Dependências:** FUND-06  

---

### CLIMA-07 — WeatherSnapshot e atualização 15 min

**Descrição:** Persistir snapshot; atualizar a cada 15 min, mudança efetiva de local, refresh manual limitado, retomada longa suspensão.

**Regras de negócio:** §13.2  
**Critérios de aceite:** —  
**Dependências:** CLIMA-06  

---

### CLIMA-08 — Transições solares (dia/noite)

**Descrição:** Nascer/pôr locais para amanhecer, dia, entardecer, noite; tratar dias polares conforme provedor.

**Regras de negócio:** §13.2  
**Critérios de aceite:** —  
**Dependências:** CLIMA-06  

---

### CLIMA-09 — Falha de clima

**Descrição:** Manter último snapshot com aviso; após 2h: "Clima indisponível" e cenário neutro dia/noite; sem inventar dados.

**Regras de negócio:** §13.2  
**Critérios de aceite:** —  
**Dependências:** CLIMA-07  

---

### CLIMA-10 — Localização pública de referência (CC-07)

**Descrição:** Página pública usa cidade da conta referência alimentar; sem conta ativa, última cidade pública; sem coordenadas exatas.

**Regras de negócio:** **CC-07**, §6.4  
**Critérios de aceite:** CA-009  
**Dependências:** CLIMA-01, PET-02, PWA  

---

## Definition of Done (épico)

- [ ] Auto e manual funcionam com fallback
- [ ] Mudança de fuso não duplica sugestão DCA (CA-062, CA-063)
- [ ] Clima não altera alimentação do pet
- [ ] Página pública exibe cidade/clima sem coordenadas precisas
- [ ] Contexto de nascimento da criatura permanece congelado
