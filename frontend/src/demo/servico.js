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
    return {
      produtoId: produto.id,
      nomeProduto: produto.nome,
      precoUnitario: produto.preco,
      quantidade,
      subtotal: dinheiro(produto.preco * quantidade),
      observacao: itemReq.observacao?.trim() || null,
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
    itens: p.itens.map((i) => ({ nomeProduto: i.nomeProduto, quantidade: i.quantidade, subtotal: i.subtotal, observacao: i.observacao })),
  };
}
