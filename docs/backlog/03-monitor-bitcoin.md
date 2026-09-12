# Épico 03 — Monitor Bitcoin

**Prioridade:** P1  
**Dependências:** FUND-02, FUND-04, FUND-06, FUND-09, CONTA-02  
**Referência PRD:** §9, §6.4 (dados públicos on-chain)

---

## Objetivo

Validar endereços Mainnet, monitorar saldo/transações via Esplora (porta para Bitcoin Core), tratar mempool, confirmações, RBF, reorganizações e backfill histórico paginado com reconciliação idempotente.

---

## Histórias

### BTC-01 — Validação de endereços Bitcoin

**Descrição:** Validar rede Mainnet, formato, checksum; suportar P2PKH, P2SH, SegWit nativo, Taproot; canonicalização interna.

**Regras de negócio:** §9.1  
**Critérios de aceite:** CA-001  
**Dependências:** FUND-01  
**Notas técnicas:** bitcoinj ou equivalente; sem manipulação incorreta de Bech32 case.

---

### BTC-02 — Porta `BitcoinIndexerPort` e adaptador Esplora

**Descrição:** Interface desacoplada + implementação Blockstream Esplora; contrato para futura migração Bitcoin Core.

**Regras de negócio:** §16.1, §23  
**Critérios de aceite:** —  
**Dependências:** FUND-01  
**Notas técnicas:** Um backend Bitcoin operacional por vez.

---

### BTC-03 — Consulta de saldo confirmado e pendências

**Descrição:** Saldo confirmado principal; entradas/saídas pendentes separadas; estados operacionais de sync.

**Regras de negócio:** §9.1, §9.2  
**Critérios de aceite:** CA-011, CA-041 (saldo exibição)  
**Dependências:** BTC-02  
**Notas técnicas:** Saldo desconhecido ≠ zero (CC-12).

---

### BTC-04 — Persistência de transações, saídas e spends

**Descrição:** Entidades `BitcoinTransaction`, `BitcoinOutput`, `BitcoinSpend` com unicidade txid+vout.

**Regras de negócio:** §16.2, §9.3  
**Critérios de aceite:** CA-033  
**Dependências:** FUND-02, BTC-02  

---

### BTC-05 — Recebimento lógico agregado

**Descrição:** Agrupar saídas ao endereço na mesma tx em um `LogicalReceipt`; somar vouts antes de alimentação.

**Regras de negócio:** §9.2, **CC-10**  
**Critérios de aceite:** CA-017  
**Dependências:** BTC-04  

---

### BTC-06 — Detecção mempool e confirmação

**Descrição:** Observar tx na mempool; status MEMPOOL → CONFIRMED; pet visível reage com pendência; confirmação não duplica energia.

**Regras de negócio:** §9.2, **CC-14** (ovo vs pet visível)  
**Critérios de aceite:** CA-012, CA-028  
**Dependências:** BTC-05, PET (integração)  
**Notas técnicas:** Detectar tx vista direto em bloco sem passar mempool.

---

### BTC-07 — Backfill histórico paginado

**Descrição:** Importar histórico confirmado integral; paginar (25 tx/página Esplora); cursor altura/hash; progresso persistido.

**Regras de negócio:** §9.4, §23  
**Critérios de aceite:** CA-014, CA-033  
**Dependências:** BTC-02, BTC-04  
**Notas técnicas:** Respeitar limite 50 tx mempool por endereço.

---

### BTC-08 — Reconciliação idempotente e retomada

**Descrição:** Após falha, reconciliar desde ponto seguro com overlap; reprocessar idempotentemente; emitir `BITCOIN_BALANCE_RECONCILED`.

**Regras de negócio:** §9.4  
**Critérios de aceite:** CA-033, CA-025  
**Dependências:** BTC-07, FUND-04  

---

### BTC-09 — RBF e substituição de transação

**Descrição:** Rastrear conflitos por entradas gastas; atualizar recebimento lógico; recalcular alimentação sem segunda refeição.

**Regras de negócio:** §9.3  
**Critérios de aceite:** CA-029, CA-030  
**Dependências:** BTC-05, PET  

---

### BTC-10 — Transação descartada e invalidação

