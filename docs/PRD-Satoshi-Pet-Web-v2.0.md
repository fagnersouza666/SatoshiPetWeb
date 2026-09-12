# PRD — Satoshi Pet Web

**Versão:** 2.0  
**Data:** 10/09/2026  
**Status:** revisão consolidada das respostas do responsável pelo produto  
**Escopo:** produto completo, sem divisão em MVP  
**Plataforma:** aplicação web responsiva e PWA  
**Idioma:** português do Brasil  
**Valores:** Bitcoin em sats/BTC; valores fiduciários em reais (BRL)  
**Responsável pelas decisões:** solicitante do projeto  
**Documento de origem:** PRD-Satoshi-Pet-Web.md, versão 1.0

## 1. Finalidade e forma de leitura

O Satoshi Pet Web é um dashboard pessoal de acumulação de Bitcoin com um display divertido, próximo de um jogo. Recebimentos reais alimentam uma criatura persistente; um plano DCA calcula sugestões de compra; clima, localização e horário compõem o ambiente.

O público prioritário são acumuladores individuais. O uso será pessoal inicialmente, financiado com recursos próprios, com intenção de disponibilizar o código aberto futuramente. Não há prazo, meta comercial, pesquisa prévia obrigatória ou reprodução de equipamento/vídeo definidos pelo responsável. O produto completo inclui o módulo DCA desde a entrega.

Este documento substitui a especificação funcional anterior. Respostas posteriores prevalecem sobre anteriores quando tratam da mesma decisão. A entrevista foi encerrada: perguntas sem resposta não são consideradas aprovadas.

Regras identificadas como **CC — critério de consolidação** são soluções adotadas nesta revisão para resolver lacunas ou conflitos. Estão reunidas na seção 20 e não são apresentadas como respostas explícitas do usuário. As escolhas de engenharia também estão identificadas. Não há necessidade de outra rodada de perguntas para ler ou implementar a proposta; esses pontos permanecem visíveis para revisão.

## 2. Princípios e limites

1. Apenas recebimentos reais de Bitcoin geram alimentação. Uma compra declarada, uma sugestão ou uma notificação não alimentam o pet.
2. O sistema acompanha endereços públicos Bitcoin Mainnet. “Carteira”, nesta interface, significa o endereço informado; não significa todos os endereços de uma carteira.
3. O sistema não guarda fundos, não cria carteiras, não executa compras, ordens, PIX, saques ou transferências e não assina transações.
4. Nunca solicitar seed, chave privada, xprv, senha de carteira ou credenciais de negociação. O código de recuperação pertence somente à conta do aplicativo.
5. Estado, saldo, recebimentos, sugestões e contabilização persistem no backend e não dependem de uma aba aberta.
6. Um endereço identifica um único pet permanente. Várias contas podem acompanhar esse mesmo endereço.
7. Uma conta possui somente um endereço ativo por vez. A possibilidade de troca termina 72 horas após a criação da conta.
8. Aparência e identidade da criatura são preservadas ao reabrir, ao voltar ao ovo e ao deixar de existir conta vinculada.
9. Ambiente, estado do pet e sentimento de mercado são dimensões distintas. O clima não é inferido pelo humor ou pelo índice.
10. O DCA influencia a medida de alimentação por meio da porção de referência. Não muda diretamente o estado: é necessário haver recebimento.
11. Páginas de endereços registrados são obrigatoriamente públicas, com a projeção de dados definida na seção 6.
12. Não haverá demonstração com Bitcoin fictício, reinício voluntário do pet, morte definitiva, troca de skin após aprovação ou múltiplos pets simultâneos por conta.

## 3. Escopo funcional completo

| Área | Entrega |
|---|---|
| Conta | Registro e acesso por e-mail, recuperação, sessões e vínculo a um endereço. |
| Bitcoin | Validação, saldo confirmado, pendências, histórico integral, mempool, confirmações, substituições e reorganizações. |
| Pet | Ovo, nascimento, criatura original por IA, sprites de todos os estados, reserva de alimentação e retorno ao ovo. |
| DCA | Onze faixas fixas, cálculo diário, orçamento, compromissos, reserva virtual, histórico e compras declaradas. |
| Ambiente | Localização automática e manual, cidade, fuso, temperatura, clima e transições solares. |
| Interface | Dashboard, gráficos, histórico, QR Code, página pública, modo display, responsividade e PWA. |
| Avisos | Push de recomendação, recebimento pendente, confirmação e mudanças de estado. |
| Operação | Administração, auditoria, recuperação, observabilidade, segurança, acessibilidade e documentação para futura abertura do código. |

Não haverá fases de redução funcional chamadas MVP. A implementação poderá ser organizada por módulos e dependências, mantendo toda esta abrangência como produto solicitado.

## 4. Conta, acesso e recuperação

### 4.1 Registro

O registro solicita e-mail, endereço Bitcoin e nome do pet, quando o endereço ainda não possui um pet. Para endereço já conhecido, recuperar nome e identidade existentes; uma nova conta não os substitui.

**CC-01:** adotar magic link enviado ao e-mail, sem senha tradicional. O usuário escolheu e-mail, mas não especificou o mecanismo final. Verificar o e-mail antes de autorizar escrita.

Não exigir assinatura de mensagem nem transação para comprovar controle do endereço. O vínculo significa acompanhamento, não prova de propriedade dos fundos. Permitir que mais de um e-mail se vincule ao mesmo endereço.

Cadastro concluído cria:

- conta e sessão;
- prazo imutável de troca do endereço;
- vínculo ativo ao endereço;
- preferências individuais;
- acesso ao pet existente ou criação de seu registro lógico.

Os parâmetros financeiros são preenchidos em uma configuração do plano, sem presumir valores reais a partir dos exemplos deste PRD.

### 4.2 Permissões

- Visitante: consulta os dados públicos, sem alterar nada.
- Conta autenticada: altera somente seu plano, compras, localização e preferências.
- Criador original do pet, enquanto vinculado: pode alterar o nome.
- Outras contas: veem o mesmo nome, aparência e estado, mas não renomeiam nem regeneram o pet.
- Administração: opera integrações e suporte, sem possibilidade de trocar o endereço de uma conta após o prazo.

O login não concede acesso aos dados privados de outras contas que acompanham o mesmo endereço.

### 4.3 Recuperação

Gerar um código de recuperação do aplicativo e permitir recuperá-lo em outro aparelho mesmo sem acesso ao e-mail antigo.

**CC-02:** usar código aleatório de alta entropia, copiável e baixável, mostrado na emissão. Guardar somente verificador seguro; não registrar o segredo nos logs. Na recuperação, validar o código, verificar o novo e-mail, revogar sessões e substituir o código. Magic links são de uso único e curta validade.

Se e-mail e código forem perdidos, não prometer recuperação privada pelo endereço público. O pet e os dados públicos permanecem disponíveis. Essa perda não cria um mecanismo para trocar o endereço bloqueado.

## 5. Vínculo permanente e janela de troca

O prazo é medido em tempo absoluto:

    addressChangeDeadline = accountCreatedAt + 72 horas
    podeTrocar = agora < addressChangeDeadline

No instante exato do limite, a troca já está bloqueada. Mudanças de fuso, recuperação de conta e trocas anteriores não reiniciam o prazo.

Durante as 72 horas, permitir várias trocas, sempre mediante confirmação do efeito na interface. O usuário determinou que os dados da configuração anterior sejam apagados.

**CC-03 — isolamento na troca:**

1. Encerrar o vínculo anterior da conta.
2. Remover seus planos, compras, compromissos, reserva, preferências e configurações relacionadas à configuração anterior; revogar avisos agendados dessa configuração.
3. Remover suas compras apagadas da projeção pública.
4. Criar nova configuração para o endereço de destino.
5. Não apagar dados de outra conta, transações públicas da rede, identidade do pet anterior ou sprites.
6. Preservar snapshots técnicos não identificadores que sejam necessários para reproduzir a alimentação compartilhada; não preservar valores privados da conta como se continuassem ativos.
7. Recuperar o pet do endereço de destino se já existir.

Ao ficar sem contas vinculadas, o pet permanece armazenado. **CC-04:** suspender tarefas exclusivas de contas e permitir reconciliação completa quando houver nova consulta ou vínculo; não regenerar a criatura.

Após 72 horas, a interface, API e ferramentas administrativas rejeitam a troca. Não oferecer reinício de cadastro como função de alteração desse vínculo.

## 6. Pet compartilhado e publicação

### 6.1 Cardinalidade

    Uma conta → um vínculo ativo → um endereço
    Um endereço → um pet
    Um endereço ← várias contas independentes
    Cada conta → um plano DCA e seus próprios lançamentos

Todas as contas veem o mesmo pet, nome, aparência, reserva de alimentação e estado emocional. Planos DCA, orçamento mensal, reserva financeira virtual e compras completas são individuais.

### 6.2 Referência comum de alimentação

Há um conflito entre “mesmo estado para todas as contas” e “24 horas por recomendação individual”, pois recomendações diferentes produziriam estados diferentes para o mesmo recebimento.

**CC-05 — regra adotada para tornar o modelo consistente:** o pet possui uma única fonte de referência alimentar. Inicialmente é o plano da conta criadora. A sugestão dessa fonte define a porção de 24 horas do pet. As sugestões das demais contas continuam individuais e não recalculam o estado compartilhado.

Se a conta de referência sair do endereço, usar, a partir de um ciclo futuro, o plano configurado da conta vinculada há mais tempo. Na ausência de outra referência positiva, manter a última porção positiva armazenada. Uma conta recém-vinculada não reinterpreta o histórico.

A interface de uma conta secundária deve distinguir “Sua sugestão DCA” de “Porção de 24 horas deste pet”. A mudança da fonte não transfere o direito de renomear e não muda alimentações passadas.

Esse é um critério de consolidação relevante, não uma escolha explicitamente respondida pelo usuário.

### 6.3 Nome, localização e aparência

O nome é único por endereço. **CC-06:** se o criador deixar de estar vinculado, o nome fica preservado sem transferência automática do direito de editar. Se voltar ao mesmo endereço dentro das regras de vínculo, recupera sua permissão.

A localização individual controla cenário e fuso da experiência autenticada de cada conta. **CC-07:** a página geral usa a cidade da conta de referência; se não houver conta ativa, conserva a última cidade pública. Isso não altera a localização histórica usada na criação da criatura.

### 6.4 Dados públicos

