# Critérios de Aceite e Qualidade

**Versão:** 1.0  
**Data:** 11/09/2026  
**Referência:** PRD v2.0 §18–19

---

## 1. Metas de SLO (PRD §18.1)

| Indicador | Alvo | Verificação |
|-----------|------|-------------|
| Disponibilidade backend | 99,5% mensal | Monitoramento ADMIN-01 |
| Monitor → cliente WS | p95 ≤ 5 s, p99 ≤ 10 s | Load test 100 conexões |
| Commit evento → cliente | p95 ≤ 500 ms | Métricas FUND-05 |
| LCP inicial PWA | p75 ≤ 2,5 s | Lighthouse perfil §18.1 |
| Integridade | Zero duplicidade alimentação/sugestão | CAs abaixo + regtest |
| RPO / RTO | 24 h / 4 h | ADMIN-07, ADMIN-08 |
| Perfil carga | 100 contas/endereços/conexões | Ambiente staging dedicado |

**Perfil de teste adotado:** mobile + desktop atuais, rede 10 Mbps, RTT 100 ms.

---

## 2. Estratégia de testes

| Camada | Ferramenta | Escopo |
|--------|------------|--------|
| Backend unitário | JUnit 5 | Motor pet, DCA, validações |
| Backend integração | Testcontainers (PostgreSQL) | Repositórios, outbox, migrations |
| Bitcoin | regtest (FUND-09) | CA-028..032, CA-017 |
| Frontend unitário | Vitest/Jasmine | Componentes pet, DCA, pipes |
| Frontend e2e | Playwright | Jornadas PWA críticas |
| Acessibilidade | axe-core + manual | CA-069 |
| Contrato API | OpenAPI + testes | DTOs públicos vs privados |

**Ambientes Bitcoin:** regtest/signet em CI; Mainnet apenas staging manual controlado e produção.

---

## 3. Matriz CA-001..CA-070

### 3.1 Conta e compartilhamento (§19.1)

| ID | Resultado esperado (resumo) | Histórias | Épico |
|----|----------------------------|-----------|-------|
| **CA-001** | Registro cria conta/vínculo/pet; sem chave ou prova de controle | CONTA-01, CONTA-02, BTC-01 | 02 |
| **CA-002** | Duas contas, mesmo endereço: mesmo pet; dados privados isolados | CONTA-04, PET-01, PWA-11 | 02, 04, 08 |
| **CA-003** | Troca em 71h59min: aceita, apaga config pessoal, preserva pet público | CONTA-08, CONTA-09, PWA-13 | 02, 08 |
| **CA-004** | Troca em 72h+ rejeitada (inclusive admin) | CONTA-10, ADMIN-04, PWA-13 | 02, 08, 10 |
| **CA-005** | Várias trocas antes do limite; prazo original mantido | CONTA-08, PWA-13 | 02, 08 |
| **CA-006** | Recuperação por código sem e-mail antigo; revoga sessões; novo código | CONTA-05, CONTA-06, PWA-24 | 02, 08 |
| **CA-007** | Visitante/2ª conta não renomeia nem regenera | CONTA-11, ART-05, ADMIN-04 | 02, 05, 10 |
| **CA-008** | Criador renomeia em todas as visualizações | CONTA-11, PWA-06 | 02, 08 |
| **CA-009** | Página pública: campos permitidos; 10 compras data/R$; sem privados | PWA-04, BTC-13, DCA-16, DCA-17, CLIMA-10 | 08, 03, 06, 07 |
| **CA-010** | Sem contas → re-registro recupera mesmo pet | CONTA-13, PET-01 | 02, 04 |

### 3.2 Nascimento, reserva e saldo (§19.2)

