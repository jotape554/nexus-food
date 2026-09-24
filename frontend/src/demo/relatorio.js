import { diaOperacional } from './regras';

/**
 * Relatório de vendas da demonstração. Espelha RelatorioVendasService + MetricasCalculator do
 * backend (mesmas definições: venda = pedido concluído, dia = dia operacional, semana de
 * segunda a domingo, mesma regra de período de comparação).
 */

const DIA_MS = 86400000;
const arred = (v) => Math.round(v * 100) / 100;
const pct = (parte, todo) => (todo ? Math.round((parte * 1000) / todo) / 10 : 0);
const variacao = (atual, anterior) => (anterior ? Math.round(((atual - anterior) * 1000) / anterior) / 10 : null);

const paraMs = (iso) => Date.parse(`${iso}T00:00:00Z`);
const paraIso = (ms) => new Date(ms).toISOString().slice(0, 10);
const somarDias = (iso, n) => paraIso(paraMs(iso) + n * DIA_MS);
const diasEntre = (a, b) => Math.round((paraMs(b) - paraMs(a)) / DIA_MS);
const diaDaSemana = (iso) => ((new Date(paraMs(iso)).getUTCDay() + 6) % 7) + 1; // 1 = segunda
const fimDoMes = (iso) => { const d = new Date(paraMs(iso)); return paraIso(Date.UTC(d.getUTCFullYear(), d.getUTCMonth() + 1, 0)); };
const menosMeses = (iso, n) => {
  const d = new Date(paraMs(iso));
  const alvo = new Date(Date.UTC(d.getUTCFullYear(), d.getUTCMonth() - n, 1));
  const ultimo = new Date(Date.UTC(alvo.getUTCFullYear(), alvo.getUTCMonth() + 1, 0)).getUTCDate();
  alvo.setUTCDate(Math.min(d.getUTCDate(), ultimo));
  return paraIso(alvo.getTime());
};

function inicioDoGrupo(iso, agrupamento) {
  if (agrupamento === 'SEMANA') return somarDias(iso, -(diaDaSemana(iso) - 1));
  if (agrupamento === 'MES') return `${iso.slice(0, 8)}01`;
  return iso;
}
function fimDoGrupo(inicio, agrupamento) {
  if (agrupamento === 'SEMANA') return somarDias(inicio, 6);
  if (agrupamento === 'MES') return fimDoMes(inicio);
  return inicio;
}

export function periodoAnterior(inicio, fim) {
  if (inicio.endsWith('-01')) {
    const meses = (Number(fim.slice(0, 4)) - Number(inicio.slice(0, 4))) * 12 + Number(fim.slice(5, 7)) - Number(inicio.slice(5, 7)) + 1;
    const fimAnterior = fim === fimDoMes(fim) ? fimDoMes(menosMeses(`${fim.slice(0, 8)}01`, meses)) : menosMeses(fim, meses);
    return [menosMeses(inicio, meses), fimAnterior];
  }
  const dias = diasEntre(inicio, fim) + 1;
  return [somarDias(inicio, -dias), somarDias(inicio, -1)];
}

function totais(pedidos) {
  let faturamento = 0; let concluidos = 0; let cancelados = 0; let canceladosRestaurante = 0;
  const clientes = new Set();
  pedidos.forEach((p) => {
    if (p.status === 'CONCLUIDO') { faturamento += p.total; concluidos++; clientes.add(p.clienteId); }
    else if (p.status === 'CANCELADO') { cancelados++; if (p.canceladoPor === 'RESTAURANTE') canceladosRestaurante++; }
  });
  return {
    faturamento: arred(faturamento), pedidosConcluidos: concluidos, pedidosRecebidos: pedidos.length,
    ticketMedio: concluidos ? arred(faturamento / concluidos) : 0, cancelados, canceladosPeloRestaurante: canceladosRestaurante,
    taxaCancelamento: pct(cancelados, pedidos.length), clientesUnicos: clientes.size,
  };
}

function porChave(pedidos, chave) {
  const mapa = new Map();
  pedidos.filter((p) => p.status === 'CONCLUIDO').forEach((p) => {
    const k = chave(p);
    const f = mapa.get(k) || { chave: k, pedidos: 0, faturamento: 0 };
    f.pedidos++; f.faturamento += p.total;
    mapa.set(k, f);
  });
  return [...mapa.values()].map((f) => ({ ...f, faturamento: arred(f.faturamento) })).sort((a, b) => b.faturamento - a.faturamento);
}

function completar(fatias, de, ate) {
  const mapa = new Map(fatias.map((f) => [f.chave, f]));
  const lista = [];
  for (let i = de; i <= ate; i++) lista.push(mapa.get(i) || { chave: i, pedidos: 0, faturamento: 0 });
  return lista;
}