| Público, sem login | Privado, por conta |
|---|---|
| Pet, nome, aparência, estado e reserva de alimentação. | E-mail, sessão, código e fluxo de recuperação. |
| Saldo confirmado, pendências e histórico completo de entradas/saídas do endereço. | Histórico completo de compras declaradas da conta. |
| Endereço e QR Code. | Corretora, observações e sats informados manualmente. |
| Cidade, clima, temperatura e horário local do cenário público. | Coordenadas de maior precisão e preferências individuais. |
| Dez compras declaradas mais recentes, somente data e valor em R$. | Orçamento, compromissos, reserva e dados internos de outras contas. |
| Sugestão DCA de referência atual e horário dos dados utilizados. | Outras sugestões pessoais, salvo projeção explicitamente descrita aqui. |

**CC-08:** “compras da carteira” será a projeção das dez compras declaradas mais recentes por contas atualmente vinculadas ao endereço, ordenadas por data real da compra e desempate estável. Não identificar a conta. Registros anteriores ao vínculo atual não são importados para essa projeção.

Uma compra continua pertencendo à conta que a declarou. Declarações semelhantes de contas diferentes não são fundidas automaticamente: o sistema não consegue provar que são a mesma compra. A interface as denomina “Compras declaradas”, sem alegar verificação pela blockchain.

**CC-09:** a sugestão publicada é a da fonte alimentar do pet, sem publicar orçamento, saldo da reserva financeira ou identidade dessa conta. Ela é identificada como “Sugestão de referência do pet”. Isso resolve a pergunta não respondida sobre múltiplas sugestões na mesma página.

Não publicar coordenadas exatas, e-mail, prompt da IA, dados de autenticação ou identificação do autor de uma compra. Páginas são públicas obrigatoriamente; não existe botão para torná-las privadas. **Critério técnico:** acesso por link/endereço, sem exigir indexação em buscadores.

## 7. Estados, alimentação e retorno ao ovo

### 7.1 Estados emocionais

O motor deixa de usar apenas o tempo desde a última alimentação. Usa uma reserva que é consumida com o tempo.

| Estado | Regra, quando a criatura pode estar visível |
|---|---|
| ALIMENTADO | Reserva de alimentação maior que zero. |
| PENSANDO | Reserva esgotada há menos de 24 horas. |
| CHATEADO | Reserva esgotada há pelo menos 24 e menos de 48 horas. |
| FAMINTO | Reserva esgotada há pelo menos 48 e menos de 72 horas. |
| CRÍTICO | Reserva esgotada há pelo menos 72 e menos de 96 horas. |
| HIBERNANDO | Reserva esgotada há 96 horas ou mais. |

OVO é uma condição de apresentação ligada a nascimento/saldo e tem prioridade sobre os estados emocionais. HIBERNANDO substitui definitivamente o nome anterior do estado extremo. Não existe morte irreversível.

Sono é uma animação sobreposta compatível com o período noturno, não outra faixa de alimentação.

### 7.2 Porção diária e reserva

A quantidade de sats correspondente à sugestão DCA de referência equivale a 24 horas de alimentação.

    horasAcrescentadas = 24 × satsRecebidos / porcaoReferenciaSats
    reservaDepois = mínimo(168 horas, reservaAntes + horasAcrescentadas)

A reserva máxima é de sete dias. Qualquer valor positivo é elegível, inclusive transferências próprias e saídas de troco para o endereço. Não há mínimo de valor nem bônus fixo por transação.

| Porção de referência | Recebimento | Duração acrescentada |
|---:|---:|---:|
| 20.000 sats | 5.000 sats | 6 horas |
| 20.000 sats | 10.000 sats | 12 horas |
| 20.000 sats | 20.000 sats | 24 horas |
| 20.000 sats | 40.000 sats | 48 horas |

Antes de aplicar um recebimento no instante t, consumir a reserva pelo tempo transcorrido. Se já estiver esgotada, começar a nova alimentação a partir de zero; não cobrar “dívida de fome”.

O excesso acima de sete dias permanece nos indicadores de recebimento, mas não fica guardado para ser creditado quando a reserva diminuir. No reprocessamento, reproduzir o limite no instante de cada evento.

Guardar por alimentação: recebimento lógico, valor, instante efetivo, porção utilizada, duração, versão de regra e situação provisória/válida/invalidada. Alterar o DCA amanhã não altera a duração de ontem.

**CC-10:** armazenar duração com precisão suficiente para pequenos recebimentos e não arredondar cada saída separadamente. Somar saídas do mesmo recebimento antes do cálculo. Valores de BTC/sats e moeda não usam ponto flutuante binário em cálculos contábeis.

### 7.3 Primeiro cadastro e primeiro nascimento

1. Consultar todo o histórico e o saldo confirmado antes de concluir o estado.
2. Se o endereço nunca teve pet gerado e o saldo confirmado for zero, apresentar ovo padrão, mesmo que tenha recebido e gasto Bitcoin no passado.
3. O ovo pode se mexer se houver recebimento pendente; exibir “Recebimento pendente”.
4. O primeiro nascimento requer saldo positivo e recebimento confirmado. Um endereço já com saldo confirmado positivo satisfaz essa condição pelo histórico.
5. Gerar a criatura e os sprites somente quando a condição de nascimento for atingida.
6. Exibir a animação de nascimento, seguida do estado reconstruído.
7. Histórico anterior ao acompanhamento não reproduz animações de alimentação antigas.

Sincronização, geração e falta de referência alimentar são estados operacionais identificados na tela, não recebimentos fictícios nem novos estados emocionais.

**CC-11:** enquanto ainda não existir a primeira porção positiva, armazenar recebimentos e apresentar “Aguardando referência do plano” para a energia. Quando houver a primeira porção, reconstruir o histórico. Não inventar um plano financeiro nem usar arbitrariamente 10.000 sats como padrão.

### 7.4 Reconstrução histórica

Usar todos os recebimentos confirmados disponíveis, em ordem temporal, com desempate estável por bloco, posição da transação e identificador de saída.

Para o período anterior à existência do plano, usar a primeira porção positiva calculada como referência técnica de reconstrução, identificada como tal. Não criar sugestões DCA históricas fictícias.

Para recebimentos acompanhados ao vivo, conservar a primeira observação. Para históricos sem primeira observação disponível, usar o instante do bloco e marcar a origem desse horário. Uma descoberta tardia não deve parecer uma transferência feita agora.

Ao adicionar uma segunda conta ao endereço, reutilizar o estado já existente. Não reconstruir com o plano dessa segunda conta nem alterar a aparência.

### 7.5 Saldo zero após o pet ter nascido

A carência é de **24 horas contínuas de saldo confirmado zero**:

    retornarAoOvo = petJaNasceu
                    e saldoConfirmadoSats == 0
                    e agora >= zeroBalanceSince + 24 horas

**CC-12 — consequências adotadas para as perguntas 132–135 e 144–145:**

- Saída pendente não inicia a carência; a contagem começa quando o saldo confirmado efetivamente zera.
- Durante as 24 horas, a criatura permanece visível e os estados emocionais evoluem normalmente.
- Qualquer saldo confirmado positivo encerra a carência. Se zerar novamente, começa nova contagem.
- Entrada apenas pendente não interrompe a carência.
- Depois de 24 horas ainda zerado, exibir ovo, mesmo que haja reserva de alimentação.
- A reserva continua sendo consumida enquanto o ovo é exibido.
- A aparência permanece armazenada. A volta ao ovo não autoriza nova geração ou regeneração.
- Se o pet já está no ovo, exigir saldo confirmado positivo para reaparecer; recebimento pendente apenas movimenta o ovo.
- Um novo recebimento confirmado acrescenta alimentação uma única vez e permite reaparecer a mesma criatura.
- Se o saldo for restaurado por reorganização, reavaliar o estado automaticamente; não fabricar alimentação nova pelo simples retorno do saldo.
- A exceção “voltar imediatamente ao ovo” vale quando uma reorganização invalida o único recebimento que permitia o primeiro nascimento, sem outro fundamento confirmado. Não é o caso normal de gastar saldo após nascimento válido.

Saldo confirmado desconhecido por falha de provedor não equivale a zero. Indicar estado desatualizado e reconciliar antes de iniciar ou concluir a carência.

### 7.6 Precedência de apresentação

| Condição | Apresentação |
|---|---|
| Sem sincronização confiável | Último estado conhecido com aviso, ou carregamento na primeira visita. |
| Pet nunca nascido e saldo confirmado zero | Ovo padrão; movimento se houver entrada pendente. |
| Primeiro nascimento elegível, arte ainda em geração/aprovação | Ovo e indicação de preparação do nascimento. |
| Pet nascido, saldo zero há menos de 24 horas | Criatura no estado emocional atual. |
| Pet nascido, saldo zero há 24 horas ou mais | Ovo, conservando criatura e reserva interna. |
| Saldo confirmado positivo e arte aprovada | Criatura no estado calculado; animação de nascimento/retorno quando aplicável. |

## 8. Criação por IA e animações

### 8.1 Identidade

Criar uma criatura original em pixel art 2D, escolhida aleatoriamente pela IA, sem seleção prévia do usuário. Gerar uma descrição diferente para cada novo endereço.

Considerar no momento da geração:

- endereço como identificador/base de semente;
- localização e fuso;
- clima, temperatura e período do dia;
- fauna, flora, elementos nativos e referências culturais regionais;
- variações de corpo, proporções, cores e traços que preservem uma identidade própria.

Usar ovo comum, sem antecipar a identidade por um ovo exclusivo. O prompt não é público.

Referências e estereótipos regionais leves são permitidos; excluir caricaturas ofensivas, conteúdo sexual, violência explícita, marcas e pessoas reais.

A semente derivada do endereço e o contexto congelado ajudam a repetir o processo, mas a garantia de identidade é a persistência dos arquivos aprovados. Não prometer reprodução idêntica de pixels por uma nova chamada à IA.

### 8.2 Conjunto visual

Gerar um conjunto coerente com o mesmo corpo, paleta e características em todas as poses:

- idle, olhar, sorriso e atividade;
- alimentação e comemoração;
- pensando, chateado, faminto e crítico;
- hibernação;
- sono;
- nascimento e retorno, combinando ovo padrão com a criatura;
- alternativas de movimento reduzido.

Não gerar uma criatura diferente para cada estado. Validar coerência entre sprites, proporção, transparência, alinhamento e legibilidade.

**Escolha técnica:** gerar uma referência visual principal e derivar poses com essa referência; empacotar em atlas/arquivos versionados. Persistir modelo, semente quando suportada, prompt privado, contexto, tentativas e versão dos assets.

### 8.3 Aprovação, falha e regeneração

Permitir uma regeneração antes da confirmação da criatura. A segunda geração válida é definitiva: não oferecer novo sorteio por preferência. Após aprovação, aparência e acessórios ficam bloqueados.