| ID | Resultado esperado (resumo) | Histórias | Épico |
|----|----------------------------|-----------|-------|
| **CA-011** | Endereço novo saldo zero: ovo; sem criatura | PET-07, PWA-05, BTC-03 | 04, 08, 03 |
| **CA-012** | Ovo + entrada pendente: movimento + mensagem; sem nascimento | PET-07, PET-14, PWA-05 | 04, 08 |
| **CA-013** | Primeiro saldo positivo confirmado: arte, nascimento, alimentação 1x | PET-07, ART-02, PWA-05 | 04, 05, 08 |
| **CA-014** | Cadastro saldo positivo + histórico: nascer + reconstruir; sem comemorar antigas | PET-08, BTC-07, PET-15 | 04, 03 |
| **CA-015** | Porção 20k sats + 5k recebidos = +6h reserva | PET-03 | 04 |
| **CA-016** | Reserva 6 dias + entrada 3 dias → cap 7 dias | PET-03 | 04 |
| **CA-017** | 3 observações mesma tx 1k+2k → 1 alimentação 3k | BTC-05, PET-06 | 03, 04 |
| **CA-018** | Faminto vários dias + porção: +24h sem descontar dívida | PET-03, PET-04 | 04 |
| **CA-019** | Esgotamento 0/24/48/72/96h → estados corretos | PET-05, PWA-06 | 04, 08 |
| **CA-020** | Saída só pendente: não inicia carência ovo | PET-11, BTC-03 | 04, 03 |
| **CA-021** | Saldo confirmado zero: inicia carência; criatura visível <24h | PET-11, PWA-06 | 04, 08 |
| **CA-022** | 24h saldo zero: ovo mesmo com energia; arte preservada | PET-11, PWA-05, PWA-06 | 04, 08 |
| **CA-023** | Saldo positivo antes 24h zera de novo: nova contagem | PET-11 | 04 |
| **CA-024** | Ovo pós-nascimento + confirmação: mesma criatura | PET-12, ART-10 | 04, 05 |
| **CA-025** | App fechado: estado correto ao reabrir (servidor) | PET-18, PWA-03, BTC-08 | 04, 08, 03 |
| **CA-026** | Sem porção positiva: aguardando referência; fatos armazenados | PET-09, PWA-06 | 04, 08 |
| **CA-027** | Primeira porção: reconstruir histórico com referência identificada | PET-10, PET-09, DCA-15 | 04, 06 |

### 3.3 Transações, arte e apresentação (§19.3)

| ID | Resultado esperado (resumo) | Histórias | Épico |
|----|----------------------------|-----------|-------|
| **CA-028** | Mempool pet visível: provisório + msg; confirmação não duplica | PET-14, BTC-06, PWA-05, PUSH-05 | 04, 03, 08, 09 |
| **CA-029** | RBF preserva recebimento, altera valor, recalcula energia | BTC-09, PET-06 | 03, 04 |
| **CA-030** | RBF remove/descarte: invalidar + reconstruir sequência | BTC-10, PET-06, PUSH-08 | 03, 04, 09 |
| **CA-031** | Falha provedor: não tratar como descarte ou saldo zero | BTC-10, BTC-03 | 03 |
| **CA-032** | Reorg: recalcular confirmações, saldo, carência, alimentação | BTC-11, PET-13 | 03, 04 |
| **CA-033** | Retomada pós-falha: paginar, reconciliar, sem duplicidade | BTC-07, BTC-08, FUND-04 | 03, 01 |
| **CA-034** | 3 recebimentos novos: 3 comemorações individuais | PET-15, PWA-08 | 04, 08 |
| **CA-035** | Pular animações: encerra apresentação; energia intacta | PET-15, PWA-08 | 04, 08 |
| **CA-036** | Sprites: mesma criatura/paleta em todos estados | ART-03, ART-04, PWA-05 | 05, 08 |
| **CA-037** | 1 regeneração antes aprovar; 2ª definitiva | ART-05, PWA-09 | 05, 08 |
| **CA-038** | Fechar antes aprovar: preserva 1ª geração | ART-06, PWA-09 | 05, 08 |
| **CA-039** | Falha IA: ovo + retry; não consome sorteio | ART-08, PWA-09 | 05, 08 |
| **CA-040** | Mudança cidade pós-aprovação: cenário muda; criatura não | CLIMA-05, PWA-18, ART-10 | 07, 08, 05 |

### 3.4 DCA e contabilidade (§19.4)

| ID | Resultado esperado (resumo) | Histórias | Épico |
|----|----------------------------|-----------|-------|
| **CA-041** | Índice 0–100 → exatamente uma das 11 faixas | DCA-02, DCA-07 | 06 |
| **CA-042** | Índice 17, base R$20 → teórico R$32 (×1,60) | DCA-02, DCA-07, PWA-10 | 06, 08 |
| **CA-043** | Teórico R$32, disponível R$25 → sugerir R$25 + explicar | DCA-07, PWA-10 | 06, 08 |
| **CA-044** | Reserva virtual negativa + orçamento: sugestão positiva ok | DCA-07, DCA-10 | 06 |
| **CA-045** | Sugestão positiva: compromete orçamento; trava cotação/sats | DCA-09, DCA-11 | 06 |
| **CA-046** | Meia-noite: compromisso mantido até 08h | DCA-09 | 06 |
| **CA-047** | 08h vencimento: libera compromisso anterior antes nova sugestão | DCA-09, DCA-05 | 06 |
| **CA-048** | COMPREI OUTRO VALOR acima orçamento: valor real + déficit | DCA-12, DCA-20, PWA-10 | 06, 08 |
| **CA-049** | Compra avulsa/atrasada: data real; recalcula posteriores | DCA-12, PWA-16 | 06, 08 |
| **CA-050** | Correção/exclusão compra: recalcula; preserva snapshots | DCA-12, DCA-18, PWA-16 | 06, 08 |
| **CA-051** | Aporte R$600, compras R$500 → carrega R$100 | DCA-08 | 06 |
| **CA-052** | Aporte R$600, compras R$650 → carrega -R$50; próximo R$550 capacidade | DCA-08 | 06 |
| **CA-053** | Compromisso atravessa mês: transporta sem duplicar | DCA-09 | 06 |
| **CA-054** | Base R$20, compra R$5/R$30 → reserva +15/-10 | DCA-10 | 06 |
| **CA-055** | Sugestão zero/indisponível: pet usa última porção positiva | DCA-11, DCA-15, PET-02 | 06, 04 |
| **CA-056** | Compra manual sem recebimento: zero alimentação | DCA-12 | 06 |
| **CA-057** | Alterar plano após sugestão: vale próxima; referência corrente mantida | DCA-01, DCA-18, PWA-12 | 06, 08 |