function horaLocal(iso, fuso) {
  return Number(new Intl.DateTimeFormat('en-GB', { timeZone: fuso, hour: '2-digit', hourCycle: 'h23' }).format(new Date(iso)));
}

export function gerarRelatorio(estado, q, agoraMs) {
  const r = estado.restaurante;
  const hoje = diaOperacional(agoraMs, r.fusoHorario, r.horaViradaDia);
  const fim = q.get('fim') || hoje;
  const inicio = q.get('inicio') || somarDias(fim, -29);
  const agrupamento = q.get('agrupamento') || 'DIA';
  if (fim < inicio) throw Object.assign(new Error('A data final não pode ser anterior à data inicial.'), { regra: true });
  if (diasEntre(inicio, fim) + 1 > 366) throw Object.assign(new Error('Escolha um período de até 1 ano.'), { regra: true });

  const doPeriodo = (a, b) => estado.pedidos.filter((p) => p.diaOperacional >= a && p.diaOperacional <= b);
  const pedidos = doPeriodo(inicio, fim);
  const t = totais(pedidos);
  const concluidos = pedidos.filter((p) => p.status === 'CONCLUIDO');

  // produtos (nome atual do cadastro)
  const porProduto = new Map();
  concluidos.forEach((p) => p.itens.forEach((i) => {
    const x = porProduto.get(i.produtoId) || { produtoId: i.produtoId, quantidade: 0, faturamento: 0 };
    x.quantidade += i.quantidade; x.faturamento += i.subtotal;
    porProduto.set(i.produtoId, x);
  }));
  const produtos = [...porProduto.values()]
    .map((x) => ({ ...x, nome: estado.produtos.find((p) => p.id === x.produtoId)?.nome || '—', faturamento: arred(x.faturamento) }))
    .sort((a, b) => b.faturamento - a.faturamento || b.quantidade - a.quantidade);
  const itensVendidos = produtos.reduce((s, p) => s + p.quantidade, 0);

  // clientes novos: primeiro pedido concluído da história caiu no período
  const primeiraCompra = new Map();
  estado.pedidos.filter((p) => p.status === 'CONCLUIDO').forEach((p) => {
    const atual = primeiraCompra.get(p.clienteId);
    if (!atual || p.diaOperacional < atual) primeiraCompra.set(p.clienteId, p.diaOperacional);
  });
  const clientesPeriodo = new Set(concluidos.map((p) => p.clienteId));
  const clientesNovos = [...clientesPeriodo].filter((c) => primeiraCompra.get(c) >= inicio).length;

  const [antesInicio, antesFim] = periodoAnterior(inicio, fim);
  const antes = totais(doPeriodo(antesInicio, antesFim));

  const serie = [];
  for (let g = inicioDoGrupo(inicio, agrupamento); g <= fim; g = somarDias(fimDoGrupo(g, agrupamento), 1)) {
    const gFim = fimDoGrupo(g, agrupamento);
    const de = g < inicio ? inicio : g;
    const ate = gFim > fim ? fim : gFim;
    const tg = totais(pedidos.filter((p) => p.diaOperacional >= de && p.diaOperacional <= ate));
    serie.push({ inicio: de, fim: ate, faturamento: tg.faturamento, pedidosConcluidos: tg.pedidosConcluidos, ticketMedio: tg.ticketMedio, cancelados: tg.cancelados });
  }

  const fatias = (lista) => lista.map((f) => ({ ...f, percentualFaturamento: pct(f.faturamento, t.faturamento) }));

  return {
    hoje, inicio, fim, agrupamento,
    resumo: { ...t, itensVendidos, clientesNovos, clientesRecorrentes: Math.max(0, t.clientesUnicos - clientesNovos) },
    comparacao: {
      inicio: antesInicio, fim: antesFim, faturamento: antes.faturamento, pedidosConcluidos: antes.pedidosConcluidos, ticketMedio: antes.ticketMedio,
      variacaoFaturamento: variacao(t.faturamento, antes.faturamento),
      variacaoPedidos: variacao(t.pedidosConcluidos, antes.pedidosConcluidos),
      variacaoTicketMedio: variacao(t.ticketMedio, antes.ticketMedio),
    },
    serie,
    porModalidade: fatias(porChave(pedidos, (p) => p.modalidade)),
    porFormaPagamento: fatias(porChave(pedidos, (p) => p.formaPagamento)),
    porHora: completar(porChave(pedidos, (p) => horaLocal(p.criadoEm, r.fusoHorario)), 0, 23),
    porDiaDaSemana: completar(porChave(pedidos, (p) => diaDaSemana(p.diaOperacional)), 1, 7),
    produtos: produtos.slice(0, 10).map((p) => ({ ...p, percentualFaturamento: pct(p.faturamento, t.faturamento) })),
  };
}