**CC-13:** ao fechar a aplicação, preservar a primeira geração esperando confirmação. Reabrir não dispara outra geração. Apenas a conta criadora vinculada pode solicitar a regeneração. Se não houver criador ativo, aprovar automaticamente o primeiro conjunto que passe pelas validações técnicas para não bloquear o pet compartilhado.

Falhas técnicas não consomem a única regeneração voluntária. Retentar automaticamente com controle de frequência; manter ovo e status de geração. Alertar operação em falhas persistentes. Não tratar um conjunto tecnicamente inválido como “segunda aparência definitiva”.

Mudanças futuras de cidade, fuso ou clima alteram o cenário, não a criatura. Retornos ao ovo e novos vínculos usam os mesmos arquivos aprovados.

## 9. Monitoramento Bitcoin e reconciliação

### 9.1 Endereços e valores

Validar rede, formato, checksum e suporte a P2PKH, P2SH, SegWit nativo e Taproot por biblioteca apropriada. Produção aceita somente Mainnet. A identificação interna deve usar rede e representação canônica de script/endereço, sem manipulação incorreta de maiúsculas em formatos sensíveis.

Saldo principal: saldo confirmado do endereço. Exibir entradas e saídas pendentes separadamente. Saídas reduzem saldo; não retiram diretamente a alimentação já adquirida. O retorno ao ovo segue a carência da seção 7.

Total recebido não é saldo atual nem prova de patrimônio novo. Troco e transferências próprias contam para alimentação conforme decisão do usuário.

### 9.2 Recebimento e confirmação

Um recebimento reúne as saídas destinadas ao endereço dentro de uma transação. Várias saídas de 1.000 e 2.000 sats resultam em uma alimentação de 3.000 sats.

Para pet visível já nascido, reagir na mempool e exibir “Recebimento pendente”. A confirmação consolida o mesmo evento sem reiniciar relógio ou duplicar energia.

**CC-14:** para um ovo que exige saldo confirmado, a energia do recebimento que o faz nascer/retornar passa a valer na confirmação. A primeira observação continua registrada no histórico. Isso evita consumir a primeira refeição inteira antes de o pet poder nascer.

Detectar também transações encontradas diretamente em bloco. Não exigir ter observado a mempool para reconhecer um recebimento real.

### 9.3 Substituições e reorganizações

Separar identidade de transação, saída e recebimento lógico:

- saída única por transação e vout;
- recebimento agregado por endereço;
- relações de conflito/substituição por entradas gastas e evidência do provedor;
- alimentação única por recebimento lógico e pet.

RBF que mantém o recebimento atualiza valor e vínculo sem criar segunda refeição. Se reduzir ou remover a saída, recalcular a alimentação. Não deduzir equivalência apenas por endereço e valor.

Transação descartada ou recebimento invalidado: preservar trilha, invalidar efeito alimentar e reconstruir a reserva cronologicamente. Somente subtrair o crédito original pode dar resultado errado quando houve limite de sete dias; reexecutar os eventos válidos.

Uma reorganização altera bloco, hash, confirmações, saldo e, se necessário, elegibilidade alimentar. Não tratar uma transação fora de bloco automaticamente como pagamento inexistente: ela pode voltar à mempool.

Estados operacionais incluem MEMPOOL, CONFIRMED, REPLACED, DROPPED e UNKNOWN. REORGED é também um evento histórico; a situação atual deve dizer se voltou à mempool, confirmou novamente ou ficou desconhecida.

Ausência em uma resposta ou indisponibilidade do provedor não prova descarte. Exigir reconciliação e evidência antes de retirar alimentação.

### 9.4 Histórico e recuperação

Importar o histórico confirmado integral com paginação; registrar progresso e ponto de reconciliação por altura/hash/cursor. Não limitar o backfill à primeira página de resultados.

Persistir entradas, saídas, saldo, taxas totais da transação, txid, vout, blocos e confirmação. Identificar a taxa como total da transação, sem afirmar que foi paga pelo recebedor.

Recuperar após interrupções: reconciliar blocos e histórico desde um ponto seguro com sobreposição, conferir hashes, reprocessar idempotentemente e atualizar clientes.

Não prometer reconstruir transações não confirmadas que apareceram e desapareceram durante a falha sem terem sido observadas ou arquivadas pelo provedor.

### 9.5 Fila visual

Cada conta mantém um cursor de apresentação dos eventos novos desde seu acompanhamento. Ao retornar, reproduzir comemorações individualmente, em ordem, com botão “Pular animações”.

**CC-15:** sincronizar o cursor da conta entre dispositivos; visitantes usam cursor local. Isso não muda os eventos compartilhados. Pular marca apresentação como concluída, sem apagar histórico.

Histórico anterior ao cadastro não entra na fila de comemoração. Confirmar ou reprocessar não repete alimentação. Eventos invalidados são retirados da fila ou exibidos como correção, sem celebração de recebimento válido.

## 10. Plano DCA

### 10.1 Configuração individual

Cada conta configura:

- valor-base diário escolhido pelo usuário;
- orçamento mensal;
- teto diário;
- plano ativo/pausado;
- localização/fuso efetivo para agendamento;
- notificações.

Não derivar automaticamente o valor-base do orçamento mensal. O horário é 08h local. Valores do documento são exemplos de cálculo, não configurações financeiras reais do usuário.

Guardar versões com vigência. Mudanças no valor-base, limites ou regras pessoais após uma sugestão passam a valer para a próxima, conservando o snapshot vigente.

### 10.2 Faixas fixas

| Fear & Greed, inclusivo | Multiplicador |
|---|---:|
| 0–4 | 2,00 |
| 5–14 | 1,80 |
| 15–24 | 1,60 |
| 25–34 | 1,40 |
| 35–44 | 1,20 |
| 45–55 | 1,00 |
| 56–65 | 0,85 |
| 66–75 | 0,70 |
| 76–85 | 0,55 |
| 86–95 | 0,40 |
| 96–100 | 0,25 |

Faixas e multiplicadores não são editáveis por usuário nem por painel administrativo comum. Uma alteração futura requer nova versão explícita da regra de produto e preservação dos cálculos anteriores.

Validar índice dentro de 0–100. As faixas acima pertencem à estratégia do aplicativo; guardar separadamente classificação fornecida pela fonte, quando existir. “Temperatura do Bitcoin” pode ser a apresentação visual, sem mudar a leitura numérica.

### 10.3 Cálculo

    valorTeorico = valorBaseDiario × multiplicador
    tetoRestanteDoDia = máximo(0, tetoDiario - comprasDeclaradasNoDia)
    disponivel = máximo(0, saldoOrcamentario - compromissosAtivos)
    sugestao = máximo(0, mínimo(valorTeorico, tetoRestanteDoDia, disponivel))

**CC-16:** compras avulsas conhecidas no mesmo dia consomem o teto diário antes de uma geração tardia. Recomendações são únicas no dia; não emitir outra para completar o teto depois.

Arredondar a sugestão monetária para centavos para baixo, de modo que não ultrapasse limites. A reserva DCA virtual não restringe a sugestão e não é somada ao orçamento.

Exemplo:

    Valor-base: R$ 20,00
    Fear & Greed: 17
    Multiplicador: 1,60
    Valor teórico: R$ 32,00
    Limite disponível: R$ 25,00
    Sugestão final: R$ 25,00

Se o orçamento esgotar, publicar R$ 0 com explicação. O orçamento pode terminar antes do mês: não distribuir obrigatoriamente valores pelos dias restantes.

### 10.4 Conversão e porção alimentar

Na geração, fixar a cotação BTC/BRL efetivamente utilizada:

    satsEquivalentes = sugestaoEmBRL / cotacaoBRLPorBTC × 100.000.000

Guardar a equivalência com precisão decimal e a cotação original. Para exibição de quantidade transferível e QR, arredondar para sats inteiros e mostrar o valor correspondente; a proporção interna usa a referência persistida.

Uma sugestão positiva inferior a um sat pode ter equivalência fracionária interna; não gerar QR com quantidade fracionária de sat nem arredondar silenciosamente a compra acima do limite.

A porção do pet é travada por sugestão. A cotação de mercado exibida no dashboard pode atualizar depois, desde que visualmente distinta da cotação usada no cálculo.

Quando a sugestão for zero, indisponível ou ainda não tiver sido emitida no novo dia, conservar a última porção positiva do pet. Isso é referência do jogo, não uma sugestão financeira nova baseada em informação vencida.

### 10.5 Interações

- COMPREI: criar compra declarada pelo valor sugerido.
- NÃO COMPREI: registrar decisão e liberar compromisso.
- COMPREI OUTRO VALOR: registrar valor efetivo informado.
- Compra avulsa: registrar sem sugestão associada.
- Corrigir/excluir: atualizar contabilização e manter auditoria privada enquanto o registro existir.

Compras acima da sugestão, teto ou orçamento são aceitas pelo valor real informado e sinalizadas como excesso. O sistema registra decisões; não controla gastos externos.

Guardar valor BRL, data real da compra e, opcionalmente, sats, corretora e observações. Permitir registros atrasados. Datas e valores corrigidos recalculam saldos posteriores.

Separar status da geração (aguardando dados, gerada, encerrada), resposta (pendente, comprei, não comprei, outro valor) e entrega da notificação. Aceite pelo serviço de push não significa leitura.

## 11. Dados de mercado, horários e fusos

### 11.1 Fontes e validade

Fear & Greed: CoinGlass, conforme o PRD e respostas. Cotação: provedor BTC/BRL identificado e acessado pelo backend. Não trocar silenciosamente fonte do índice.

Guardar por leitura: fonte, dado, data de referência, instante de atualização na fonte quando disponível, instante da coleta, instante de validação e resultado.

Consultar às 06h, 07h e 08h no fuso local efetivo do plano. As duas primeiras coletas aquecem o cache e não emitem sugestão ou aviso de compra.

Às 08h, tentar obter ambos os dados atuais. Se algum falhar, usar a última leitura válida desse componente, desde que respeite o limite:

    limiteInferior = 06h do dia civil anterior, no fuso da sugestão

O usuário aceitou esse mesmo limite para índice e cotação. Em dia sem mudança de offset, uma leitura às 06h de ontem pode ter 26 horas às 08h de hoje. Não substituir essa decisão por limite de duas horas.

**CC-17:** validade considera a idade efetiva do dado, não apenas uma nova consulta ao mesmo registro antigo. Persistir data de referência de índices diários e timestamp da cotação. Se a fonte não permitir estabelecer validade, não apresentar o dado como atual.

Exibir os dois horários efetivos e a condição “dados armazenados”. Rejeitar números inválidos, preços não positivos e leituras futuras inconsistentes. A indisponibilidade total não gera números inventados.

### 11.2 Falha e recuperação

Se não houver dados válidos às 08h:

- expirar o compromisso anterior no horário previsto;
- manter a sugestão do novo ciclo como aguardando dados, sem novo compromisso;
- mostrar indisponibilidade;
- retentar automaticamente com atraso progressivo;
- gerar uma única sugestão quando houver dados válidos ainda dentro daquele ciclo;
- fixar suas leituras e encerrar as retentativas de geração.

Uma consulta bem-sucedida posterior não recalcula uma sugestão já gerada. A regra dos limites de validade usa a data local do ciclo corrente.

### 11.3 Vigência e mudança de localização

Cada sugestão expira às 08h do dia seguinte no fuso congelado para seu ciclo. Resposta do usuário pode liberar ou consumir seu compromisso antes disso. À meia-noite ele não é liberado automaticamente.

A localização automática é preferida, com escolha manual. Mudanças do fuso entram em vigor somente no dia seguinte; não alteram a sugestão já emitida.

**CC-18 — prevenção de duplicidade em viagem:**

1. Guardar data local da última sugestão, fuso, instante de geração e instante de término do ciclo.
2. Selecionar o próximo horário de 08h no fuso aplicável cuja data seja posterior à data local da última sugestão.
3. Esse horário não pode anteceder o término do ciclo anterior já congelado.
4. Nunca gerar duas sugestões por atravessar fusos, voltar a uma data anterior ou repetir uma tarefa.
5. Se a combinação de fuso e ciclo impedir a próxima ocorrência, aguardar a primeira ocorrência elegível de 08h, explicando o próximo horário na interface.

Não impor “24 horas exatas entre sugestões” a dias com transição de horário local; aplicar os limites de ciclo e calendário. Usar zonas IANA, não offsets fixos.

Agendamentos são individualizados por conta. Leituras de mercado podem ser compartilhadas em cache para evitar chamadas redundantes.

## 12. Orçamento, compromissos e reserva DCA

### 12.1 Orçamento mensal acumulado

O orçamento não utilizado passa ao mês seguinte. Se houve excesso, ele reduz o próximo orçamento.

    fechamentoMes = aberturaMes + aporteConfiguradoMes - comprasDeclaradasMes
    aberturaMesSeguinte = fechamentoMes
    capacidadeMesSeguinte = aberturaMesSeguinte + novoAporteConfigurado

Exemplo de sobra: aporte R$ 600, compras R$ 500 → carregar R$ 100; com novo aporte de R$ 600, capacidade R$ 700.

Exemplo de excesso: aporte R$ 600, compras R$ 650 → carregar R$ -50; com novo aporte de R$ 600, capacidade R$ 550.

Esse orçamento é planejamento contábil, não saldo bancário comprovado.

**CC-19:** usar mês civil no fuso contábil congelado para cada lançamento/período. Mudança de localização não move retroativamente compras entre meses nem cria novo aporte para o mesmo mês. No primeiro mês, usar o orçamento informado integralmente, sem rateio automático não solicitado.

Alterar o valor mensal no mês corrente aplica somente a diferença da nova configuração, nunca um segundo aporte integral. A mudança passa a afetar novas sugestões conforme a vigência da versão.

### 12.2 Compromissos

Uma sugestão positiva pendente compromete seu valor. Ao informar compra, remover o compromisso e debitar o valor real, numa mesma operação. NÃO COMPREI e expiração liberam o compromisso.

Um compromisso não é compra. Se atravessar o fechamento mensal, transportar a obrigação sem contá-la como gasto nem duplicá-la na sobra.

Exemplo: capacidade R$ 100, compras R$ 80 e compromisso pendente R$ 10 → saldo contábil R$ 20, disponível R$ 10. Na virada, carregar R$ 20 e manter o compromisso de R$ 10 até sua liberação às 08h ou resposta.

Ao gerar a próxima sugestão, primeiro encerrar o compromisso anterior vencido, reconciliar lançamentos e somente depois calcular o novo valor.

Se uma correção tardia tornar o orçamento negativo, registrar o déficit e impedir novas sugestões positivas até haver capacidade. A sugestão histórica já apresentada não é reescrita; mostrar o descompasso na contabilização atual.

### 12.3 Reserva virtual informativa

    reservaDca = soma(valorBaseDosDiasEncerrados)
                 - soma(comprasDeclaradasAtribuidasAosDiasEncerrados)

Pode ser positiva ou negativa, acumula entre meses e não limita nem amplia o orçamento. Compras avulsas também entram no cálculo.

Exemplos: base diária R$ 20 e compra R$ 5 → variação +R$ 15; compra R$ 30 → variação -R$ 10; nenhuma compra declarada → +R$ 20, com indicação de que o resultado depende dos registros do usuário.

**CC-20:** a reserva fecha dias civis, independentemente do compromisso que expira às 08h. Só há base para dias a partir da ativação do plano; dias pausados não acrescentam base. A versão diária é persistida. Lançamentos atrasados corrigem a reserva desde sua data real. Não contabilizar todo o passado on-chain como compras declaradas.

### 12.4 Reprodutibilidade

Manter extrato de aportes/diferenças de configuração, compras, correções, compromissos e liberações, com identificadores idempotentes.

Separar:

- snapshot de como a sugestão foi calculada naquele momento;
- projeção atual de orçamento/reserva após correções;
- snapshots de porção alimentar já aplicados.

Recalcular saldos não altera porções antigas nem inventa outra sugestão para o mesmo dia.

## 13. Localização, clima e ambiente

### 13.1 Preferência e alternativas

Preferir geolocalização automática, solicitada após ação do usuário. Permitir trocar manualmente para cidade/estado/país e distinguir cidades homônimas. Uma escolha manual permanece até o usuário retornar ao modo automático.

Manter a última localização válida quando a automática falhar. Para o fuso: usar o da localização; sem essa informação, usar o do dispositivo; se também indisponível, usar America/Sao_Paulo.

Publicar somente cidade e contexto meteorológico. **Escolha técnica:** guardar coordenadas arredondadas o suficiente para clima local, sem histórico de deslocamentos precisos.

Uma alteração de localização é registrada como pendente e aplicada no dia seguinte, incluindo o novo fuso de recomendações. Mostrar qual cidade está ativa e qual mudança está agendada. Não alterar o contexto congelado de nascimento.

### 13.2 Representação

Exibir temperatura em °C, horário, cidade, condição meteorológica e atualidade do dado. Oferecer sensação térmica, mínima/máxima e umidade como detalhes.

Suportar céu limpo, parcialmente nublado, nublado, chuva, chuva forte, tempestade, neblina, neve, granizo e vento forte. Fazer o mapeamento dos códigos do provedor para essas representações.

Usar nascer/pôr do sol locais para amanhecer, dia, entardecer e noite com transições suaves. **Escolha técnica:** tratar explicitamente dias sem nascer/pôr do sol, usando a condição informada pelo provedor.

Atualizar clima a cada 15 minutos, na mudança efetiva da localização, em solicitação manual com limite de frequência e ao retomar após longa suspensão.

Se o clima falhar, conservar o último snapshot com aviso. **Escolha técnica:** após duas horas sem atualização, apresentar “Clima indisponível” e cenário neutro compatível com dia/noite; não inventar temperatura ou chuva.

Efeitos climáticos não alteram a alimentação. O mesmo pet pode estar feliz na chuva ou faminto ao sol.

## 14. Interface, display e PWA

### 14.1 Jornadas

**Visitante:** informar endereço conhecido ou abrir link → visualizar pet, blockchain, QR, dez compras e sugestão pública de referência.

**Nova conta:** verificar e-mail → informar endereço e nome quando novo → aceitar a publicação descrita na tela → autorizar/localizar cidade → guardar recuperação → sincronizar saldo e histórico → mostrar ovo ou preparar nascimento → configurar plano individual.

**Retorno:** abrir endereço ou sessão → recuperar o mesmo pet e estado → atualizar dados → reproduzir novos recebimentos individualmente, com opção de pular.

**Compra:** abrir sugestão → decidir e comprar fora do aplicativo → registrar compra → atualizar orçamento. Somente eventual recebimento on-chain gera alimentação.

**Troca permitida:** abrir configuração → informar novo endereço → mostrar exclusão da configuração pessoal anterior e prazo remanescente → confirmar → realizar troca atômica no backend e sincronizar o pet de destino.

**Recuperação:** fornecer código do aplicativo → verificar novo e-mail → revogar acesso antigo e emitir novo código → recuperar a mesma conta e o mesmo prazo de vínculo.

### 14.2 Tela principal

O pet é o centro da interface. Exibir:

- criatura ou ovo, nome e estado;
- reserva alimentar e tempo para a próxima faixa;
- saldo confirmado em sats e pendências em área separada;
- valor e horário da última alimentação;
- cidade, horário, temperatura e clima;
- situação de sincronização e último recebimento;
- sugestão do dia, porção de 24 horas e origem da referência;
- atalhos para QR, histórico, dashboard e modo display.

Em contas secundárias, diferenciar sua sugestão individual da referência do pet. No ovo por saldo zero, mostrar o motivo; durante a carência, mostrar o tempo restante sem afirmar que o pet foi apagado.

### 14.3 Dashboard, históricos e estatísticas

Preservar o conjunto completo do documento original:

- saldo, recebimentos hoje/semana/mês/ano, total recebido e pendências;
- quantidade de alimentações, média e maior refeição;
- idade desde o primeiro nascimento, dias de acompanhamento e sequência de dias alimentados;
- tempo alimentado, faminto, hibernando e no ovo;
- distribuição por horário e intervalo entre recebimentos;
- histórico diário/semanal/mensal/anual do índice utilizado;
- sugestões, valor-base, multiplicador, cotação usada e limites aplicados;
- total comprado declarado, orçamento, compromissos e reserva virtual;
- decisões atendidas, ignoradas, ajustadas e sem resposta;
- gráficos de recebimentos, compras declaradas e comparação base/sugestão.

Separar histórico on-chain de compras declaradas e de sugestões. Gráficos financeiros nunca contabilizam um registro manual também como recebimento.

O histórico on-chain mostra todas as entradas e saídas disponíveis, com status, txid, bloco, confirmações, valor, taxa total e link de explorador. O público não recebe pelo payload o histórico privado completo para depois apenas escondê-lo na tela.

**CC-21:** idade não reinicia ao voltar ao ovo. Período histórico reconstruído e período observado pelo sistema são distinguidos nas estatísticas de pet.

### 14.4 QR Code

Gerar QR do endereço e URI Bitcoin com valor opcional. Atalhos de valor devem incluir porção de referência de 24 horas e quantidade personalizada; impedir valor fracionário de sat e explicar a conversão.

Nunca substituir o destinatário por endereço da aplicação. O QR é público conforme decisão do usuário.

