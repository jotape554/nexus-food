# Plano de arquitetura — Nexus Food (aprovado na Fase 1)

> Status: **aprovado** (decisões na seção 1). Nenhum código foi escrito nesta fase.
> Base analisada: `main` em `72f1ec8`.

---

## 0. O que existe hoje (e o que dá pra reaproveitar)

Não existe `CLAUDE.md` no repositório — as convenções abaixo foram tiradas do próprio código.

| Camada | Hoje | Reaproveita? |
|---|---|---|
| Backend | Spring Boot 3.3 / Java 21, JPA, PostgreSQL, JWT stateless, Lombok | Sim — base inteira |
| Multi-tenant | coluna `barbearia_id` em toda entidade; tenant vem **só do token** (`SecurityUtils.barbeariaAtualId()`), nunca do payload | Sim — mesmo padrão, tenant passa a ser `Restaurante` |
| Planos | `PlanoSaas` (BASICO/PROFISSIONAL/PREMIUM, cumulativos) + `Recurso` (plano mínimo por recurso) + `RecursoGateFilter` (prefixo de rota → 402 com `upgradeNecessario`) + `AssinaturaGateFilter` (trial/ativa). Trial libera tudo. | Sim — com dois ajustes (seção 6) |
| Cobrança | Stripe Checkout + Portal + webhook | Sim, sem mudanças |
| Público | `/public/barbearias/{slug}`, cliente localizado/criado por telefone, `RateLimitFilter` | Padrão reaproveitado (cardápio/pedido público) |
| Relatórios/Dashboard | `DashboardService`/`RelatorioService` carregam **todas** as linhas do tenant e agregam em memória (`findAllByBarbeariaId` + streams) | **Não** — não escala para pedidos; novo módulo agrega no banco |
| Schema | `ddl-auto=update`, sem migrações | **Trocar** por Flyway (snapshots e constraints únicas não podem depender do Hibernate) |
| Datas | `LocalDate.now()` espalhado, fuso do servidor, sem `Clock` injetado | **Trocar** por fuso do restaurante + `Clock` injetável |
| Jobs | nenhum `@Scheduled` | Novo (snapshots diários) |
| Testes | integração com H2 (modo PostgreSQL) no CI | Sim — manter, somando testes unitários puros no motor do score |
| Frontend | React 18 + Vite + React Router, JS puro, CSS próprio com tokens, `api/http.js`, `RecursoBloqueado` | Sim (seção 7) |

Pontos de atenção encontrados na base que afetam o plano:
- `ClienteService.buscarOuCriarPorTelefone` compara o telefone **cru**: "(11) 99999-0000" e "11999990000" viram dois clientes. Para pedidos isso quebra recompra/retenção → telefone **normalizado** + constraint única.
- `RecursoGateFilter` usa `Map.of(...)` + `findFirst` por prefixo: ordem não determinística se dois prefixos se sobrepuserem. Resolver na Fase 5.

---

## 1. Decisões tomadas

1. **Produtos separados.** O SaaS de restaurante é outro produto, em **repositório próprio**, criado a partir desta base (plataforma: auth, tenant, assinatura/Stripe, gates de plano, rate limit, e-mail). O domínio de barbearia não vai para lá, e nada muda no BarberFlow.
2. **O próprio restaurante configura, no painel,** quais modalidades aceita (`RETIRADA`, `ENTREGA`, `CONSUMO_LOCAL`) e a taxa de entrega: **fixa** ou **por bairro** (lista de bairros com a taxa de cada um; bairro fora da lista = não entrega). Também configura o pedido mínimo e o tempo estimado de preparo.
3. **Sem pagamento online.** A forma de pagamento é só informada no pedido e paga na entrega/balcão.
4. **Nota provisória** (explicada na seção 5.6): adotada. A partir de 28 dias aparece uma nota "em formação" com o que já dá para medir; com 56 dias vira a nota completa.
5. **Planos da seção 6 aprovados.**

## 2. Módulos

Monólito modular. Para o código novo, pacote **por módulo** (cada um com `model/`, `repository/`, `service/`, `controller/`, `dto/` dentro), mantendo o estilo de camadas que já existe. Regra de dependência: setas só de cima para baixo.

