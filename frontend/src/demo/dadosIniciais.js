import { criarPedido, mudarStatus, proximoId } from './servico';

export const VERSAO_DADOS = 4; // mude ao alterar os dados de exemplo: quem já abriu a demo recebe os novos
export const SLUG_DEMO = 'cantina-da-nona';

const CARDAPIO = {
  Massas: [
    ['Lasanha à bolonhesa', 42.9, 'Camadas de massa fresca, ragu de carne e muito queijo gratinado'],
    ['Nhoque ao sugo', 36, 'Nhoque de batata artesanal com molho de tomate italiano'],
    ['Fettuccine Alfredo', 39.5, 'Molho cremoso de parmesão e manteiga'],
    ['Espaguete alho e óleo', 29.5, 'Clássico com alho dourado e salsinha', false],
  ],
  Pizzas: [
    ['Margherita', 49.9, 'Molho de tomate, muçarela de búfala e manjericão'],
    ['Calabresa', 46, 'Calabresa fatiada, cebola roxa e azeitonas'],
    ['Quatro queijos', 54.9, 'Muçarela, provolone, gorgonzola e parmesão'],
    ['Portuguesa', 52, 'Presunto, ovos, cebola, ervilha e azeitonas'],
  ],
  Bebidas: [
    ['Refrigerante lata', 6.5, 'Coca-Cola, Guaraná ou Sprite'],
    ['Suco natural 500ml', 12, 'Laranja, limão ou maracujá'],
    ['Água sem gás', 4.5, ''],
    ['Vinho tinto (taça)', 22, 'Cabernet Sauvignon da casa'],
  ],
  Sobremesas: [
    ['Tiramisù', 24, 'Receita da nonna, com café e mascarpone'],
    ['Panna cotta', 19, 'Com calda de frutas vermelhas'],
  ],
};

const BAIRROS = [['Bela Vista', 6.5], ['Centro', 7], ['Consolação', 5], ['Jardins', 8], ['Pinheiros', 10]];

/**
 * Pedidos de exemplo: [minutos atrás, cliente, telefone, modalidade, pagamento, itens, bairro,
 * endereço, troco, observação, etapas → minutos depois da criação].
 * As idades foram escolhidas para o painel mostrar as três cores de urgência.
 */
const PEDIDOS = [
  [150, 'Carla Mendes', '11987650007', 'ENTREGA', 'PIX', [['Margherita', 2]], 'Centro', 'Rua Direita, 50 — sala 12', null, null,
    [['CONFIRMADO', 2], ['EM_PREPARO', 4], ['PRONTO', 30], ['SAIU_PARA_ENTREGA', 33], ['CONCLUIDO', 55]]],
  [120, 'Thiago Ramos', '11987650008', 'RETIRADA', 'DINHEIRO', [['Lasanha à bolonhesa', 1]], null, null, 50, null,
    [['CONFIRMADO', 1], ['EM_PREPARO', 3], ['PRONTO', 28], ['CONCLUIDO', 36]]],
  [95, 'Gustavo Lima', '11987650006', 'RETIRADA', 'PIX', [['Nhoque ao sugo', 1], ['Panna cotta', 1]], null, null, null, null,
    [['CANCELADO', 6, 'CLIENTE_DESISTIU']]],
  [44, 'Beatriz Nogueira', '11987650005', 'ENTREGA', 'PIX', [['Portuguesa', 1], ['Água sem gás', 1]], 'Consolação', 'Rua Frei Caneca, 300 — bloco B', null, null,
    [['CONFIRMADO', 2], ['EM_PREPARO', 4], ['PRONTO', 29], ['SAIU_PARA_ENTREGA', 32]]],
  [38, 'Luciana Prado', '11987650003', 'ENTREGA', 'DINHEIRO', [['Quatro queijos', 1], ['Calabresa', 1], ['Suco natural 500ml', 2]], 'Jardins', 'Alameda Santos, 1200 — casa 3', 150, 'Interfone quebrado, ligar quando chegar',
    [['CONFIRMADO', 3], ['EM_PREPARO', 6]]],
  [22, 'Pedro Henrique', '11987650002', 'RETIRADA', 'CARTAO_CREDITO', [['Lasanha à bolonhesa', 2], ['Tiramisù', 1]], null, null, null, null,
    [['CONFIRMADO', 2]]],
  [18, 'Rafael Costa', '11987650004', 'CONSUMO_LOCAL', 'CARTAO_DEBITO', [['Fettuccine Alfredo', 1], ['Vinho tinto (taça)', 1]], null, null, null, 'Mesa 7',
    [['CONFIRMADO', 1], ['EM_PREPARO', 2], ['PRONTO', 16]]],
  [7, 'Mariana Alves', '11987650001', 'ENTREGA', 'PIX', [['Margherita', 1, 'Borda bem assada'], ['Refrigerante lata', 2]], 'Bela Vista', 'Rua Treze de Maio, 800 — apto 41', null, null,
    []],
];

// ---------- histórico de vendas (para os relatórios) ----------

