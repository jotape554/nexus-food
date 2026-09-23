export function moeda(valor) {
  return Number(valor ?? 0).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
}

export function hora(instante) {
  if (!instante) return '';
  return new Date(instante).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
}

/** "há 3 min", "há 1 h 05 min" — usado nos cartões do painel para ver o que está atrasando. */
export function haQuantoTempo(instante, agora = Date.now()) {
  if (!instante) return '';
  const minutos = Math.max(0, Math.floor((agora - new Date(instante).getTime()) / 60000));
  if (minutos < 60) return `há ${minutos} min`;
  const h = Math.floor(minutos / 60);
  const m = String(minutos % 60).padStart(2, '0');
  return `há ${h} h ${m} min`;
}

export function telefone(numero) {
  if (!numero) return '';
  const d = numero.replace(/^55/, '');
  if (d.length === 11) return `(${d.slice(0, 2)}) ${d.slice(2, 7)}-${d.slice(7)}`;
  if (d.length === 10) return `(${d.slice(0, 2)}) ${d.slice(2, 6)}-${d.slice(6)}`;
  return numero;
}

export const STATUS_LABEL = {
  RECEBIDO: 'Novo',
  CONFIRMADO: 'Aceito',
  EM_PREPARO: 'Em preparo',
  PRONTO: 'Pronto',
  SAIU_PARA_ENTREGA: 'Saiu para entrega',
  CONCLUIDO: 'Concluído',
  CANCELADO: 'Cancelado',
};

export const MODALIDADE_LABEL = {
  RETIRADA: 'Retirada',
  ENTREGA: 'Entrega',
  CONSUMO_LOCAL: 'Consumo no local',
};

export const PAGAMENTO_LABEL = {
  DINHEIRO: 'Dinheiro',
  PIX: 'Pix',
  CARTAO_CREDITO: 'Cartão de crédito',
  CARTAO_DEBITO: 'Cartão de débito',
};

export const MOTIVO_CANCELAMENTO_LABEL = {
  RECUSADO_PELO_RESTAURANTE: 'Recusado pelo restaurante',
  ITEM_INDISPONIVEL: 'Item indisponível',
  FORA_DA_AREA_DE_ENTREGA: 'Fora da área de entrega',
  CLIENTE_DESISTIU: 'Cliente desistiu',
  NAO_ENTREGUE: 'Não foi possível entregar',
  OUTRO: 'Outro motivo',
};

export function numeroPedido(numero) {
  return `#${String(numero).padStart(3, '0')}`;
}

/**
 * Urgência do pedido no painel: 'ok' | 'atencao' | 'atrasado'.
 *
 * - Novo (ainda não aceito): conta o tempo esperando aceite — até 5 min ok, até 10 atenção.
 *   São as mesmas referências do indicador "tempo de aceite" do Nexus Score.
 * - Aceito / em preparo / pronto: compara com a previsão prometida ao cliente
 *   (criação + tempo de preparo configurado). Até 75% do prazo ok, até 100% atenção, depois atrasado.
 *   Assim uma pizza com 25 min não fica vermelha se o restaurante promete 40.
 * - Saiu para entrega: tempo desde a saída — até 30 min ok, até 45 atenção.
 */
export function urgencia(pedido, agora = Date.now()) {
  const min = (desde) => (agora - new Date(desde).getTime()) / 60000;

  if (pedido.status === 'RECEBIDO') {
    const esperando = min(pedido.criadoEm);
    return esperando < 5 ? 'ok' : esperando < 10 ? 'atencao' : 'atrasado';
  }
  if (pedido.status === 'SAIU_PARA_ENTREGA' && pedido.saiuParaEntregaEm) {
    const naRua = min(pedido.saiuParaEntregaEm);
    return naRua < 30 ? 'ok' : naRua < 45 ? 'atencao' : 'atrasado';
  }
  const inicio = new Date(pedido.criadoEm).getTime();
  const prazo = new Date(pedido.prontoPrevistoPara).getTime();
  const fracao = (agora - inicio) / Math.max(1, prazo - inicio);
  return fracao < 0.75 ? 'ok' : fracao <= 1 ? 'atencao' : 'atrasado';
}

export function minutosDesde(instante, agora = Date.now()) {
  const minutos = Math.max(0, Math.floor((agora - new Date(instante).getTime()) / 60000));
  if (minutos < 60) return `${minutos} min`;
  return `${Math.floor(minutos / 60)} h ${String(minutos % 60).padStart(2, '0')}`;
}