```
plataforma   auth, usuários, tenant (Restaurante), assinatura/Stripe, gates de plano, rate limit, e-mail
   ↑
catalogo     Categoria, Produto, cardápio público
   ↑
clientes     Cliente (identidade = telefone normalizado por restaurante)
   ↑
pedidos      Pedido, ItemPedido, PedidoEvento, máquina de status, painel, pedido público
   ↑
relatorios   leitura agregada (dia/semana/mês) — só lê pedidos, nunca escreve
   ↑
analytics    (Nexus) snapshots diários, indicadores, score, insights — só lê pedidos/catalogo
```

- `relatorios` e `analytics` **nunca** alteram pedidos. Os dois usam a mesma classe de cálculo de métricas (seção 4), então o número do relatório e o do snapshot não podem divergir.
- `analytics` não depende de `relatorios`.

---

## 3. Entidades (Fase 2)

Todas com `restaurante_id NOT NULL` + índice começando por `restaurante_id`. Valores em `BigDecimal`/`NUMERIC(12,2)`. Datas-hora em `TIMESTAMPTZ` (`Instant`).

**Restaurante** (tenant — substitui `Barbearia`)
`id, nome, slug (único), telefone, endereco, logoUrl, fusoHorario (default America/Sao_Paulo), horaViradaDia (default 04:00), aceitandoPedidos, modalidadesAceitas, tipoTaxaEntrega (FIXA | POR_BAIRRO), taxaEntregaFixa, pedidoMinimo, tempoPreparoEstimadoMin, planoSaas, statusAssinaturaSaas, dataFimTrial, stripeCustomerId, stripeSubscriptionId, criadoEm`

- `horaViradaDia` define o **dia operacional**: pedido às 00:40 de sábado num restaurante que fecha à 01:00 conta como sexta. Sem isso, relatório diário e snapshot ficam errados para quem trabalha de noite.

**BairroEntrega** `id, restaurante, nome, taxa, ativo` — usado quando `tipoTaxaEntrega = POR_BAIRRO`. O pedido guarda a taxa cobrada (cópia), então mudar a taxa do bairro não altera pedidos antigos.

**Usuario** — igual ao atual; papéis `ADMINISTRADOR`, `GERENTE`, `ATENDENTE` (substitui `PROFISSIONAL`).

**Categoria** `id, restaurante, nome, ordem, ativa`

**Produto** `id, restaurante, categoria, nome, descricao, preco, imagemUrl, disponivel (esgotado hoje), ativo (soft delete), ordem, criadoEm`
- Produto nunca é apagado de verdade se já foi vendido (histórico e "produtos sem venda" dependem disso).

**Cliente** `id, restaurante, telefone (normalizado, só dígitos com DDI: 5511999990000), nome, criadoEm`
- `UNIQUE (restaurante_id, telefone)`; criação por upsert (a constraint resolve corrida de dois pedidos simultâneos).
- Endereço fica no pedido (cliente pode pedir de lugares diferentes).

**Pedido**
`id, restaurante, cliente, numeroDia (sequencial por restaurante/dia operacional — o "#27" que a cozinha grita), codigoPublico (UUID, acompanhamento pelo cliente), chaveIdempotencia, modalidade, status, formaPagamento, trocoPara, enderecoEntrega, observacao, subtotal, taxaEntrega, total, diaOperacional, prontoPrevistoPara, criadoEm, confirmadoEm, emPreparoEm, prontoEm, saiuParaEntregaEm, concluidoEm, canceladoEm, motivoCancelamento, canceladoPor`

**ItemPedido** `id, pedido, produto, nomeProduto, precoUnitario, quantidade, subtotal, observacao`
- `nomeProduto`/`precoUnitario` são **cópia** do momento da venda: mudar preço amanhã não reescreve o faturamento de ontem.

**PedidoEvento** `id, pedido, statusAnterior, statusNovo, ocorridoEm, usuario (nulo = cliente/sistema)`
- "Status e horário de cada mudança": o evento é o registro de auditoria completo (inclusive quem mudou); as colunas `*Em` no pedido são a cópia desnormalizada para as métricas de tempo não precisarem de join. Os dois são gravados na mesma transação, pelo mesmo método.