const NOMES = ['Ana', 'Bruno', 'Camila', 'Diego', 'Eduarda', 'Felipe', 'Gabriela', 'Henrique', 'Isabela', 'João', 'Larissa', 'Marcos',
  'Natália', 'Otávio', 'Patrícia', 'Renato', 'Sofia', 'Tiago', 'Vanessa', 'Wagner', 'Yasmin', 'Lucas', 'Beatriz', 'Rodrigo'];
const SOBRENOMES = ['Almeida', 'Barbosa', 'Cardoso', 'Duarte', 'Ferreira', 'Gomes', 'Lima', 'Moreira', 'Oliveira', 'Pereira', 'Ribeiro', 'Souza'];
const DIAS_HISTORICO = 60;
// Mais pedidos no almoço e no jantar.
const HORAS = [11, 11, 12, 12, 12, 12, 13, 13, 13, 14, 18, 19, 19, 19, 20, 20, 20, 20, 21, 21, 21, 22, 23];
// Peso por dia da semana (0 = domingo): sexta e sábado são os dias fortes.
const PESO_DIA = [1.3, 0.8, 0.9, 0.9, 1.0, 1.5, 1.7];
// Todo pedido começa por um prato (garante o pedido mínimo); repetidos saem mais.
const PRATOS = ['Margherita', 'Margherita', 'Margherita', 'Calabresa', 'Calabresa', 'Lasanha à bolonhesa', 'Lasanha à bolonhesa',
  'Quatro queijos', 'Portuguesa', 'Nhoque ao sugo', 'Fettuccine Alfredo'];
// Acompanhamentos e outros itens: repetidos saem mais.
const PREFERIDOS = ['Margherita', 'Margherita', 'Margherita', 'Calabresa', 'Calabresa', 'Lasanha à bolonhesa', 'Lasanha à bolonhesa',
  'Quatro queijos', 'Portuguesa', 'Nhoque ao sugo', 'Fettuccine Alfredo', 'Refrigerante lata', 'Refrigerante lata',
  'Refrigerante lata', 'Suco natural 500ml', 'Suco natural 500ml', 'Vinho tinto (taça)', 'Tiramisù', 'Tiramisù', 'Panna cotta', 'Água sem gás'];

/** Gerador pseudoaleatório com semente: a demo sai sempre com a mesma história. */
function sorteador(semente) {
  let s = semente;
  const proximo = () => (s = (s * 16807) % 2147483647) / 2147483647;
  return { numero: proximo, escolher: (lista) => lista[Math.floor(proximo() * lista.length)] };
}

/** Meio-dia no fuso de São Paulo (UTC-3, sem horário de verão desde 2019) do dia N dias atrás. */
function inicioDoDiaLocal(agoraMs, diasAtras) {
  const hojeSp = new Date(agoraMs - 3 * 3600000);
  return Date.UTC(hojeSp.getUTCFullYear(), hojeSp.getUTCMonth(), hojeSp.getUTCDate() - diasAtras) + 3 * 3600000;
}

function gerarHistorico(estado, agoraMs, produtoPorNome) {
  const s = sorteador(20260924);
  const clientes = Array.from({ length: 150 }, (_, i) => [
    `${NOMES[i % NOMES.length]} ${SOBRENOMES[(i * 7) % SOBRENOMES.length]}`,
    `119${String(70000000 + i * 911).padStart(8, '0')}`,
  ]);
  const frequentes = clientes.slice(0, 30);
  const bairros = estado.bairros;

  for (let d = DIAS_HISTORICO; d >= 1; d--) {
    const inicioDia = inicioDoDiaLocal(agoraMs, d);
    const diaSemana = new Date(inicioDia - 3 * 3600000).getUTCDay();
    const crescimento = 1 + (DIAS_HISTORICO - d) / 120; // o movimento vem crescendo
    const quantos = Math.round((4 + s.numero() * 4) * PESO_DIA[diaSemana] * crescimento);

    for (let n = 0; n < quantos; n++) {
      const hora = s.escolher(HORAS);
      const criadoMs = inicioDia + hora * 3600000 + Math.floor(s.numero() * 60) * 60000;
      const [nome, telefone] = s.numero() < 0.55 ? s.escolher(frequentes) : s.escolher(clientes);
      const modalidade = s.escolher(['ENTREGA', 'ENTREGA', 'ENTREGA', 'RETIRADA', 'RETIRADA', 'CONSUMO_LOCAL']);
      const itens = [{ produtoId: produtoPorNome[s.escolher(PRATOS)].id, quantidade: s.numero() < 0.2 ? 2 : 1 }];
      const extras = Math.floor(s.numero() * 3);
      for (let i = 0; i < extras; i++) {
        itens.push({ produtoId: produtoPorNome[s.escolher(PREFERIDOS)].id, quantidade: s.numero() < 0.2 ? 2 : 1 });
      }
      const pedido = criarPedido(estado, {
        chaveIdempotencia: `historico-${d}-${n}`,
        nomeCliente: nome,
        telefoneCliente: telefone,
        modalidade,
        formaPagamento: s.escolher(['PIX', 'PIX', 'PIX', 'CARTAO_CREDITO', 'CARTAO_CREDITO', 'CARTAO_DEBITO', 'DINHEIRO']),
        enderecoEntrega: modalidade === 'ENTREGA' ? 'Endereço de exemplo' : null,
        bairroId: modalidade === 'ENTREGA' ? s.escolher(bairros).id : null,
        itens,
      }, criadoMs);

      const sorte = s.numero();
      if (sorte < 0.05) {
        mudarStatus(pedido, 'CANCELADO', criadoMs + 4 * 60000, 'ITEM_INDISPONIVEL');
      } else if (sorte < 0.08) {
        mudarStatus(pedido, 'CANCELADO', criadoMs + 6 * 60000, 'CLIENTE_DESISTIU');
      } else {
        mudarStatus(pedido, 'CONFIRMADO', criadoMs + 2 * 60000);
        mudarStatus(pedido, 'EM_PREPARO', criadoMs + 4 * 60000);
        mudarStatus(pedido, 'PRONTO', criadoMs + (25 + Math.floor(s.numero() * 15)) * 60000);
        mudarStatus(pedido, 'CONCLUIDO', criadoMs + 50 * 60000);
      }
    }
  }
}