### 14.5 Modo display e som

Modo display em tela cheia, especialmente adequado a landscape, com menus recolhidos, saída acessível por toque/teclado, pet em destaque, clima, hora, saldo e estado.

**Escolhas técnicas:** solicitar manutenção de tela acordada quando suportada, recuperar após voltar ao foco, reduzir taxa de quadros em dispositivos lentos e limitar efeitos intensos. Não garantir comportamento de quiosque em qualquer TV/navegador sem validação.

Som opcional, desligado por padrão em todos os dispositivos; habilitação por ação do usuário. Incluir sons de alimentação, nascimento, ambiente e clima, sem tocar automaticamente apenas porque houve nova visita.

### 14.6 Responsividade, instalação e offline

Layout mobile-first com referência de 320–767 px, 768–1199 px e 1200 px ou mais, funcionando em portrait e landscape.

A intenção “todos os dispositivos” é traduzida em abrangência máxima com detecção de capacidades. Instalação, push, tela cheia e bloqueio de suspensão dependem do ambiente e devem ter alternativas visíveis; a consulta básica do pet deve continuar disponível.

PWA com manifest, ícones, modo standalone, service worker, cache de arquivos visuais e recuperação de sessão.

Offline: abrir a estrutura e último estado disponível com aviso; não inventar sincronização, recomendar com dados fora da validade ou confirmar gravação que não chegou ao servidor. **Escolha técnica:** desabilitar alterações financeiras offline nesta versão completa, preservando leitura local.

Logout/troca de conta limpa dados privados em cache. Nova versão de assets não mistura sprites de duas criaturas ou gerações.

### 14.7 Acessibilidade

Adotar WCAG 2.2 AA como alvo de aceitação: contraste, teclado, foco, rótulos, status legível por leitor de tela, alvos de toque adequados e redução de movimento.

Animação, cor, som e ícones não são o único meio de transmitir estado. Oferecer descrições do pet e opção de pular comemorações.

## 15. Notificações

As categorias de recomendação diária, recebimento pendente, confirmação e mudança de estado começam habilitadas nas preferências. A permissão real de Web Push depende de consentimento no navegador.

Não há horário silencioso por padrão: avisos podem chegar de madrugada. Controles individuais permitem desativar categorias.

Exemplos:

- “Recebimento pendente: +4.500 sats.”
- “Recebimento confirmado: +4.500 sats.”
- “Seu pet está ficando com fome.”
- “Seu pet voltou ao ovo após 24 horas sem saldo.”
- “Sua regra de DCA calculou R$ 32,00 para hoje.”

Mensagens usam a situação correta do pet: um ovo com entrada pendente não é anunciado como já nascido.

As coletas das 06h e 07h não notificam sugestão. A sugestão das 08h, ou gerada posteriormente por recuperação de dados, notifica uma única vez e abre o registro correspondente.

**Escolhas técnicas:** chaves idempotentes por conta, evento e categoria; fila com repetição controlada; expiração de mensagens que perderam utilidade; nunca reproduzir alertas antigos em massa após reconciliação. Correção de recebimento já notificado pode gerar aviso corretivo único.

Não incluir e-mail, observações, corretora ou código de recuperação na mensagem. Configurar dispositivos de recebimento e revogar subscriptions inválidas.

## 16. Arquitetura e modelo de dados

Esta seção especifica responsabilidades e escolhas técnicas de referência. As versões exatas das dependências serão fixadas e verificadas durante a implementação; a revisão do PRD não equivale a testes de software já realizados.

### 16.1 Componentes

    Frontend responsivo/PWA
           ↕ API + eventos em tempo real
    Backend modular
      ├─ contas, sessões e vínculos
      ├─ monitor e reconciliação Bitcoin
      ├─ motor compartilhado do pet
      ├─ geração visual por IA
      ├─ plano, agenda e contabilidade DCA por conta
      ├─ localização e clima
      ├─ projeções públicas e privadas
      └─ notificações, auditoria e operação
           ↕
    PostgreSQL + armazenamento de imagens
           ↕
    Provedores externos ou infraestrutura Bitcoin própria

Manter Java 21+ e Quarkus como referência de backend do documento original. **Escolha técnica adotada:** frontend React com TypeScript, renderização de pixel art por sprites, PostgreSQL como fonte persistente, armazenamento de objetos para imagens e jobs com trava/estado persistido.

**Escolha técnica adotada — containerização:** todos os componentes do sistema (frontend PWA, backend, banco de dados, armazenamento de objetos, cache e nó Bitcoin de teste) executam em **containers** em todos os ambientes, sem instalação direta no host. Desenvolvimento usa Docker Compose para subir a stack completa; produção executa as mesmas imagens versionadas (Compose ou Kubernetes). Nenhuma funcionalidade pode depender de software instalado fora de container.

WebSocket é a preferência para atualização; reconexão deve buscar snapshot e cursor de eventos. Redis é opcional para cache/filas conforme necessidade, sem substituir a persistência financeira.

Separar integrações por contratos para migrar de serviço Bitcoin externo para Bitcoin Core com indexador compatível. Não exigir dois backends Bitcoin operacionais ao mesmo tempo para cumprir a arquitetura.

### 16.2 Entidades e restrições

| Entidade | Conteúdo e invariantes |
|---|---|
| Account | E-mail verificado, criação, prazo de 72h imutável, recuperação e estado da conta. |
| Address | Rede, endereço/script canônico, único por rede e destino. |
| AccountAddressBinding | Conta, endereço, início/fim; um vínculo ativo por conta. |
| Pet | Único por endereço; criador, nome, fonte de referência, nascimento, zeroBalanceSince e projeção de estado. |
| PetArtwork | Contexto, prompt privado, geração principal, sprites, tentativas, regeneração voluntária e aprovação. |
| BitcoinTransaction | txid único por rede, entradas, status, observações e referência de bloco. |
| BitcoinOutput | transactionId, vout, destino, amountSats; unicidade por transactionId + vout. |
| BitcoinSpend | Referência à saída gasta, transação gastadora, situação pendente/confirmada. |
| LogicalReceipt | Agrupamento de recebimento do endereço e relações de substituição/conflito. |
| PetFeeding | Único por pet + recebimento lógico; valor, porção, duração, instante e revisões. |
| PetEvent / StateHistory | Eventos ordenados, versões, snapshots reconstruíveis e intervalos de estado. |
| Location | Conta, cidade, coordenadas minimizadas, fonte, fuso ativo e mudança agendada. |
| WeatherSnapshot | Local/região, condições, dados solares, sourceTime e fetchedAt. |
| MarketSnapshot | Tipo índice/cotação, fonte, valor, data de referência e horários de validade/coleta. |
| DcaPlanVersion | Conta/vínculo, base, orçamento, teto e vigência. |
| Recommendation | Conta, ciclo, data/fuso, leituras, limites, valor BRL, equivalente sats e status separado. |
| BudgetEntry / Commitment | Lançamentos e compromissos auditáveis, com chave de idempotência. |
| ReportedPurchase | Conta/vínculo, sugestão opcional, data real, BRL, campos privados opcionais e revisões. |
| ReserveDailyEntry | Base diária congelada, compras atribuídas, correções e saldo informativo. |
| PetReferencePortion | Snapshot compartilhado de porção, origem, vigência e reconstrução histórica. |
| Notification / Subscription | Destinatário, categoria, evento, envio, expiração e dispositivo. |
| PresentationCursor | Último evento apresentado/pulado por conta ou dispositivo visitante. |
| Outbox / Job / Audit | Entrega confiável, tentativas, travas, ações privilegiadas e consistência. |

Unicidade de saída não resolve sozinha substituição de transação; usar também recebimento lógico. Unicidade de sugestão precisa de data/ciclo e guarda de fuso, não apenas execução do job.

### 16.3 Eventos padronizados

Usar um único catálogo:

    BITCOIN_TRANSACTION_OBSERVED
    BITCOIN_TRANSACTION_CONFIRMED
    BITCOIN_TRANSACTION_REPLACED
    BITCOIN_TRANSACTION_DROPPED
    BITCOIN_CHAIN_REORG
    BITCOIN_BALANCE_RECONCILED
    PET_FEEDING_APPLIED
    PET_FEEDING_REVISED
    PET_FEEDING_INVALIDATED
    PET_ARTWORK_READY
    PET_BORN
    PET_RETURNED_TO_EGG
    PET_REAPPEARED
    PET_STATE_CHANGED
    DCA_RECOMMENDATION_GENERATED
    DCA_RECOMMENDATION_EXPIRED
    PURCHASE_REPORTED
    PURCHASE_CORRECTED
    PURCHASE_DELETED
    LOCATION_CHANGE_SCHEDULED
    LOCATION_CHANGE_APPLIED

Não usar diferentes nomes para o mesmo evento entre frontend, backend e relatórios. Eventos de compra nunca são tratados como PET_FEEDING_APPLIED.

### 16.4 Consistência e ambientes

Usar transações de banco e outbox para gravar estado e preparar publicação sem perder um dos lados. Consumidores e jobs são idempotentes, com trava de concorrência por domínio relevante.

Ambientes de teste usam rede isolada/regtest para transações, substituições e reorganizações; Mainnet permanece exclusiva da execução real. Não enviar fundos reais para validar critérios de software.

## 17. Segurança, privacidade e administração

### 17.1 Controles

- HTTPS, políticas de conteúdo, validação de entrada, limites de frequência e proteção de sessão.
- Autorização por conta em toda consulta/mutação privada.
- Construtores de resposta pública explícitos; não serializar entidades privadas integralmente.
- Verificação de permissão especial para renomear e regenerar.
- Segredos de provedor, e-mail e IA somente no servidor.
- Logs sem segredos, prompts privados ou payloads financeiros pessoais desnecessários.
- Limites de geração e deduplicação por pet para não gerar custos duplicados por várias contas.
- Sem verificação de propriedade de carteira, conforme decisão expressa; não exibir selo de propriedade verificada.

### 17.2 Exclusão e preservação

Não oferecer exclusão ou reinício voluntário do pet. Preservar aparência e eventos necessários ao endereço público, inclusive sem contas vinculadas.

Manter a possibilidade de excluir dados privados da conta, separada da preservação do pet. **CC-22:** ao excluir conta, remover compras da projeção pública, revogar sessões e subscriptions, apagar associações pessoais e conservar apenas fatos públicos e snapshots técnicos necessários ao pet sem identificadores pessoais.

Não prometer excluir transações da blockchain. **Escolha técnica de retenção:** remoção dos dados privados ativos em até 24h após execução da exclusão e expiração de backups em até 30 dias, com instrução de reaplicar exclusões ao restaurar. Esses prazos são parâmetros operacionais adotados, não respostas da entrevista.