**Máquina de status**
```
RECEBIDO → CONFIRMADO → EM_PREPARO → PRONTO → (SAIU_PARA_ENTREGA, só ENTREGA) → CONCLUIDO
RECEBIDO | CONFIRMADO | EM_PREPARO | PRONTO → CANCELADO (motivo obrigatório)
```
- Pular etapas intermediárias é permitido para frente (ex.: CONFIRMADO → CONCLUIDO no balcão); os timestamps pulados ficam nulos — e os indicadores de tempo tratam isso (seção 5.5, cobertura).
- `motivoCancelamento`: `RECUSADO_PELO_RESTAURANTE, ITEM_INDISPONIVEL, CLIENTE_DESISTIU, NAO_ENTREGUE, OUTRO`; `canceladoPor`: `RESTAURANTE | CLIENTE`.
- **Venda = pedido CONCLUIDO.** Não crio `Receita` separada: o faturamento sai direto dos pedidos concluídos (uma fonte só).

---

## 4. Fluxo de dados e onde fica cada regra

```
[Cardápio público /r/{slug}] ──POST /public/restaurantes/{slug}/pedidos──▶ PedidoService.criarPublico
     valida restaurante aberto/modalidade/pedido mínimo
     recalcula preços NO SERVIDOR a partir do Produto (ignora preço vindo do front)
     normaliza telefone → upsert Cliente
     calcula diaOperacional, numeroDia, prontoPrevistoPara
     grava Pedido + Itens + PedidoEvento(RECEBIDO)   ← uma transação
                         │
[Painel de pedidos] ◀── GET /api/pedidos?status=... (polling 5–10s)
     PATCH /api/pedidos/{id}/status ──▶ Pedido.transicionarPara(novo, agora, usuario)
                         │
[Fase 3] RelatorioVendasService ── consultas GROUP BY dia_operacional ──▶ /api/relatorios/vendas
                         │                          (usa MetricasCalculator)
[Fase 4] Job diário ──▶ SnapshotDiario + SnapshotProdutoDiario (MetricasCalculator, mesmo código)
                   ──▶ NexusScoreEngine(janela de snapshots, regra vN) ──▶ NexusScore + NexusScoreIndicador
                   ──▶ InsightEngine(templates) ──▶ NexusInsight
                         │
[Fase 5] gates de plano controlam LEITURA de /api/relatorios e /api/nexus (o cálculo roda para todos)
```

| Regra | Onde mora | Por quê |
|---|---|---|
| Isolamento de tenant | repositórios sempre recebem `restauranteId` vindo de `SecurityUtils`; nunca do payload | já é o padrão da base; teste de integração cobre acesso cruzado |
| Transições de status + timestamps | método `Pedido.transicionarPara(...)` + mapa de transições no enum `StatusPedido` | regra num lugar só, testável sem Spring |
| Quem pode transicionar (papel) | `PedidoService` | depende do usuário logado |
| Preço/total do pedido | `PedidoService` (servidor) | nunca confiar no front público |
| Normalização de telefone | `TelefoneUtil` (como `CpfValidator`) | usado no pedido e na busca |
| Dia operacional | `RelogioRestaurante` (usa `Clock` injetado + fuso + virada) | testes conseguem "viajar no tempo"; hoje `LocalDate.now()` impede |
| Idempotência do pedido público | `chaveIdempotencia` (UUID gerado no front) com `UNIQUE (restaurante_id, chave)` | clique duplo/rede ruim não gera pedido em dobro |
| Métricas (faturamento, ticket, tempos...) | `MetricasCalculator` | relatório e snapshot usam o mesmo código |
| Score | `NexusScoreEngine` — função pura (snapshots + regra → resultado), sem banco | testável com tabelas de casos |
| Insights | `InsightEngine` + catálogo de templates | determinístico, auditável |
| Acesso por plano | `Recurso`/`PlanoSaas` + gate | já existe |

Pedido público: sem login, identificado pelo telefone. Consequência de segurança: **nenhum endpoint público devolve dados do cliente a partir do telefone** (histórico, endereço, nome). O acompanhamento é só por `codigoPublico` (UUID impossível de adivinhar). `RateLimitFilter` passa a cobrir o POST de pedido.

---

## 5. Nexus Analytics (Fase 4)

### 5.1 Snapshots diários

**SnapshotDiario** — `UNIQUE (restaurante_id, dia_operacional)`
`pedidosRecebidos, pedidosConcluidos, pedidosCanceladosRestaurante, pedidosCanceladosCliente, faturamento, itensVendidos, clientesUnicos, clientesNovos, clientesRecorrentes, tempoAceiteMedianaSeg, qtdComTempoAceite, pedidosComPrevisao, pedidosProntosNoPrazo, pedidosPorHora (24 posições), faturamentoPorModalidade, versaoCalculo, calculadoEm`

