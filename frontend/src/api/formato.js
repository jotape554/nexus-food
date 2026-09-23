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