### 17.3 Administração

Disponibilizar saúde das integrações, jobs, geração de arte, atraso de monitoramento, notificações, erros de reconciliação, custo por serviço e auditoria.

Não disponibilizar:

- mudança de endereço após 72 horas, mesmo para administrador;
- alteração livre de faixas/multiplicadores financeiros fixos;
- acesso padrão irrestrito a compras privadas de todas as contas;
- reinício de pet ou novo sorteio após aprovação.

Mudanças técnicas de configuração não reescrevem decisões históricas. Suporte consegue inspecionar identificadores operacionais e solicitar correção dentro do escopo autorizado.

## 18. Qualidade, observabilidade e entrega

### 18.1 Metas verificáveis

| Indicador | Alvo adotado |
|---|---|
| Disponibilidade do backend principal | 99,5% mensal; evolução desejada para 99,9%. |
| Observação pelo monitor → apresentação em cliente conectado | p95 até 5 s e p99 até 10 s nas condições de carga verificadas. |
| Commit do evento → envio ao cliente | p95 até 500 ms. |
| Carregamento inicial | LCP p75 até 2,5 s no perfil de teste definido. |
| Integridade | Nenhuma alimentação ou sugestão duplicada nos cenários de aceitação. |
| Recuperação de dados | RPO de até 24h para dados privados com backup diário, como alvo operacional inicial. |
| Retomada após incidente | RTO de até 4h como alvo operacional inicial, com reconciliação da rede ao retornar. |

Metas separam atraso interno de indisponibilidade de API externa e restrições de navegador. Não prometer detecção anterior à própria observação pelo provedor/monitor.

**Perfil de validação adotado:** 100 contas, 100 endereços, 100 conexões simultâneas, navegador móvel atual e desktop atual; rede simulada de 10 Mbps e RTT de 100 ms para o ensaio de carregamento. Ajustar o perfil quando houver dados reais, sem usar o ensaio como garantia para qualquer dispositivo.

### 18.2 Telemetria e operação

Medir disponibilidade, latência, filas, leituras vencidas, atraso de jobs das 06/07/08h, páginas de histórico reconciliadas, falhas de IA, duplicidades evitadas e custo por endereço/conta.

Não inventar metas comerciais ou retenção aprovadas pelo usuário: ele não definiu critérios de negócio. Telemetria de ativação e uso pode orientar evolução pessoal, com coleta mínima.

Backup e restauração devem ser exercitados, incluindo imagens aprovadas, transações, plano, compras e efeitos das exclusões. O código preparado para abertura futura não inclui chaves, dumps de produção ou dados do usuário.

### 18.3 Dependências a resolver na implementação

Selecionar/contratar dentro do orçamento do projeto:

- serviço de e-mail transacional;
- acesso CoinGlass adequado ao endpoint;
- cotação BTC/BRL e clima/geocodificação;
- provedor de geração de imagens e armazenamento;
- infraestrutura de execução baseada em containers (orquestração, banco, backups e domínio);
- licença de distribuição futura do código.

Essas são decisões técnicas/comerciais de implantação, não uma nova entrevista de produto. Custos e permissões reais devem ser verificados antes de contratar. Não se afirma que qualquer serviço foi contratado ou integrado nesta revisão.

## 19. Critérios de aceite

Cada cenário deve ser verificável com dados controlados. Identificadores abaixo são novos; substituem os critérios mais genéricos da versão 1.0.

### 19.1 Conta e compartilhamento

| ID | Dado / Quando | Resultado esperado |
|---|---|---|
| CA-001 | E-mail verificado e endereço válido são registrados. | Criar conta, vínculo e acesso a um pet; não solicitar chave ou prova de controle. |
| CA-002 | Duas contas informam o mesmo endereço. | Ambas são aceitas e veem o mesmo pet; seus dados privados permanecem isolados. |
| CA-003 | Endereço é trocado em 71h59min de conta. | Troca aceita, configuração pessoal anterior removida, pet público e outras contas preservados. |
| CA-004 | Troca é solicitada em 72h exatas ou depois, inclusive por administrador. | Operação rejeitada; prazo não reiniciado. |
| CA-005 | Várias trocas ocorrem antes do limite. | Permitidas, sempre com o prazo original. |
| CA-006 | Código de recuperação válido é usado sem acesso ao e-mail antigo. | Recuperar a mesma conta, verificar novo e-mail, revogar sessões e renovar o código. |
| CA-007 | Visitante ou segunda conta tenta renomear/regenerar. | Escrita negada; leitura pública continua permitida. |
| CA-008 | Conta criadora autorizada renomeia. | Nome muda em todas as visualizações sem apagar histórico/arte. |
| CA-009 | Página pública é consultada. | Mostrar os campos permitidos e somente dez compras declaradas por data/valor; não enviar dados privados ocultos. |
| CA-010 | Todas as contas deixam um endereço, que depois é registrado novamente. | Recuperar a mesma criatura e dados públicos reconciliados. |

### 19.2 Nascimento, reserva e saldo

| ID | Dado / Quando | Resultado esperado |
|---|---|---|
| CA-011 | Endereço novo tem saldo confirmado zero, mesmo com histórico de entradas. | Exibir ovo; não gerar criatura. |
| CA-012 | Ovo recebe entrada ainda pendente. | Movimento do ovo e mensagem de pendência; nenhum primeiro nascimento. |
| CA-013 | Primeiro saldo positivo confirma. | Gerar arte quando necessário, animar nascimento e aplicar recebimento uma vez. |
| CA-014 | Primeiro cadastro tem saldo positivo e histórico antigo. | Nascer com animação e estado reconstruído; não comemorar todas as transações antigas. |
| CA-015 | Porção é 20.000 sats e entram 5.000 sats. | Acrescentar seis horas. |
| CA-016 | Reserva é seis dias e uma entrada acrescentaria três dias. | Limitar a sete dias no instante do evento. |
| CA-017 | Transação tem saídas de 1.000 e 2.000 sats e é recebida três vezes pelo monitor. | Uma alimentação de 3.000 sats. |
| CA-018 | Pet está faminto há vários dias e recebe uma porção. | Ganhar 24h sem descontar dívida anterior. |
| CA-019 | Reserva chega exatamente a zero, 24h, 48h, 72h e 96h desde esgotamento. | Estados PENSANDO, CHATEADO, FAMINTO, CRÍTICO e HIBERNANDO, respectivamente. |
| CA-020 | Saldo positivo é gasto numa transação apenas pendente. | Manter regra de saldo confirmado; não iniciar ainda a carência. |
| CA-021 | Saldo confirmado chega a zero. | Iniciar carência; manter criatura visível antes de 24h. |
| CA-022 | Completar 24h contínuas com saldo confirmado zero. | Voltar ao ovo, mesmo com energia restante; não apagar arte. |
| CA-023 | Saldo volta a ser positivo antes das 24h e zera outra vez. | Encerrar primeira carência e iniciar outra contagem. |
| CA-024 | Ovo de pet já nascido recebe novamente e confirma. | Reaparecer a mesma criatura; não chamar IA para criar outra. |
| CA-025 | Navegador fica fechado durante consumo e carência. | Ao reabrir, mostrar estado correto pelo relógio do servidor e fatos reconciliados. |
| CA-026 | Não existe porção positiva de referência. | Mostrar falta de referência para energia, armazenar fatos e não inventar recomendação. |
| CA-027 | Surge a primeira porção positiva. | Reconstruir histórico anterior com essa referência identificada, sem sugestões históricas fictícias. |

### 19.3 Transações, arte e apresentação

| ID | Dado / Quando | Resultado esperado |
|---|---|---|
| CA-028 | Pet visível recebe transação na mempool. | Alimentação provisória e mensagem; confirmação não duplica duração. |
| CA-029 | RBF preserva recebimento e altera valor. | Atualizar o recebimento lógico e recalcular energia sem outra refeição. |
| CA-030 | RBF remove o recebimento ou descarte é comprovado. | Invalidar alimentação e reproduzir sequência válida, considerando o limite de sete dias. |
| CA-031 | Uma consulta ao provedor falha. | Não transformar ausência de resposta em descarte ou saldo zero. |
| CA-032 | Reorganização muda saldo/bloco. | Recalcular confirmações, saldo, carência e alimentação; preservar trilha. |
| CA-033 | Monitor retorna após falha com histórico de várias páginas. | Percorrer páginas necessárias, reconciliar e evitar duplicidade. |
| CA-034 | Três recebimentos novos ocorreram desde a última visita. | Apresentar três comemorações individuais. |
| CA-035 | Usuário seleciona “Pular animações”. | Encerrar apresentação sem apagar fatos nem alterar energia. |
| CA-036 | IA gera sprites dos diferentes estados. | Mesma criatura, paleta e identidade em todo o conjunto. |
| CA-037 | Usuário pede uma regeneração antes de aprovar. | Permitir uma; segunda geração válida é definitiva. |
| CA-038 | Aplicação é fechada antes da aprovação. | Preservar a primeira geração para o retorno. |
| CA-039 | IA falha tecnicamente. | Permanecer no ovo com status e retentar; não consumir sorteio voluntário. |
| CA-040 | Conta muda de cidade após aprovação. | Cenário muda na vigência definida; identidade da criatura permanece. |

### 19.4 DCA e contabilidade

| ID | Dado / Quando | Resultado esperado |
|---|---|---|
| CA-041 | Índice inteiro entre 0 e 100. | Aplicar exatamente uma das onze faixas, inclusive limites. |
| CA-042 | Índice 17, base R$ 20 e capacidade suficiente. | Valor teórico R$ 32 pelo multiplicador 1,60. |
| CA-043 | Valor teórico R$ 32, disponível R$ 25. | Sugerir R$ 25 e explicar corte. |
| CA-044 | Reserva virtual é negativa, mas há orçamento e teto. | Permitir sugestão positiva dentro dos limites. |
| CA-045 | Sugestão positiva é gerada. | Comprometer orçamento uma vez e travar cotação/equivalência em sats. |
| CA-046 | Chega meia-noite com sugestão pendente. | Manter compromisso até as 08h do próximo dia do ciclo. |
| CA-047 | Chegam as 08h de vencimento. | Liberar compromisso anterior antes de calcular o novo. |
| CA-048 | Usuário marca COMPREI OUTRO VALOR acima do orçamento. | Registrar valor real, liberar compromisso e mostrar déficit. |
| CA-049 | Compra avulsa ou atrasada é registrada. | Contabilizar pela data real; corrigir períodos posteriores. |
| CA-050 | Compra é corrigida/excluída. | Recalcular saldos e reserva; preservar snapshots de sugestões e alimentações. |
| CA-051 | Mês tem R$ 600 de aporte e R$ 500 de compras. | Carregar R$ 100 para somar ao novo orçamento. |
| CA-052 | Mês tem R$ 600 de aporte e R$ 650 de compras. | Carregar R$ -50; aporte seguinte de R$ 600 resulta em R$ 550. |
| CA-053 | Compromisso pendente atravessa o mês. | Transportar obrigação sem duplicar gasto, sobra ou reserva. |
| CA-054 | Base R$ 20 e compra do dia R$ 5 / R$ 30. | Variação de reserva +R$ 15 / -R$ 10; manter acúmulo entre meses. |
| CA-055 | Sugestão fica zero ou dados ficam indisponíveis. | Pet usa última porção positiva; não dividir por zero. |
| CA-056 | Usuário registra compra manual sem recebimento. | Nenhuma alimentação, nascimento ou alteração de saldo on-chain. |
| CA-057 | Plano é alterado após sugestão. | Mudança vale para a próxima; referência corrente permanece. |