**SnapshotProdutoDiario** — `UNIQUE (restaurante_id, dia_operacional, produto_id)`: `quantidade, faturamento`

Regras do job:
- `@Scheduled` de hora em hora; para cada restaurante cujo horário local já passou da `horaViradaDia`, gera o snapshot do dia operacional anterior.
- Recalcula também os **3 dias anteriores** (pedido cancelado ou concluído depois da virada).
- Upsert pela chave única → rodar duas vezes não duplica.
- Na implantação da Fase 4, **backfill** de todos os dias desde o primeiro pedido — é por isso que pedidos vêm antes: o score já nasce com histórico.
- Os snapshots são calculados para **todos** os restaurantes, em qualquer plano: quem fizer upgrade já encontra o histórico pronto.

Definições usadas abaixo (todas só com dados de pedido/catálogo):
- *Janela atual (J)* = últimos **28 dias operacionais** fechados. *Janela anterior (J-1)* = os 28 dias antes dela. 28 = 4 semanas completas, então cada janela tem o mesmo número de sextas e sábados — comparar 30 dias com 30 dias distorce por dia da semana.
- *Cliente recorrente* no dia = cliente com pedido concluído no dia **e** algum pedido concluído antes.
- *Cliente novo* = primeiro pedido concluído da vida dele naquele restaurante.

### 5.2 Áreas, indicadores e fórmulas

| Área (peso) | Indicador (peso na área) | Fórmula |
|---|---|---|
| **Vendas (35%)** | V1 Crescimento do faturamento (50%) | `fat(J) / fat(J-1) − 1` |
| | V2 Evolução do ticket médio (25%) | `ticket(J) / ticket(J-1) − 1`, com `ticket = fat / pedidosConcluidos` |
| | V3 Regularidade semanal (25%) | coeficiente de variação do faturamento das 4 semanas de J: `desvioPadrão / média` |
| **Clientes (30%)** | C1 Taxa de recompra (50%) | clientes únicos em J com pedido concluído anterior a J ÷ clientes únicos em J |
| | C2 Retenção de novos (30%) | dos clientes cujo 1º pedido caiu em J-1, % que voltou a pedir em até 28 dias após o 1º pedido |
| | C3 Crescimento da base ativa (20%) | `clientesUnicos(J) / clientesUnicos(J-1) − 1` |
| **Operação (25%)** | O1 Cancelamento pelo restaurante (45%) | cancelados com `canceladoPor = RESTAURANTE` ÷ pedidos recebidos em J |
| | O2 Tempo de aceite (30%) | mediana de `confirmadoEm − criadoEm` em J |
| | O3 Pontualidade (25%) | pedidos com `prontoEm ≤ prontoPrevistoPara` ÷ pedidos com `prontoEm` preenchido |
| **Cardápio (10%)** | K1 Produtos parados (100%) | produtos ativos há ≥ 28 dias sem nenhuma venda em J ÷ produtos ativos há ≥ 28 dias |

Ficaram de fora de propósito (o sistema não tem o dado): margem/lucro (não há custo de produto), avaliação/NPS, tempo de entrega real (não há confirmação do entregador), comparação com outros restaurantes (só depois de termos base para calibrar).

### 5.3 Pontuação de cada indicador (0–100)

Interpolação **linear** entre três âncoras (piso → 0, referência → 60, teto → 100), com trava nos extremos. Sem faixas em degrau, para uma variação mínima não derrubar 25 pontos. Referência = 60 = "estável/saudável".

| Indicador | 0 pontos | 60 pontos | 100 pontos |
|---|---|---|---|
| V1 Crescimento do faturamento | ≤ −25% | 0% | ≥ +15% |
| V2 Ticket médio | ≤ −15% | 0% | ≥ +10% |
| V3 Regularidade (CV) | ≥ 0,50 | 0,25 | ≤ 0,10 |
| C1 Recompra | ≤ 10% | 30% | ≥ 50% |
| C2 Retenção de novos | ≤ 5% | 20% | ≥ 35% |
| C3 Base ativa | ≤ −30% | 0% | ≥ +20% |
| O1 Cancelamento pelo restaurante | ≥ 12% | 5% | ≤ 2% |
| O2 Tempo de aceite (mediana) | ≥ 15 min | 5 min | ≤ 2 min |
| O3 Pontualidade | ≤ 60% | 85% | ≥ 95% |
| K1 Produtos parados | ≥ 60% | 30% | ≤ 10% |