### 3.5 Tempo, falhas, privacidade e dispositivos (§19.5)

| ID | Resultado esperado (resumo) | Histórias | Épico |
|----|----------------------------|-----------|-------|
| **CA-058** | 06h/07h/08h: coletas; sugestão/notificação só 08h | DCA-05, PUSH-04, ADMIN-02 | 06, 09, 10 |
| **CA-059** | Falha 08h + leitura 07h válida: usar armazenada; exibir horários | DCA-06, PWA-10 | 06, 08 |
| **CA-060** | Leitura limite 06h ontem ok; anterior rejeitada | DCA-06 | 06 |
| **CA-061** | Sem dados válidos → tardia no ciclo; aviso único | DCA-14, PUSH-04 | 06, 09 |
| **CA-062** | Mudança local pós-recomendação: fuso dia seguinte | CLIMA-05, DCA-13 | 07, 06 |
| **CA-063** | Fuso volta data já recomendada: não duplicar | DCA-13 | 06 |
| **CA-064** | Geo falha: última válida; fallback fuso | CLIMA-02, CLIMA-04 | 07 |
| **CA-065** | Push madrugada permitido se categoria habilitada | PUSH-03, PUSH-04 | 09 |
| **CA-066** | Push não autorizado: não enviar; evento na UI | PUSH-02, PWA-10 | 09, 08 |
| **CA-067** | Logout + login outra conta: sem cache privado anterior | CONTA-07, FUND-11, PWA-21 | 02, 01, 08 |
| **CA-068** | Sem install/push/fullscreen: consulta básica + explicação | PWA-01, PWA-02, PWA-19, PWA-22 | 08 |
| **CA-069** | reduced-motion: estados legíveis sem animação intensa | PWA-23 | 08 |
| **CA-070** | Restore backup: identidade, contabilidade, snapshots; reaplicar exclusões; reconciliar rede | ADMIN-07, ADMIN-08, CONTA-14 | 10, 02 |

---

## 4. Cobertura por épico

| Épico | CAs principais | Qtd CAs |
|-------|----------------|---------|
| 01 Fundação | CA-033, CA-067, CA-070 (base) | 3 |
| 02 Contas | CA-001..010, CA-067, CA-070 | 12 |
| 03 Bitcoin | CA-009, CA-017, CA-028..033 | 8 |
| 04 Pet | CA-011..027, CA-028, CA-034, CA-035 | 19 |
| 05 Arte | CA-036..040 | 5 |
| 06 DCA | CA-041..057, CA-058..063 | 23 |
| 07 Clima | CA-009, CA-040, CA-062..064 | 5 |
| 08 PWA | Transversal — maioria dos CAs tem UI | 40+ |
| 09 Push | CA-065, CA-066, CA-058, CA-061 | 4 |
| 10 Admin | CA-004, CA-007, CA-070 | 3 |

> Vários CAs são transversais e exigem integração entre épicos. A verificação final ocorre em suite E2E Playwright cobrindo jornadas §14.1.

---

## 5. Checklist de lançamento (produto completo)

Conforme PRD §3 — **não reduzir escopo**:

- [ ] Todos CA-001..CA-070 verdes em staging
- [ ] PWA instalável em Chrome, Firefox, Safari (iOS conforme §23)
- [ ] SLOs §18.1 medidos no perfil de teste
- [ ] WCAG 2.2 AA auditado (CA-069)
- [ ] Backup/restore exercitado (CA-070)
- [ ] Dependências externas contratadas (§18.3)
- [ ] ADR frontend Angular registrado
- [ ] Documentação operacional ADMIN-10 publicada

---

## 6. Referências

- [PRD v2.0 §19](../PRD-Satoshi-Pet-Web-v2.0.md)
- [README do backlog](./README.md)
- [Definição técnica](./00-definicao-tecnica.md)