### 19.5 Tempo, falhas, privacidade e dispositivos

| ID | Dado / Quando | Resultado esperado |
|---|---|---|
| CA-058 | Chegam 06h, 07h e 08h locais. | Fazer coletas; somente às 08h gerar/notificar sugestão. |
| CA-059 | Coleta das 08h falha e há leitura válida das 07h. | Usar leitura armazenada e mostrar horário de cada componente. |
| CA-060 | Leitura é das 06h de ontem ou anterior a esse limite. | Aceitar no limite; rejeitar antes dele, conforme timestamps efetivos. |
| CA-061 | Não há dados válidos e depois o serviço retorna no mesmo ciclo. | Gerar uma sugestão tardia, com travamento e aviso único. |
| CA-062 | Localização muda após recomendação. | Aplicar novo fuso somente no dia seguinte, preservando término do ciclo. |
| CA-063 | Mudança de fuso volta a uma data já recomendada. | Não duplicar sugestão; mostrar próximo horário elegível. |
| CA-064 | Localização automática falha. | Manter última válida; usar alternativas de fuso quando não houver localização utilizável. |
| CA-065 | Notificação ocorre de madrugada com categoria autorizada. | Enviar sem horário silencioso obrigatório. |
| CA-066 | Usuário não autorizou push no navegador. | Não enviar; manter evento e sugestão consultáveis na interface. |
| CA-067 | Logout seguido de login de outra conta. | Não expor cache privado da anterior. |
| CA-068 | Navegador não suporta instalação/push/tela cheia. | Consulta básica funciona e o recurso ausente é explicado. |
| CA-069 | Redução de movimento está ativa. | Estados permanecem legíveis sem animações intensas. |
| CA-070 | Dados/backup são restaurados. | Recuperar identidade, contabilidade e snapshots; reaplicar exclusões e reconciliar rede sem duplicar. |

## 20. Critérios de consolidação e respostas não fornecidas

Os itens abaixo completam o documento sem atribuir ao usuário respostas que ele não deu.

| Código | Decisão adotada / motivo |
|---|---|
| CC-01 | Magic link para concretizar “e-mail” sem introduzir senha não solicitada. |
| CC-02 | Código de recuperação aleatório do aplicativo, renovável e protegido, pois “sim” não escolheu formato. |
| CC-03 | Troca apaga somente a configuração pessoal anterior; dados compartilhados e de terceiros sobrevivem. |
| CC-04 | Pet sem contas continua preservado e pode ser reconciliado sob demanda. |
| CC-05 | Fonte alimentar única por pet resolve conflito entre estado compartilhado e planos individuais. |
| CC-06 | Nome continua exclusivo do criador; não transferir automaticamente o direito sem resposta a respeito. |
| CC-07 | Página geral usa localização de referência; ambientes autenticados podem seguir preferências individuais. |
| CC-08 | Dez compras públicas agregadas do endereço, sem identidade da conta; não deduplicar declarações de autores diferentes por suposição. |
| CC-09 | Página publica a sugestão de referência; planos de contas secundárias permanecem individuais. |
| CC-10 | Precisão proporcional, agregação e limite cronológico impedem bônus por divisão/arredondamento. |
| CC-11 | Sem porção positiva, indicar referência pendente e aguardar o primeiro cálculo; não inventar valores. |
| CC-12 | Consumo continua no ovo; carência depende de saldo confirmado; reentrada e reorganização seguem fatos válidos. |
| CC-13 | Preservar geração ao fechar; falha técnica não consome regeneração; aprovação automática somente quando criador ausente impediria conclusão. |
| CC-14 | Alimentação que viabiliza nascimento/retorno do ovo começa na confirmação, ao contrário de pet já visível. |
| CC-15 | Cursor visual por conta, independente de fatos compartilhados e do histórico anterior. |
| CC-16 | Compras conhecidas do dia consomem teto em geração tardia; nenhuma nova sugestão para “completar”. |
| CC-17 | Distinguir momento da coleta e idade efetiva do dado; não renovar artificialmente dado antigo. |
| CC-18 | Guardas de data e ciclo em mudança de fuso impedem recomendações repetidas. |
| CC-19 | Contabilidade por mês civil congelado, sem aporte duplicado em viagem e sem rateio inicial não solicitado. |
| CC-20 | Reserva informativa por dias civis, base versionada e correção por data real. |
| CC-21 | Idade não reinicia no ovo; estatísticas reconstruídas identificam sua origem. |
| CC-22 | Exclusão de dados pessoais é distinta de excluir pet e transações públicas. |

As perguntas finais 132–145 ficaram sem resposta quando a entrevista foi encerrada. Sua resolução nesta proposta está principalmente em CC-05 a CC-15. Em especial, compartilhamento de porção alimentar e publicação agregada são decisões de produto adotadas, não meras configurações técnicas.

Não se mantêm como requisitos simultâneos decisões antigas substituídas: acesso sem registro, ausência absoluta de troca de endereço, horário sempre de Brasília, porção fixa de 10.000 sats, fome após poucas horas, aparência editável e morte definitiva.

## 21. Rastreabilidade das respostas da entrevista

Os números abaixo seguem as perguntas efetivamente respondidas nos blocos da conversa, não a lista exploratória inicial completa de 216 perguntas. A coluna “Consolidação” registra a resposta e, quando necessário, sua substituição posterior.

### 21.1 Objetivo e configuração inicial

| Resposta | Consolidação |
|---|---|
| 1 | Acumular Bitcoin com display divertido, quase um jogo. |
| 2 | Público de acumuladores individuais. |
| 3 | Dashboard como experiência de acompanhamento desejada. |
| 4 | Sem pesquisa/validação prévia solicitada. |
| 5 | Sem elementos obrigatórios a reproduzir do equipamento. |
| 6 | Sem vídeo ou outra referência oficial necessária. |
| 7 | Uso pessoal inicialmente; intenção de código aberto futuramente. |
| 8 | Financiamento próprio. |
| 9 | Sem critério de negócio definido para continuar investindo. |
| 10 | Solicitante decide prioridades. |
| 11 | Produto completo; não dividir em MVP. |
| 12 | DCA participa da entrega. |
| 13 | Um pet por endereço ativo da conta. Várias contas podem acompanhar o mesmo pet conforme 106/123. |
| 14 | Sem funcionalidades escolhidas para adiar; os limites não custodiais permanecem. |
| 15 | Sem prazo ou restrição adicional de equipe definida. |
| 16 | Intenção de suportar todos os dispositivos; operacionalizada por responsividade e alternativas conforme capacidade. |
| 17 | Português e R$, com sats/BTC para Bitcoin. |
| 18 | Critérios de lançamento não definidos pelo usuário; critérios técnicos adotados nas seções 18–19. |
| 19 | Inicialmente acesso sem autenticação por endereço; substituído por registro por e-mail em 90–91. |
| 20 | Ao abrir um endereço, recuperar seu estado persistente. |
| 21 | Sem demonstração. |
| 22 | Nome do pet como informação pessoal inicial do pet; e-mail/endereço vieram do modelo de conta posterior. |
| 23 | Sem vinculação a provedores sociais; registro final é por e-mail. |
| 24 | Endereço reconhece o pet; autenticação final reconhece a conta para escrita privada. |
| 25 | Pet inicialmente dentro do ovo. |
| 26 | Consultar todo o histórico. Nascimento condicionado ao saldo confirmado conforme 105/111/112. |
| 19A | Recuperar pet e compras; publicação refinada depois para dez compras e campos públicos definidos. |
| 19B | Visitante pode ver, não alterar. |
| 26A | Histórico anterior reconstrói estado; saldo atual zero continua no ovo conforme decisão posterior. |

### 21.2 Alimentação, DCA e contabilidade

| Resposta | Consolidação |
|---|---|
| 27 | Não oferecer exclusão/reinício do pet. |
| 28 | Alterações permitidas preservam evolução e estatísticas; aparência depois foi bloqueada. |
| 29 | Todos os estados, incluindo ovo e crítico. |
| 30 | Nome HIBERNANDO em lugar do estado extremo anterior. |
| 31 | Usuário aceitou sugestão de cadência; intervalos aprovados em 34. |
| 32 | Sempre se recupera; sem morte definitiva. |
| 33 | Quanto mais sats recebe, maior a alimentação. |
| 34 | Aprovação de limite de sete dias e faixas de 24h após esgotamento. Porção de 10.000 sats foi substituída em 36A. |
| 35 | Autorização no aparelho com código aprovada inicialmente; substituída por conta, mantendo recuperação. |
| 36 | Qualquer recebimento; quantidade sugerida deve equivaler a um dia inteiro. |
| 36A | Porção da recomendação DCA do dia equivale a 24h. Conflito com várias contas resolvido explicitamente em CC-05. |
| 37 | Transferências próprias também alimentam. |
| 38 | Reagir na mempool com mensagem de pendência; primeiro nascimento exige confirmação por 114. |
| 39 | Remover alimentação inválida e recalcular. |
| 40 | Reproduzir comemorações individualmente. |
| 41 | Saldo confirmado em destaque e pendências separadas. |
| 42 | Saídas reduzem saldo, não retiram diretamente alimentação. Regra adicional de ovo em 131. |
| 43 | Histórico completo, inclusive saídas. |
| 44 | Cada endereço tem seu próprio pet permanente; janela posterior permite trocar o vínculo da conta, não transplantar o pet. |
| 45 | Importar histórico anterior sem reproduzir comemorações antigas. |
| 46 | Botão para pular animações. |
| 47 | Sem sugestão positiva nova, manter última porção positiva para o jogo. |
| 48 | Usar cotação no momento da sugestão e travá-la no dia; fallback posterior autorizado em 67/71. |
| 49 | Antes da nova sugestão, vale a última porção válida. |
| 50 | Primeira porção calculada serve de referência para reconstruir o histórico anterior ao plano. |
| 51 | Usuário escolhe valor diário; não derivar do orçamento. |
| 52 | Sugestões pendentes comprometem orçamento. |
| 53 | Orçamento pode acabar antes do fim do mês. |
| 54 | Basta orçamento disponível para sugerir acima da base; reserva virtual não é requisito. |
| 55 | 08h de Brasília inicialmente; substituído por 08h local em 73. |
| 56 | Liberar compromisso sem resposta somente às 08h do dia seguinte, não à meia-noite. |
| 57 | Registrar compras acima dos limites e mostrar excesso. |
| 58 | Permitir corrigir e excluir compras, com recálculo e auditoria. |
| 59 | Permitir compras avulsas. |
| 60 | Reserva = diferenças acumuladas entre valores-base e compras dos dias encerrados; pode ficar negativa. |
| 61 | Sobra acrescentada ao orçamento seguinte. |
| 62 | Reserva virtual acumulada entre meses. |
| 63 | Excesso de gasto descontado do orçamento seguinte. |
| 64 | Compra atrasada entra na data real e recalcula períodos posteriores. |
| 65 | Multiplicadores aprovados com pedido de mais faixas; onze faixas aprovadas em 70. |
| 66 | Multiplicadores fixos. |
| 67 | Retentar, coletar às 06h/07h, guardar e usar último dado válido se falhar às 08h. |
| 68 | Alterações de plano valem para a próxima sugestão. |
| 69 | Todas as quatro categorias de notificação inicialmente habilitadas. |