As âncoras são **hipóteses iniciais** (regra v1). Com ~60 dias de dados reais de vários restaurantes, a v2 recalibra pelos percentis observados. Isso só é possível sem bagunçar o histórico por causa do versionamento (5.7).

Nota de área = média ponderada dos indicadores válidos da área. Nexus Score = média ponderada das áreas válidas, arredondada para inteiro.

### 5.4 Faixas do Nexus Score

| Nota | Faixa |
|---|---|
| 0–39 | Crítico |
| 40–59 | Atenção |
| 60–74 | Bom |
| 75–89 | Muito bom |
| 90–100 | Excelente |

Coerente com as âncoras: um restaurante estável em todos os indicadores fica em 60 = começo de "Bom".

### 5.5 Mínimos por indicador (qualidade do dado)

Um indicador que não atinge o mínimo fica **"sem dados suficientes"** e o peso dele é redistribuído proporcionalmente dentro da área. Nunca vira zero (zero puniria falta de dado como se fosse desempenho ruim).

| Indicador | Mínimo |
|---|---|
| V1, V2, C3 | J-1 completa (56 dias de histórico) e ≥ 30 pedidos concluídos em cada janela |
| V3 | 4 semanas completas em J |
| C1 | ≥ 30 clientes únicos em J |
| C2 | ≥ 15 clientes novos em J-1 (portanto 56 dias de histórico) |
| O1 | ≥ 30 pedidos recebidos em J |
| O2, O3 | ≥ 30 pedidos com o timestamp **e** cobertura ≥ 60% (pedidos com o timestamp ÷ concluídos) — se a equipe pula "Pronto" no painel, o indicador não inventa número |
| K1 | ≥ 5 produtos ativos há ≥ 28 dias |

Uma área só aparece se ≥ 50% do peso dela tiver indicadores válidos. O "peso total válido" da tabela abaixo é a soma dos pesos das **áreas que aparecem** (decisão tomada na implementação para manter a nota provisória aos 28 dias, como aprovado: Clientes + Operação + Cardápio = 65%).

### 5.6 Histórico mínimo para exibir a nota

| Situação | O que aparece |
|---|---|
| < 28 dias operacionais desde o 1º pedido **ou** < 60 pedidos concluídos em J | Sem nota. Barra "coletando dados: faltam X dias / Y pedidos". Relatórios e insights de volume já funcionam. |
| 28–55 dias, ≥ 60 pedidos concluídos em J, ≥ 60% do peso total válido | **Nota provisória** (selo visível). V1, V2, C2 e C3 ainda em formação. |
| ≥ 56 dias e ≥ 60% do peso total válido | Nota oficial |
| Qualquer momento com < 60% do peso válido | Sem nota (mostra as áreas que existirem) |

Por que existe a nota provisória: metade dos indicadores compara as últimas 4 semanas com as 4 anteriores ("cresceu ou caiu?"). Um restaurante com 1 mês de uso ainda não tem as 4 anteriores. Então, entre 28 e 55 dias, a nota é calculada só com o que já dá para medir (recompra, cancelamento, tempo de aceite, pontualidade, produtos parados, regularidade), com o selo **"Nota em formação: completa em X dias"**. Com 56 dias entram os indicadores de crescimento e ela vira a nota completa.

Observação: o trial dura 14 dias, então durante o teste o restaurante vê a barra de "coletando dados", e não a nota.

O score é recalculado todo dia, logo depois do snapshot. A tela mostra a nota atual e a variação em relação a 7 dias atrás.

### 5.7 Regras versionadas

- A definição da regra (áreas, pesos, âncoras, mínimos) fica em arquivo versionado no repo: `analytics/regras/nexus-score-v1.json`. **Uma versão publicada nunca é editada**: mudou âncora ou peso → `v2.json`. Um teste falha se o hash de uma versão publicada mudar.
- As **fórmulas** ficam em código, identificadas por código estável (`V1_CRESCIMENTO_FATURAMENTO`...). Mudar fórmula também exige versão nova.
- Tabela `nexus_score`: `restaurante_id, dia_operacional, regra_versao, nota, faixa, provisoria, calculadoEm`. Tabela `nexus_score_indicador`: `codigo, valorBruto, pontos, pesoEfetivo, status (OK | SEM_DADOS), amostra`. Toda nota exibida pode ser explicada ("por que minha nota é 58?") e reproduzida.
- Troca de versão: a vigente passa a valer daqui em diante; opcionalmente o histórico é recalculado na nova versão e gravado **ao lado** da antiga (nunca por cima), para o gráfico de evolução ficar comparável.