**Descrição:** Estados DROPPED/UNKNOWN; invalidar alimentação; reconstruir reserva cronologicamente (não só subtrair crédito).

**Regras de negócio:** §9.3  
**Critérios de aceite:** CA-030, CA-031  
**Dependências:** BTC-09, PET  
**Notas técnicas:** Ausência de resposta ≠ descarte comprovado.

---

### BTC-11 — Reorganização de chain

**Descrição:** Tratar `BITCOIN_CHAIN_REORG`; recalcular confirmações, saldo, carência ovo, alimentação; preservar trilha.

**Regras de negócio:** §9.3, §7.5  
**Critérios de aceite:** CA-032  
**Dependências:** BTC-08, PET  

---

### BTC-12 — Job de polling e webhook (se disponível)

**Descrição:** Polling periódico por endereço ativo; backoff; métricas de atraso de monitoramento.

**Regras de negócio:** §18.1, §18.2  
**Critérios de aceite:** — (meta p95 monitor→cliente ≤ 5s)  
**Dependências:** BTC-02, FUND-06  

---

### BTC-13 — API pública on-chain

**Descrição:** Endpoints públicos: saldo, pendências, histórico completo entradas/saídas, QR data — sem payload privado.

**Regras de negócio:** §6.4, §14.3  
**Critérios de aceite:** CA-009  
**Dependências:** BTC-03, PROJ (épico PWA)  
**Notas técnicas:** DTO público explícito; link explorador por tx.

---

### BTC-14 — Histórico com taxa total da transação

**Descrição:** Persistir e exibir taxa total da tx; não afirmar que foi paga pelo recebedor.

**Regras de negócio:** §9.4  
**Critérios de aceite:** —  
**Dependências:** BTC-04  

---

### BTC-15 — Testes regtest: tx, RBF, reorg

**Descrição:** Suite de integração com regtest simulando cenários CA-028..032.

**Regras de negócio:** §16.4, §19.3  
**Critérios de aceite:** CA-028, CA-029, CA-030, CA-031, CA-032  
**Dependências:** FUND-09, BTC-09, BTC-10, BTC-11  

---

### BTC-16 — Emissão de eventos padronizados Bitcoin

**Descrição:** Publicar eventos §16.3 via outbox/WebSocket para cada transição de estado.

**Regras de negócio:** §16.3  
**Critérios de aceite:** —  
**Dependências:** FUND-04, BTC-06  

**Catálogo:** [eventos Bitcoin](../contratos/eventos-bitcoin.md) — versão 1.0.

O monitor cobre diretamente as seis transições Bitcoin abaixo. Eventos de pet,
DCA, compra e localização permanecem nos respectivos contextos:

| Evento | Transição monitorada |
| --- | --- |
| `BITCOIN_TRANSACTION_OBSERVED` | Não registrada → observada na mempool ou diretamente em bloco |
| `BITCOIN_TRANSACTION_CONFIRMED` | Observada/mempool ou descoberta direta → confirmada |
| `BITCOIN_TRANSACTION_REPLACED` | Transação observada → substituída com evidência de conflito/RBF |
| `BITCOIN_TRANSACTION_DROPPED` | Transação conhecida → descartada após evidência e reconciliação |
| `BITCOIN_CHAIN_REORG` | Bloco/confirmações observados → cadeia reorganizada |
| `BITCOIN_BALANCE_RECONCILED` | Ponto seguro → saldo e histórico reconciliados |

O catálogo nomeia eventos e transições. A projeção pública e a redaction do
payload estão definidas em [eventos-bitcoin-redaction.md](../contratos/eventos-bitcoin-redaction.md);
envelope, correlação e transporte permanecem nos contratos próprios. Ausência
de resposta do provedor não é descarte, e uma compra declarada nunca dispara
`PET_FEEDING_APPLIED`.

---

## Definition of Done (épico)

- [ ] Endereço válido Mainnet aceito; inválido rejeitado
- [ ] Backfill completo sem duplicidade (CA-017, CA-033)
- [ ] RBF e reorg recalculam pet corretamente
- [ ] Falha de provedor não zera saldo (CA-031)
- [ ] Dados on-chain públicos sem vazamento privado (CA-009)