### 21.3 Localização, visual e publicação

| Resposta | Consolidação |
|---|---|
| 70 | Aprovar as onze faixas especificadas na seção 10. |
| 71 | Usar dados do dia anterior no máximo desde as 06h. |
| 72 | Localização automática preferida, com troca manual. |
| 73 | Sugestão sempre às 08h no fuso local. |
| 74 | Criatura original diferente em cada criação, em 2D. |
| 75 | Notificações podem aparecer de madrugada. |
| 76 | Geração por IA com descrição variada, clima, localização/fuso e referências nativas. |
| 77 | Aparência aleatória conforme descrição, sem escolher previamente entre opções. |
| 78 | Não permitir alterações de aparência depois de criada/aprovada. |
| 79 | Ovo comum. |
| 80 | Endereço como base determinística da identidade; resultado final persistido para garantir a mesma arte. |
| 81 | Pixel art. |
| 82 | Depois de recomendar, só recomendar no próximo dia no fuso vigente. |
| 83 | Mudança de localização/fuso vale somente no dia seguinte. |
| 84 | Manter última localização válida. |
| 85 | Limite de validade desde 06h do dia anterior mantido para índice e cotação, coerente com 71. |
| 86 | Exibir horários e idade dos dados utilizados. |
| 87 | Publicar pet e últimas dez compras; detalhes refinados em 96–99 e 121. |
| 88 | Saldo, histórico on-chain e QR públicos. |
| 89 | Código de recuperação aceito sem formato escolhido; formato definido em CC-02. |
| 90 | Passar a ter registro de usuário vinculado a um endereço. Janela de troca definida em 94/107. |
| 91 | Acesso por e-mail. |
| 92 | Não exigir assinatura para provar controle. |
| 93 | Não exigir transferência de comprovação. |
| 94 | Permitir trocar endereço até três dias. |
| 95 | Código recupera acesso sem o e-mail. |
| 96 | Aprovar página pública com pet, dez compras, saldo, blockchain, QR, cidade/clima e sugestão DCA. |
| 97 | Nas compras públicas, somente data e valor em R$. |
| 98 | Histórico completo de compras manuais privado do titular da conta. |
| 99 | Cidade pública, sem coordenadas exatas. |
| 100 | Todos os sprites, preservando o mesmo pet entre poses. |
| 101 | Falha de IA mantém ovo e provoca novas tentativas. |
| 102 | Uma regeneração antes de confirmar. |
| 103 | Não exibir descrição/prompt. |
| 104 | Referências regionais e estereótipos permitidos; limites de conteúdo confirmados em 118. |
| 105 | Animação de nascimento; com saldo zero, só nasce quando receber e atender confirmação posterior. |

### 21.4 Contas compartilhadas e ciclo de ovo

| Resposta | Consolidação |
|---|---|
| 106 | Permitir acesso de duas ou mais contas ao mesmo endereço. |
| 107 | Janela de troca começa no registro e termina em 72h exatas. |
| 108 | Permitir várias trocas dentro da janela. |
| 109 | Apagar configuração anterior na troca; alcance restrito à conta por decisão 127. |
| 110 | Nem administração pode trocar depois do prazo. |
| 111 | Histórico antigo com saldo atual zero fica no ovo. |
| 112 | Saldo positivo no cadastro permite nascimento e reconstrução integral. |
| 113 | Ao zerar saldo, volta ao ovo; renasce a mesma criatura. Carência acrescentada em 131. |
| 114 | Primeiro nascimento só após confirmação. |
| 115 | Se desaparecer o único fundamento válido do nascimento, voltar ao ovo. MemPool sozinha já não permite primeiro nascimento. |
| 116 | IA somente no nascimento. |
| 117 | Segunda geração válida é definitiva. |
| 118 | Bloquear caricaturas ofensivas, conteúdo sexual, violência explícita, marcas e pessoas reais. |
| 119 | E-mail, recuperação e dados privados nunca públicos. |
| 120 | Fuso da localização, depois dispositivo, depois America/Sao_Paulo. |
| 121 | Histórico blockchain completo público; somente dez compras manuais públicas. |
| 122 | Página pública obrigatória. |
| 123 | Mesmo pet, nome, aparência e estado para contas do mesmo endereço. |
| 124 | Planos, orçamento, reserva virtual e compras individuais por conta. |
| 125 | Compras públicas relacionadas à carteira informada; agregação concreta adotada em CC-08. |
| 126 | Só quem criou primeiro pode renomear. |
| 127 | Troca remove somente dados da conta; não apaga o pet de outras contas. |
| 128 | Preservar criatura e sprites mesmo sem contas vinculadas. |
| 129 | Somente saldo confirmado determina ovo. |
| 130 | Ovo pode se mexer com entrada pendente. |
| 131 | Pet já nascido só volta ao ovo depois de 24h se saldo ainda estiver zero. |
| 132–145 | Sem respostas; entrevista encerrada. Soluções adotadas estão sinalizadas como CC. |

## 22. Correspondência com o PRD original

Esta versão reorganiza as 81 seções originais em regras completas, sem simplesmente anexar respostas contraditórias ao texto antigo.

| Seções da v1.0 | Destino / tratamento na v2.0 |
|---|---|
| 1–5: visão, objetivo, princípios, público e jornada | Seções 1–3 e 14; objetivo pessoal de acumulação e dashboard, sem obrigação de reproduzir vídeo. |
| 6–8: autenticação, criação e endereço | Seções 4–5 e 9; e-mail, múltiplas contas por endereço, 72h e validação Mainnet. |
| 9: tela principal | Seção 14. |
| 10–17: estados e cronômetro | Seção 7; reserva proporcional, intervalos de um dia, ovo e carência. |
| 18–26: alimentação e transações | Seções 7 e 9; exceção de confirmação para ovo, RBF/reorg e idempotência. |
| 27–29: saldo, histórico e QR | Seções 9 e 14. |
| 30 e subseções: mercado/DCA | Seções 10–12; onze faixas, 08h local, dados antecipados, acúmulo de orçamento e reserva informativa. |
| 31–38: ambiente e clima | Seção 13. |
| 39–42: display, PWA e responsividade | Seção 14. |
| 43–45: animações, clima visual e som | Seções 8, 13 e 14. |
| 46: notificações | Seção 15. |
| 47–49: dashboard, gráficos e estatísticas | Seção 14.3; conjunto funcional preservado. |
| 50: personalização | Nome autorizado, localização, som e preferências; skins/idiomas/moedas extras retirados conforme respostas. |
| 51–52: múltiplos pets e seleção | Substituídos por um vínculo ativo por conta, pet por endereço e consulta pública. |
| 53–54: compartilhamento e privacidade | Seções 6 e 17; publicação obrigatória e separação de dados pessoais. |
| 55–68: arquitetura e modelos | Seção 16; modelo compartilhado, ledger individual, arte persistente e filas confiáveis. |
| 69: segurança | Seção 17. |
| 70–72: observabilidade, disponibilidade e desempenho | Seção 18. |
| 73–74: acessibilidade e idiomas | Seção 14; alvo de acessibilidade e português/BRL. |
| 75–76: administração e parâmetros | Seção 17.3; restrições expressas, multiplicadores fixos e regra versionada. |
| 77: aceite | Seção 19, com 70 cenários verificáveis. |
| 78: fora do escopo | Seção 2, mantendo ausência de custódia e execução financeira. |
| 79–81: diferenciais e experiência | Incorporados às seções 1, 7, 8 e 14. |

## 23. Referências técnicas e limites desta revisão

Fontes oficiais consultadas em 10/09/2026 para verificar premissas das integrações:

- [CoinGlass — Crypto Fear & Greed Index](https://docs.coinglass.com/reference/cryptofear-greedindex): endpoint documentado e frequência diária. Contratar acesso e validar timestamps antes da integração.
- [Blockstream — Esplora API](https://github.com/Blockstream/esplora/blob/master/API.md): transações, saídas gastas, saldo e histórico paginado. A documentação informa páginas de 25 transações confirmadas e até 50 transações na consulta de mempool por endereço, sem paginação desta última; o monitor deve reconhecer esse limite.
- [Bitcoin Core — Mempool replacements](https://github.com/bitcoin/bitcoin/blob/master/doc/policy/mempool-replacements.md): relações de conflito por entradas e substituição; não equivaler RBF a coincidência de valor/destino.
- [WebKit — Web Push for Web Apps](https://webkit.org/blog/13878/web-push-for-web-apps-on-ios-and-ipados/): requisitos de aplicação na tela inicial e interação do usuário para permissão no suporte descrito para iOS/iPadOS; validar os ambientes-alvo efetivos.
- [W3C — WCAG 2.2](https://www.w3.org/TR/WCAG22/): referência para detalhar e verificar os critérios de acessibilidade adotados.

As faixas DCA, os prazos, a exposição pública e o desenho do jogo são decisões de produto desta conversa; as fontes não validam desempenho financeiro da estratégia.

Esta entrega é uma revisão documental. Não cria aplicação, contas externas, transações, imagens ou infraestrutura. As verificações de software descritas são critérios para a implementação, e não resultados de testes já executados.