### 5.8 Insights por template

Catálogo em código: cada template tem `id`, `versão`, `condição` (sobre indicadores/snapshots), `severidade` (ALERTA / OPORTUNIDADE / CONQUISTA), `prioridade` e texto com placeholders. Sem IA gerando texto: o texto é determinístico e sempre corresponde a um número que dá para conferir.

Exemplos iniciais (todos com dados que o sistema terá):
- `FATURAMENTO_QUEDA`: V1 ≤ −10% → "Seu faturamento caiu {pct}% nas últimas 4 semanas em relação às 4 anteriores."
- `PRODUTOS_PARADOS`: K1 → "{n} produtos não venderam nenhuma unidade em 28 dias: {lista}. Vale revisar ou tirar do cardápio."
- `ACEITE_LENTO`: O2 > 5 min → "Metade dos pedidos esperou mais de {min} min para ser aceita."
- `CANCELAMENTO_ALTO`: O1 > 5% → "{pct}% dos pedidos foram cancelados pelo restaurante. Motivo mais comum: {motivo}."
- `HORARIO_PICO`: sempre que houver volume → "{pct}% dos seus pedidos chegam entre {inicio} e {fim}."
- `PRODUTO_EM_ALTA`: produto com ≥ 10 vendas em J e crescimento ≥ 30% → "{produto} vendeu {pct}% a mais que nas 4 semanas anteriores."
- `RECOMPRA_BOA`: C1 ≥ 40% → conquista.

Regras: no máximo 5 insights ativos por restaurante; o mesmo template não se repete em 7 dias, a menos que o número piore; cada insight grava `parametros` (JSON) e o texto renderizado em `nexus_insight`.

---

## 6. Controle de acesso por plano (Fase 5)

Reaproveita `PlanoSaas` + `Recurso` + gate (402 com `upgradeNecessario`, trial libera tudo). Proposta:

| Recurso | Básico | Profissional | Premium |
|---|---|---|---|
| Cardápio, painel de pedidos, pedido público | ✓ | ✓ | ✓ |
| Relatório diário | ✓ | ✓ | ✓ |
| Relatório semanal e mensal | até 30 dias para trás | 12 meses | histórico completo |
| Nexus Score (nota + áreas) | — | ✓ | ✓ |
| Detalhe dos indicadores + insights | — | — | ✓ |
| Usuários | 2 | 5 | ilimitado |

Ajustes técnicos:
1. Trocar o mapa de prefixos por anotação `@RequerRecurso(Recurso.X)` nos controllers, verificada num `HandlerInterceptor`. Um teste percorre todos os controllers de `/api/**` e falha se algum não tiver anotação nem estiver marcado como livre — hoje, esquecer de adicionar o prefixo no mapa libera a rota sem aviso.
2. Limites quantitativos (período máximo do relatório, nº de usuários) ficam no `PlanoSaas` e são checados no **service**, com a mesma resposta 402.
3. O front só esconde/mostra (`RecursoBloqueado`); quem decide é sempre o backend.
4. Downgrade não apaga nada; só bloqueia a leitura.

**Como ficou (Fase 5):**
- `@RequerRecurso` / `@AcessoLivre` + `RecursoInterceptor`; o `RecursoGateFilter` (mapa de prefixos) foi removido.
  `RecursoAnotacaoTest` falha se alguma rota de `/api/**` não declarar nenhum dos dois.
- `AcessoPlanoService` é o único ponto de "o que pode": teste grátis vale como Premium; assinatura ativa, o plano contratado.
- Recursos: `NEXUS_SCORE` (Profissional) devolve 402 no Básico; `NEXUS_DETALHES` (Premium) não é rota
  própria: no Profissional a mesma `/api/nexus` volta sem o valor de cada indicador e sem o texto das
  dicas (`NexusResponse.semDetalhes()`, com `insightsBloqueados` para a tela dizer quantas existem).
- Histórico do relatório: Básico = últimos 30 dias (e sempre o mês atual); Profissional = último ano
  (e sempre o atalho "12 meses"); Premium = tudo. A comparação com o período anterior continua valendo.
