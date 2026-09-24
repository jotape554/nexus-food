import { dinheiro, diaOperacional, ErroRegra, normalizarTelefone, transicionar } from './regras';

/** Operações sobre o estado da demonstração (objeto simples, persistido pela apiFalsa). */

export const USUARIO_DEMO = 'Joana Martins';

export function proximoId(estado, tipo) {
  estado.seq[tipo] = (estado.seq[tipo] || 0) + 1;
  return estado.seq[tipo];
}

function uuid() {
  if (globalThis.crypto?.randomUUID) return globalThis.crypto.randomUUID();
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16);
  });
}

export function identificarCliente(estado, telefone, nome, agoraIso) {
  const normalizado = normalizarTelefone(telefone);
  if (!normalizado) throw new ErroRegra('Informe um telefone válido com DDD.');
  if (!nome || !nome.trim()) throw new ErroRegra('Informe seu nome.');
  let cliente = estado.clientes.find((c) => c.telefone === normalizado);
  if (cliente) {
    cliente.nome = nome.trim();
  } else {
    cliente = { id: proximoId(estado, 'cliente'), telefone: normalizado, nome: nome.trim(), criadoEm: agoraIso };
    estado.clientes.push(cliente);
  }
  return cliente;
}

const COBRANCA = {
  SOMA: (precos) => precos.reduce((a, b) => a + b, 0),
  MAIOR: (precos) => (precos.length ? Math.max(...precos) : 0),
  MEDIA: (precos) => (precos.length ? precos.reduce((a, b) => a + b, 0) / precos.length : 0),
};

/** Espelha PedidoService.opcoesEscolhidas: mesmas regras e mensagens. */
function opcoesEscolhidas(produto, ids = []) {
  if (new Set(ids).size !== ids.length) throw new ErroRegra(`A mesma opção veio repetida em "${produto.nome}". Atualize a página.`);
  const grupos = produto.grupos || [];
  const doProduto = {};
  grupos.forEach((g) => g.opcoes.forEach((o) => { doProduto[o.id] = { ...o, grupo: g }; }));
  const escolhidas = ids.map((id) => {
    const opcao = doProduto[id];
    if (!opcao) throw new ErroRegra(`As opções de "${produto.nome}" mudaram. Atualize a página e escolha de novo.`);
    if (!opcao.disponivel) throw new ErroRegra(`"${opcao.nome}" esgotou em "${produto.nome}". Escolha outra opção.`);
    return opcao;
  });
  grupos.forEach((g) => {
    const n = escolhidas.filter((o) => o.grupo.id === g.id).length;
    if (n < g.minimo) {
      throw new ErroRegra(g.minimo === 1 && g.maximo === 1
        ? `Escolha ${g.nome.toLowerCase()} em "${produto.nome}".`
        : `Escolha pelo menos ${g.minimo} em ${g.nome} ("${produto.nome}").`);
    }
    if (n > g.maximo) throw new ErroRegra(`Escolha no máximo ${g.maximo} em ${g.nome} ("${produto.nome}").`);
  });
  return escolhidas;
}

/** Preço do produto + o valor de cada grupo (soma, mais cara ou média). */
export function precoComOpcoes(produto, escolhidas) {
  let total = produto.preco;
  (produto.grupos || []).forEach((g) => {
    total += COBRANCA[g.cobranca || 'SOMA'](escolhidas.filter((o) => o.grupo.id === g.id).map((o) => o.preco));
  });
  return dinheiro(total);
}

/** "A partir de": o mais barato possível em cada grupo obrigatório (ProdutoResponse.precoMinimo). */
export function precoMinimo(produto) {
  let total = produto.preco;
  (produto.grupos || []).forEach((g) => {
    if (g.minimo <= 0) return;
    const precos = g.opcoes.filter((o) => o.disponivel).map((o) => o.preco).sort((a, b) => a - b).slice(0, g.minimo);
    if (precos.length < g.minimo) return;
    total += COBRANCA[g.cobranca || 'SOMA'](precos);
  });
  return dinheiro(total);
}