export function criarEstadoInicial(agoraMs) {
  const estado = {
    versao: VERSAO_DADOS,
    geradoEm: agoraMs,
    seq: { usuario: 2 },
    // A demo começa no Premium para mostrar tudo; a barra de cima troca o plano na hora.
    plano: 'PREMIUM',
    usuarios: [
      { id: 1, nome: 'Joana Martins', email: 'joana@cantinadanona.com.br', papel: 'ADMINISTRADOR', ativo: true, convitePendente: false },
      { id: 2, nome: 'Rafael Souza', email: 'rafael@cantinadanona.com.br', papel: 'GERENTE', ativo: true, convitePendente: false },
    ],
    restaurante: {
      id: 1,
      nome: 'Cantina da Nona',
      slug: SLUG_DEMO,
      telefone: '(11) 3222-1100',
      endereco: 'Rua Augusta, 1500 — Consolação, São Paulo',
      logoUrl: null,
      fusoHorario: 'America/Sao_Paulo',
      horaViradaDia: '04:00:00',
      aceitandoPedidos: true,
      aceitaRetirada: true,
      aceitaEntrega: true,
      aceitaConsumoLocal: true,
      tipoTaxaEntrega: 'POR_BAIRRO',
      taxaEntregaFixa: 0,
      pedidoMinimo: 25,
      tempoPreparoEstimadoMin: 35,
      // Restaurante (e cardápio) desde antes do histórico: o indicador de produtos parados precisa de 4 semanas.
      criadoEm: new Date(agoraMs - 75 * 86400000).toISOString(),
    },
    bairros: [],
    categorias: [],
    produtos: [],
    clientes: [],
    pedidos: [],
  };

  BAIRROS.forEach(([nome, taxa]) => estado.bairros.push({ id: proximoId(estado, 'bairro'), nome, taxa, ativo: true }));

  const produtoPorNome = {};
  Object.entries(CARDAPIO).forEach(([nomeCategoria, itens], ordemCategoria) => {
    const categoria = { id: proximoId(estado, 'categoria'), nome: nomeCategoria, ordem: ordemCategoria, ativa: true };
    estado.categorias.push(categoria);
    itens.forEach(([nome, preco, descricao, disponivel = true], ordem) => {
      const produto = {
        id: proximoId(estado, 'produto'), categoriaId: categoria.id, nome, descricao: descricao || null, preco,
        imagemUrl: null, disponivel: true, ativo: true, ordem, criadoEm: estado.restaurante.criadoEm,
      };
      estado.produtos.push(produto);
      produtoPorNome[nome] = produto;
      produto._disponivelFinal = disponivel;
    });
  });

  gerarHistorico(estado, agoraMs, produtoPorNome);

  PEDIDOS.forEach(([minutosAtras, nome, telefone, modalidade, pagamento, itens, bairro, endereco, troco, obs, etapas]) => {
    const criadoMs = agoraMs - minutosAtras * 60000;
    const pedido = criarPedido(estado, {
      chaveIdempotencia: `demo-${telefone}`,
      nomeCliente: nome,
      telefoneCliente: telefone,
      modalidade,
      formaPagamento: pagamento,
      trocoPara: troco,
      enderecoEntrega: endereco,
      bairroId: bairro ? estado.bairros.find((b) => b.nome === bairro).id : null,
      observacao: obs,
      itens: itens.map(([produto, quantidade, observacao]) => ({ produtoId: produtoPorNome[produto].id, quantidade, observacao })),
    }, criadoMs);
    etapas.forEach(([status, minutosDepois, motivo]) => mudarStatus(pedido, status, criadoMs + minutosDepois * 60000, motivo));
  });

  // Esgota os produtos marcados só depois de gerar os pedidos de exemplo.
  estado.produtos.forEach((p) => {
    p.disponivel = p._disponivelFinal;
    delete p._disponivelFinal;
  });
  return estado;
}
