import { criarPedido, mudarStatus, proximoId } from './servico';

export const VERSAO_DADOS = 1;
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

export function criarEstadoInicial(agoraMs) {
  const estado = {
    versao: VERSAO_DADOS,
    geradoEm: agoraMs,
    seq: {},
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
      criadoEm: new Date(agoraMs - 3 * 86400000).toISOString(),
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
