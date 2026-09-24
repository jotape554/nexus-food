/**
 * Regras de negócio da demonstração. Espelham o backend para a demo se comportar igual ao
 * sistema real — se uma regra mudar lá, mude aqui também:
 *  - StatusPedido.podeIrPara / Pedido.transicionarPara   (pedidos)
 *  - PedidoService.criarPublico                         (preço, taxa, mínimo, troco)
 *  - TelefoneUtil.normalizar                            (identidade do cliente)
 *  - RelogioRestaurante.diaOperacional                  (fuso + hora de virada)
 */

export const ORDEM_STATUS = ['RECEBIDO', 'CONFIRMADO', 'EM_PREPARO', 'PRONTO', 'SAIU_PARA_ENTREGA', 'CONCLUIDO', 'CANCELADO'];

const CAMPO_HORARIO = {
  CONFIRMADO: 'confirmadoEm',
  EM_PREPARO: 'emPreparoEm',
  PRONTO: 'prontoEm',
  SAIU_PARA_ENTREGA: 'saiuParaEntregaEm',
  CONCLUIDO: 'concluidoEm',
  CANCELADO: 'canceladoEm',
};

export const RESPONSAVEL_CANCELAMENTO = {
  RECUSADO_PELO_RESTAURANTE: 'RESTAURANTE',
  ITEM_INDISPONIVEL: 'RESTAURANTE',
  FORA_DA_AREA_DE_ENTREGA: 'RESTAURANTE',
  CLIENTE_DESISTIU: 'CLIENTE',
  NAO_ENTREGUE: 'RESTAURANTE',
  OUTRO: 'RESTAURANTE',
};

export class ErroRegra extends Error {}

export function ehFinal(status) {
  return status === 'CONCLUIDO' || status === 'CANCELADO';
}

export function podeIrPara(atual, novo, modalidade) {
  if (ehFinal(atual) || novo === atual || novo === 'RECEBIDO') return false;
  if (novo === 'CANCELADO') return true;
  if (novo === 'SAIU_PARA_ENTREGA' && modalidade !== 'ENTREGA') return false;
  return ORDEM_STATUS.indexOf(novo) > ORDEM_STATUS.indexOf(atual);
}

/** Muda o status do pedido (objeto mutável), grava o horário da etapa e o evento. */
export function transicionar(pedido, novo, agoraIso, usuario, motivo) {
  if (!podeIrPara(pedido.status, novo, pedido.modalidade)) {
    throw new ErroRegra(`Não é possível mudar o pedido de ${pedido.status} para ${novo}.`);
  }
  if (novo === 'CANCELADO' && !motivo) throw new ErroRegra('Informe o motivo do cancelamento.');

  const anterior = pedido.status;
  pedido[CAMPO_HORARIO[novo]] = agoraIso;
  if (novo === 'CANCELADO') {
    pedido.motivoCancelamento = motivo;
    pedido.canceladoPor = RESPONSAVEL_CANCELAMENTO[motivo];
  }
  // Sair de RECEBIDO para frente conta como aceitar o pedido.
  if (anterior === 'RECEBIDO' && novo !== 'CANCELADO' && !pedido.confirmadoEm) pedido.confirmadoEm = agoraIso;
  pedido.status = novo;
  pedido.eventos.push({ statusAnterior: anterior, statusNovo: novo, ocorridoEm: agoraIso, usuario });
}

export function normalizarTelefone(telefone) {
  if (!telefone) return null;
  let d = String(telefone).replace(/\D/g, '').replace(/^0+/, '');
  if (d.length === 10 || d.length === 11) d = `55${d}`;
  if (!d.startsWith('55') || (d.length !== 12 && d.length !== 13)) return null;
  if (d[2] === '0') return null;
  if (d.length === 13 && d[4] !== '9') return null;
  return d;
}

export function dinheiro(valor) {
  return Math.round(Number(valor) * 100) / 100;
}

/** Dia operacional (AAAA-MM-DD) de um instante, no fuso do restaurante e com a hora de virada. */
export function diaOperacional(instanteMs, fusoHorario, horaVirada) {
  const partes = Object.fromEntries(
    new Intl.DateTimeFormat('en-CA', {
      timeZone: fusoHorario, year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hourCycle: 'h23',
    }).formatToParts(new Date(instanteMs)).map((p) => [p.type, p.value]),
  );
  const horaLocal = `${partes.hour}:${partes.minute}`;
  const data = new Date(Date.UTC(Number(partes.year), Number(partes.month) - 1, Number(partes.day)));
  if (horaLocal < horaVirada.slice(0, 5)) data.setUTCDate(data.getUTCDate() - 1);
  return data.toISOString().slice(0, 10);
}