- Equipe (`/api/usuarios`, só administrador): convite por link de 72 h (e-mail + link para o WhatsApp),
  perfis Administrador/Gerente/Atendente, limite de usuários ativos por plano (desativado não conta).
  Trocar para um plano menor que a equipe é recusado antes de cobrar; se o plano diminuir por fora
  (portal da Stripe), quem entrou por último fica sem acesso (402 `usuarioForaDoLimite`) até o
  administrador ajustar — administradores vêm primeiro na fila.
- Troca de plano de quem já assina: `StripeGateway.trocarPreco` na assinatura existente (proporcional),
  nunca um segundo checkout. `customer.subscription.updated` sincroniza plano e situação
  (`past_due` mantém o acesso enquanto a Stripe tenta cobrar; `unpaid`/`canceled` encerram).

---

## 7. Frontend

**Manter React 18 + Vite + React Router**, que já estão na base, e somar só:
- **TanStack Query**: cache e `refetchInterval` para o painel de pedidos (polling), retry e invalidação após mudar status. Substitui os `useEffect` + `useState` manuais, que no painel viram condições de corrida.
- **Recharts** na Fase 3 (hoje os gráficos são barras em CSS; relatórios por dia/semana/mês e a evolução do score precisam de eixos e tooltip).

Por quê:
- Toda a base (auth, `http.js`, gates, tokens de CSS, `RecursoBloqueado`, telas de assinatura) é reaproveitada sem reescrever nada.
- Next.js/SSR não se paga: o cardápio é aberto por link de WhatsApp/Instagram, não por busca no Google. A prévia do link (Open Graph) dá para resolver depois com o backend servindo as meta tags da rota `/r/{slug}`.
- O cardápio público fica em **rota separada com code-splitting** (`React.lazy`), então o cliente do restaurante não baixa o código do painel. Mobile-first.
- Painel de pedidos pensado para **tablet na cozinha/balcão**: colunas por status, alerta sonoro de pedido novo. O navegador só toca som depois de uma interação, então a tela tem um botão "Ativar som".
- **Polling (5–10s) antes de WebSocket/SSE**: funciona com o JWT stateless atual, em qualquer hospedagem, e o custo é irrelevante no volume de um restaurante. Migrar para SSE depois não muda o contrato da API.
- TypeScript: não agora. Migrar os arquivos existentes custaria uma fase inteira sem entregar nada ao usuário.

---

## 8. Infraestrutura transversal (entra no começo da Fase 2)

- **Flyway** com baseline do schema atual; `ddl-auto=validate`. SQL portável para rodar também no H2 (modo PostgreSQL) do CI.
- `Clock` como bean; `RelogioRestaurante` para "agora" e "dia operacional".
- Índices: `pedidos (restaurante_id, dia_operacional)`, `pedidos (restaurante_id, status)`, `pedidos (restaurante_id, cliente_id, concluido_em)`, `itens_pedido (pedido_id)`.
- `CLAUDE.md` com as convenções deste plano (tenant só pelo token, preço no servidor, status só por `transicionarPara`, métricas só pelo `MetricasCalculator`, regra publicada é imutável).

## 9. Entregas por fase (resumo)

| Fase | Backend | Frontend | Testes-chave |
|---|---|---|---|
| 2 | Flyway, Restaurante, Categoria, Produto, Cliente, Pedido/Itens/Eventos, cardápio e pedido públicos, painel | Cardápio `/r/{slug}`, acompanhamento do pedido, CRUD de cardápio, painel de pedidos | isolamento de tenant, transições inválidas, preço adulterado no front, idempotência, telefone normalizado, virada do dia |
| 3 | `MetricasCalculator`, `/api/relatorios/vendas?agrupamento=DIA|SEMANA|MES` com agregação no banco | Tela de relatórios com gráfico | somas conferidas contra pedidos, semana ISO, borda da virada do dia |
| 4 | Snapshots + job + backfill, `NexusScoreEngine`, regra v1, `InsightEngine`, `/api/nexus` | Tela Nexus (nota, faixas, áreas, indicadores, insights, evolução) | tabela de casos das âncoras, redistribuição de peso, mínimos, imutabilidade da v1, idempotência do job |
| 5 | `@RequerRecurso`, limites por plano | bloqueios e upsell | matriz plano × rota |