/** Espelha PedidoService.criarPublico: nada de preço vindo do cliente; tudo recalculado aqui. */
export function criarPedido(estado, req, agoraMs) {
  const r = estado.restaurante;
  const agoraIso = new Date(agoraMs).toISOString();

  const existente = estado.pedidos.find((p) => p.chaveIdempotencia === req.chaveIdempotencia);
  if (existente) return existente;

  if (!r.aceitandoPedidos) throw new ErroRegra('O restaurante não está aceitando pedidos agora.');
  const aceita = { RETIRADA: r.aceitaRetirada, ENTREGA: r.aceitaEntrega, CONSUMO_LOCAL: r.aceitaConsumoLocal }[req.modalidade];
  if (!aceita) throw new ErroRegra('Este restaurante não atende nessa modalidade.');
  if (!req.itens?.length) throw new ErroRegra('Adicione pelo menos um item ao pedido.');

  const itens = req.itens.map((itemReq) => {
    const produto = estado.produtos.find((p) => p.id === itemReq.produtoId);
    const categoria = produto && estado.categorias.find((c) => c.id === produto.categoriaId);
    if (!produto || !produto.ativo || !categoria?.ativa) {
      throw new ErroRegra('Um dos produtos do pedido não está mais no cardápio. Atualize a página.');
    }
    if (!produto.disponivel) throw new ErroRegra(`"${produto.nome}" esgotou. Remova-o do pedido para continuar.`);
    const quantidade = Math.max(1, Math.min(99, Number(itemReq.quantidade) || 1));
    const escolhidas = opcoesEscolhidas(produto, (itemReq.opcoes || []).map(Number));
    const precoUnitario = precoComOpcoes(produto, escolhidas);
    if (precoUnitario <= 0) throw new ErroRegra(`"${produto.nome}" está sem preço no cardápio. Escolha outro produto.`);
    return {
      produtoId: produto.id,
      nomeProduto: produto.nome,
      precoUnitario,
      quantidade,
      subtotal: dinheiro(precoUnitario * quantidade),
      observacao: itemReq.observacao?.trim() || null,
      opcoes: escolhidas.map((o) => ({ grupo: o.grupo.nome, nome: o.nome, preco: o.preco })),
    };
  });

  const subtotal = dinheiro(itens.reduce((s, i) => s + i.subtotal, 0));
  if (subtotal < r.pedidoMinimo) {
    throw new ErroRegra(`O pedido mínimo deste restaurante é R$ ${r.pedidoMinimo.toFixed(2).replace('.', ',')} (sem a taxa de entrega).`);
  }

  let taxaEntrega = 0;
  let bairroEntrega = null;
  let enderecoEntrega = null;
  if (req.modalidade === 'ENTREGA') {
    if (!req.enderecoEntrega?.trim()) throw new ErroRegra('Informe o endereço de entrega.');
    enderecoEntrega = req.enderecoEntrega.trim();
    if (r.tipoTaxaEntrega === 'POR_BAIRRO') {
      if (!req.bairroId) throw new ErroRegra('Escolha o bairro de entrega.');
      const bairro = estado.bairros.find((b) => b.id === req.bairroId && b.ativo);
      if (!bairro) throw new ErroRegra('O restaurante não entrega nesse bairro.');
      taxaEntrega = bairro.taxa;
      bairroEntrega = bairro.nome;
    } else {
      taxaEntrega = r.taxaEntregaFixa;
    }
  }

  const total = dinheiro(subtotal + taxaEntrega);
  let trocoPara = null;
  if (req.formaPagamento === 'DINHEIRO' && req.trocoPara != null) {
    if (req.trocoPara < total) throw new ErroRegra('O valor para troco deve ser maior ou igual ao total do pedido.');
    trocoPara = dinheiro(req.trocoPara);
  }

  const cliente = identificarCliente(estado, req.telefoneCliente, req.nomeCliente, agoraIso);
  const dia = diaOperacional(agoraMs, r.fusoHorario, r.horaViradaDia);
  const numeroDia = estado.pedidos.filter((p) => p.diaOperacional === dia).reduce((m, p) => Math.max(m, p.numeroDia), 0) + 1;

  const pedido = {
    id: proximoId(estado, 'pedido'),
    numeroDia,
    codigoPublico: uuid(),
    chaveIdempotencia: req.chaveIdempotencia,
    status: 'RECEBIDO',
    modalidade: req.modalidade,
    formaPagamento: req.formaPagamento,
    trocoPara,
    enderecoEntrega,
    bairroEntrega,
    observacao: req.observacao?.trim() || null,
    subtotal,
    taxaEntrega,
    total,
    diaOperacional: dia,
    prontoPrevistoPara: new Date(agoraMs + r.tempoPreparoEstimadoMin * 60000).toISOString(),
    criadoEm: agoraIso,
    confirmadoEm: null,
    emPreparoEm: null,
    prontoEm: null,
    saiuParaEntregaEm: null,
    concluidoEm: null,
    canceladoEm: null,
    motivoCancelamento: null,
    canceladoPor: null,
    clienteId: cliente.id,
    itens,
    eventos: [{ statusAnterior: null, statusNovo: 'RECEBIDO', ocorridoEm: agoraIso, usuario: null }],
  };
  estado.pedidos.push(pedido);
  return pedido;
}

export function mudarStatus(pedido, novo, agoraMs, motivo) {
  transicionar(pedido, novo, new Date(agoraMs).toISOString(), USUARIO_DEMO, motivo);
}

/** Formato de PedidoResponse (painel). */
export function paraPainel(estado, p, comEventos) {
  const cliente = estado.clientes.find((c) => c.id === p.clienteId);
  const { chaveIdempotencia, clienteId, eventos, ...resto } = p;
  return {
    ...resto,
    cliente: { id: cliente.id, nome: cliente.nome, telefone: cliente.telefone },
    itens: p.itens,
    eventos: comEventos ? eventos : [],
  };
}

/** Formato de AcompanhamentoPedidoResponse (público: sem dados do cliente). */
export function paraAcompanhamento(estado, p) {
  const r = estado.restaurante;
  return {
    codigoPublico: p.codigoPublico,
    numeroDia: p.numeroDia,
    status: p.status,
    modalidade: p.modalidade,
    formaPagamento: p.formaPagamento,
    subtotal: p.subtotal,
    taxaEntrega: p.taxaEntrega,
    total: p.total,
    criadoEm: p.criadoEm,
    prontoPrevistoPara: p.prontoPrevistoPara,
    confirmadoEm: p.confirmadoEm,
    emPreparoEm: p.emPreparoEm,
    prontoEm: p.prontoEm,
    saiuParaEntregaEm: p.saiuParaEntregaEm,
    concluidoEm: p.concluidoEm,
    canceladoEm: p.canceladoEm,
    motivoCancelamento: p.motivoCancelamento,
    restauranteNome: r.nome,
    restauranteSlug: r.slug,
    restauranteTelefone: r.telefone,
    itens: p.itens.map((i) => ({ nomeProduto: i.nomeProduto, quantidade: i.quantidade, subtotal: i.subtotal, observacao: i.observacao, opcoes: i.opcoes || [] })),
  };
}
