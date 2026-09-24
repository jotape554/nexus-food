// A regra é o MESMO arquivo do backend: a demonstração nunca tem pesos ou âncoras diferentes.
import regra from '../../../backend/src/main/resources/analytics/regras/nexus-score-v1.json';
import { diaOperacional } from './regras';

/**
 * Nexus Score da demonstração. Espelha ColetorIndicadores + NexusScoreEngine + InsightService do
 * backend (mesmas janelas de 28 dias, mesmas fórmulas e mínimos). Aqui as somas saem direto dos
 * pedidos em vez dos resumos diários salvos — o resultado é o mesmo.
 */

const JANELA = 28;
const DIA_MS = 86400000;
const paraMs = (iso) => Date.parse(`${iso}T00:00:00Z`);
const somarDias = (iso, n) => new Date(paraMs(iso) + n * DIA_MS).toISOString().slice(0, 10);
const diasEntre = (a, b) => Math.round((paraMs(b) - paraMs(a)) / DIA_MS);
const umaCasa = (v) => Math.round(v * 10) / 10;
const pct = (parte, todo) => (todo ? (parte * 100) / todo : null);
const variacao = (atual, anterior) => (anterior ? (atual / anterior - 1) * 100 : null);

function horaLocal(iso, fuso) {
  return Number(new Intl.DateTimeFormat('en-GB', { timeZone: fuso, hour: '2-digit', hourCycle: 'h23' }).format(new Date(iso)));
}

// ---------- coleta (ColetorIndicadores) ----------

function coletar(estado, dia) {
  const r = estado.restaurante;
  const inicioJ = somarDias(dia, -(JANELA - 1));
  const fimJ1 = somarDias(inicioJ, -1);
  const inicioJ1 = somarDias(inicioJ, -JANELA);

  const primeiro = estado.pedidos.reduce((m, p) => (!m || p.diaOperacional < m ? p.diaOperacional : m), null);
  const diasHistorico = !primeiro || primeiro > dia ? 0 : diasEntre(primeiro, dia) + 1;

  const noPeriodo = (a, b) => estado.pedidos.filter((p) => p.diaOperacional >= a && p.diaOperacional <= b);
  const pJ = noPeriodo(inicioJ, dia);
  const pJ1 = noPeriodo(inicioJ1, fimJ1);
  const concl = (lista) => lista.filter((p) => p.status === 'CONCLUIDO');
  const cJ = concl(pJ);
  const cJ1 = concl(pJ1);
  const fat = (lista) => lista.reduce((s, p) => s + p.total, 0);
  const fatJ = fat(cJ);
  const fatJ1 = fat(cJ1);
  const amostraComparacao = Math.min(cJ.length, cJ1.length);
  const m = {};

  m.V1_CRESCIMENTO_FATURAMENTO = { valor: variacao(fatJ, fatJ1), amostra: amostraComparacao };
  const ticketJ = cJ.length ? fatJ / cJ.length : null;
  const ticketJ1 = cJ1.length ? fatJ1 / cJ1.length : null;
  m.V2_EVOLUCAO_TICKET = { valor: ticketJ == null || ticketJ1 == null ? null : variacao(ticketJ, ticketJ1), amostra: amostraComparacao };
  const semanas = [0, 0, 0, 0];
  cJ.forEach((p) => { const s = Math.floor(diasEntre(inicioJ, p.diaOperacional) / 7); if (s >= 0 && s < 4) semanas[s] += p.total; });
  const media = semanas.reduce((a, b) => a + b, 0) / 4;
  const cv = media ? Math.sqrt(semanas.reduce((a, v) => a + (v - media) ** 2, 0) / 4) / media : null;
  m.V3_REGULARIDADE = { valor: cv, amostra: cJ.length };

  const primeiraCompra = new Map();
  concl(estado.pedidos).filter((p) => p.diaOperacional <= dia).forEach((p) => {
    const atual = primeiraCompra.get(p.clienteId);
    if (!atual || p.diaOperacional < atual) primeiraCompra.set(p.clienteId, p.diaOperacional);
  });
  const clientesJ = new Set(cJ.map((p) => p.clienteId));
  const clientesJ1 = new Set(cJ1.map((p) => p.clienteId));
  const recorrentes = [...clientesJ].filter((c) => primeiraCompra.get(c) < inicioJ).length;
  m.C1_RECOMPRA = { valor: pct(recorrentes, clientesJ.size), amostra: clientesJ.size };
  const novosJ1 = [...primeiraCompra.entries()].filter(([, d]) => d >= inicioJ1 && d <= fimJ1).map(([c]) => c);
  const concluidosAmbos = [...cJ1, ...cJ];
  const voltaram = novosJ1.filter((c) => {
    const primeira = primeiraCompra.get(c);
    const limite = somarDias(primeira, JANELA);
    return concluidosAmbos.filter((p) => p.clienteId === c && p.diaOperacional >= primeira && p.diaOperacional <= limite).length >= 2;
  }).length;
  m.C2_RETENCAO_NOVOS = { valor: pct(voltaram, novosJ1.length), amostra: novosJ1.length };
  m.C3_BASE_ATIVA = { valor: variacao(clientesJ.size, clientesJ1.size), amostra: amostraComparacao };

  const canceladosRestaurante = pJ.filter((p) => p.status === 'CANCELADO' && p.canceladoPor === 'RESTAURANTE').length;
  m.O1_CANCELAMENTO_RESTAURANTE = { valor: pct(canceladosRestaurante, pJ.length), amostra: pJ.length };
  const aceites = pJ.filter((p) => p.confirmadoEm).map((p) => (Date.parse(p.confirmadoEm) - Date.parse(p.criadoEm)) / 60000).sort((a, b) => a - b);
  const mediana = aceites.length ? (aceites.length % 2 ? aceites[(aceites.length - 1) / 2] : (aceites[aceites.length / 2 - 1] + aceites[aceites.length / 2]) / 2) : null;
  const concluidosComAceite = cJ.filter((p) => p.confirmadoEm).length;
  m.O2_TEMPO_ACEITE = { valor: mediana, amostra: aceites.length, cobertura: cJ.length ? concluidosComAceite / cJ.length : null };
  const comPronto = cJ.filter((p) => p.prontoEm);
  const noPrazo = comPronto.filter((p) => p.prontoEm <= p.prontoPrevistoPara).length;
  m.O3_PONTUALIDADE = { valor: pct(noPrazo, comPronto.length), amostra: comPronto.length, cobertura: cJ.length ? comPronto.length / cJ.length : null };

  const vendidos = (lista) => {
    const mapa = new Map();
    lista.forEach((p) => p.itens.forEach((i) => mapa.set(i.produtoId, (mapa.get(i.produtoId) || 0) + i.quantidade)));
    return mapa;
  };
  const vendidosJ = vendidos(cJ);
  const vendidosJ1 = vendidos(cJ1);
  const inicioJms = Date.parse(`${inicioJ}T03:00:00Z`); // meia-noite em São Paulo
  const antigos = estado.produtos.filter((p) => p.ativo && Date.parse(p.criadoEm) <= inicioJms);
  const parados = antigos.filter((p) => !vendidosJ.get(p.id)).map((p) => p.nome).sort((a, b) => a.localeCompare(b, 'pt-BR'));
  m.K1_PRODUTOS_PARADOS = { valor: pct(parados.length, antigos.length), amostra: antigos.length };

  const porHora = new Array(24).fill(0);
  cJ.forEach((p) => { porHora[horaLocal(p.criadoEm, r.fusoHorario)]++; });

  const motivos = {};
  pJ.filter((p) => p.status === 'CANCELADO' && p.canceladoPor === 'RESTAURANTE' && p.motivoCancelamento)
    .forEach((p) => { motivos[p.motivoCancelamento] = (motivos[p.motivoCancelamento] || 0) + 1; });
  const motivoMaisComum = Object.entries(motivos).sort((a, b) => b[1] - a[1])[0]?.[0] || null;

  return { dia, inicioJ, diasHistorico, pedidosConcluidosJanela: cJ.length, medidas: m, fatJ, fatJ1, porHora, vendidosJ, vendidosJ1, parados, motivoMaisComum };
}

// ---------- motor (NexusScoreEngine) ----------

function pontos(a, valor) {
  const maiorMelhor = a.cem > a.zero;
  const s = maiorMelhor ? 1 : -1;
  const v = valor * s; const zero = a.zero * s; const ref = a.referencia * s; const cem = a.cem * s;
  let p;
  if (v <= zero) p = 0;
  else if (v >= cem) p = 100;
  else if (v <= ref) p = (60 * (v - zero)) / (ref - zero);
  else p = 60 + (40 * (v - ref)) / (cem - ref);
  return umaCasa(p);
}

function motivoSemDados(ind, m, diasHistorico) {
  const min = ind.minimos || {};
  if (min.diasHistorico != null && diasHistorico < min.diasHistorico) return `Precisa de ${min.diasHistorico} dias de histórico (faltam ${min.diasHistorico - diasHistorico}).`;
  if (!m) return 'Ainda não há dados para medir.';
  if (min.amostra != null && m.amostra < min.amostra) return `Poucos dados para medir: ${m.amostra} de ${min.amostra} necessários.`;
  if (min.cobertura != null && (m.cobertura == null || m.cobertura < min.cobertura)) {
    return `Só ${Math.round((m.cobertura || 0) * 100)}% dos pedidos têm esse horário registrado (mínimo ${Math.round(min.cobertura * 100)}%).`;
  }
  if (m.valor == null || Number.isNaN(m.valor)) return 'Ainda não há dados para medir.';
  return null;
}

function calcular(coleta) {
  const h = regra.historico;
  const areas = regra.areas.map((area) => {
    let pesoValido = 0; let soma = 0;
    const indicadores = area.indicadores.map((ind) => {
      const m = coleta.medidas[ind.codigo];
      const motivo = motivoSemDados(ind, m, coleta.diasHistorico);
      const pts = motivo ? null : pontos(ind.ancoras, m.valor);
      if (pts != null) { pesoValido += ind.peso; soma += pts * ind.peso; }
      return { ind, m, motivo, pts };
    });
    const exibida = pesoValido >= h.pesoValidoMinimoArea - 1e-9;
    return { area, indicadores, pesoValido, nota: exibida ? umaCasa(soma / pesoValido) : null };
  });
  const pesoAreas = areas.filter((a) => a.nota != null).reduce((s, a) => s + a.area.peso, 0);
  const notaBruta = pesoAreas ? areas.filter((a) => a.nota != null).reduce((s, a) => s + a.nota * a.area.peso, 0) / pesoAreas : 0;

  let motivoSemNota = null;
  if (coleta.diasHistorico < h.diasMinimosNota) motivoSemNota = `Coletando dados: a nota aparece com ${h.diasMinimosNota} dias de histórico (faltam ${h.diasMinimosNota - coleta.diasHistorico}).`;
  else if (coleta.pedidosConcluidosJanela < h.pedidosConcluidosMinimos) motivoSemNota = `Coletando dados: são necessários ${h.pedidosConcluidosMinimos} pedidos concluídos nas últimas 4 semanas (${coleta.pedidosConcluidosJanela} até agora).`;
  else if (pesoAreas < h.pesoValidoMinimo - 1e-9) motivoSemNota = 'Ainda não há dados suficientes nos indicadores para uma nota confiável.';

  const situacao = motivoSemNota ? 'COLETANDO' : coleta.diasHistorico < h.diasNotaOficial ? 'PROVISORIA' : 'OFICIAL';
  const nota = situacao === 'COLETANDO' ? null : Math.round(notaBruta);
  const faixa = nota == null ? null : regra.faixas.find((f) => nota >= f.de && nota <= f.ate);
  return { situacao, nota, faixa, motivoSemNota, pesoValido: Math.round(pesoAreas * 10000) / 10000, areas, pesoAreas };
}

// ---------- insights (InsightService.CATALOGO) ----------

const nf = (v, casas) => Number(v).toLocaleString('pt-BR', { maximumFractionDigits: casas });
const p100 = (v) => `${nf(v, Math.abs(v) < 10 ? 1 : 0)}%`;
const moeda = (v) => Number(v).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' }).replace(' ', ' ');
const MOTIVO = {
  RECUSADO_PELO_RESTAURANTE: 'recusado pelo restaurante', ITEM_INDISPONIVEL: 'item indisponível', FORA_DA_AREA_DE_ENTREGA: 'fora da área de entrega',
  CLIENTE_DESISTIU: 'cliente desistiu', NAO_ENTREGUE: 'não foi possível entregar', OUTRO: 'outro motivo',
};

function insights(c, estado) {
  const v = (codigo) => c.medidas[codigo]?.valor;
  const a = (codigo) => c.medidas[codigo]?.amostra || 0;
  const lista = [];
  const add = (template, severidade, prioridade, texto) => lista.push({ id: `${c.dia}-${template}`, dia: c.dia, template, severidade, prioridade, texto });

  const v1 = v('V1_CRESCIMENTO_FATURAMENTO');
  if (v1 != null && v1 <= -10) add('FATURAMENTO_QUEDA', 'ALERTA', 10, `Seu faturamento caiu ${p100(-v1)} nas últimas 4 semanas em relação às 4 anteriores (${moeda(c.fatJ1)} → ${moeda(c.fatJ)}).`);
  const o1 = v('O1_CANCELAMENTO_RESTAURANTE');
  if (o1 != null && o1 > 5 && a('O1_CANCELAMENTO_RESTAURANTE') >= 30) {
    add('CANCELAMENTO_ALTO', 'ALERTA', 20, `${p100(o1)} dos pedidos das últimas 4 semanas foram cancelados pelo restaurante${c.motivoMaisComum ? `. Motivo mais comum: ${MOTIVO[c.motivoMaisComum]}.` : '.'}`);
  }
  const o2 = v('O2_TEMPO_ACEITE');
  if (o2 != null && o2 > 5 && a('O2_TEMPO_ACEITE') >= 30) add('ACEITE_LENTO', 'ALERTA', 30, `Metade dos pedidos esperou mais de ${Math.round(o2)} min para ser aceita. O ideal é aceitar em até 5 min.`);
  if (c.parados.length) {
    const nomes = c.parados.length <= 3
      ? (c.parados.length === 1 ? c.parados[0] : `${c.parados.slice(0, -1).join(', ')} e ${c.parados[c.parados.length - 1]}`)
      : `${c.parados.slice(0, 3).join(', ')} e mais ${c.parados.length - 3}`;
    const sujeito = c.parados.length === 1 ? '1 produto não vendeu' : `${c.parados.length} produtos não venderam`;
    add('PRODUTOS_PARADOS', 'OPORTUNIDADE', 40, `${sujeito} nenhuma unidade nas últimas 4 semanas: ${nomes}. Vale revisar o preço, a descrição ou tirar do cardápio.`);
  }
  const total = c.porHora.reduce((s, x) => s + x, 0);
  if (total >= 30) {
    let melhor = 0; let maior = -1;
    for (let h = 0; h < 24; h++) {
      const bloco = c.porHora[h] + c.porHora[(h + 1) % 24];
      if (bloco > maior || (bloco === maior && c.porHora[h] > c.porHora[melhor])) { maior = bloco; melhor = h; }
    }
    const parte = (maior * 100) / total;
    if (parte >= 25) add('HORARIO_PICO', 'OPORTUNIDADE', 50, `${p100(parte)} dos seus pedidos chegam entre ${melhor}h e ${(melhor + 2) % 24}h. Garanta equipe e estoque nesse horário.`);
  }
  if (v1 != null && v1 >= 10) add('FATURAMENTO_ALTA', 'CONQUISTA', 60, `Seu faturamento cresceu ${p100(v1)} nas últimas 4 semanas em relação às 4 anteriores (${moeda(c.fatJ1)} → ${moeda(c.fatJ)}).`);
  const emAlta = [...c.vendidosJ.entries()].filter(([id, q]) => q >= 10 && (c.vendidosJ1.get(id) || 0) > 0)
    .map(([id, q]) => [id, (q * 100) / c.vendidosJ1.get(id) - 100]).filter(([, g]) => g >= 30).sort((x, y) => y[1] - x[1])[0];
  if (emAlta) {
    const nome = estado.produtos.find((p) => p.id === emAlta[0])?.nome || 'Um produto';
    add('PRODUTO_EM_ALTA', 'CONQUISTA', 70, `${nome} vendeu ${p100(emAlta[1])} a mais que nas 4 semanas anteriores (${c.vendidosJ1.get(emAlta[0])} → ${c.vendidosJ.get(emAlta[0])} unidades).`);
  }
  const c1 = v('C1_RECOMPRA');
  if (c1 != null && c1 >= 40 && a('C1_RECOMPRA') >= 30) add('RECOMPRA_BOA', 'CONQUISTA', 80, `${p100(c1)} dos clientes das últimas 4 semanas já tinham comprado antes: sua clientela está voltando.`);

  const ordem = { ALERTA: 0, OPORTUNIDADE: 1, CONQUISTA: 2 };
  return lista.sort((x, y) => ordem[x.severidade] - ordem[y.severidade] || x.prioridade - y.prioridade).slice(0, 5);
}

// ---------- resposta (mesmo formato do NexusResponse) ----------

export function gerarNexus(estado, agoraMs) {
  const r = estado.restaurante;
  const dia = somarDias(diaOperacional(agoraMs, r.fusoHorario, r.horaViradaDia), -1);
  const coleta = coletar(estado, dia);
  const res = calcular(coleta);

  const historico = [];
  const primeiro = estado.pedidos.reduce((m, p) => (!m || p.diaOperacional < m ? p.diaOperacional : m), null);
  if (primeiro) {
    let d = somarDias(primeiro, regra.historico.diasMinimosNota - 1);
    if (d < somarDias(dia, -89)) d = somarDias(dia, -89);
    for (; d < dia; d = somarDias(d, 1)) {
      const x = calcular(coletar(estado, d));
      historico.push({ dia: d, nota: x.nota, situacao: x.situacao });
    }
  }
  historico.push({ dia, nota: res.nota, situacao: res.situacao });
  const seteDiasAntes = historico.find((x) => x.dia === somarDias(dia, -7));
  const h = regra.historico;

  return {
    dia,
    inicioJanela: coleta.inicioJ,
    regraVersao: regra.versao,
    situacao: res.situacao,
    nota: res.nota,
    faixa: res.faixa?.codigo || null,
    faixaNome: res.faixa?.nome || null,
    motivoSemNota: res.motivoSemNota,
    pesoValido: res.pesoValido,
    diasHistorico: coleta.diasHistorico,
    diasParaNota: Math.max(0, h.diasMinimosNota - coleta.diasHistorico),
    diasParaOficial: Math.max(0, h.diasNotaOficial - coleta.diasHistorico),
    pedidosConcluidosJanela: coleta.pedidosConcluidosJanela,
    pedidosConcluidosMinimos: h.pedidosConcluidosMinimos,
    variacao7Dias: res.nota != null && seteDiasAntes?.nota != null ? res.nota - seteDiasAntes.nota : null,
    faixas: regra.faixas.map(({ codigo, nome, de, ate }) => ({ codigo, nome, de, ate })),
    areas: res.areas.map((a) => ({
      codigo: a.area.codigo,
      nome: a.area.nome,
      peso: a.area.peso,
      nota: a.nota,
      exibida: a.nota != null,
      indicadores: a.indicadores.map(({ ind, m, motivo, pts }) => ({
        codigo: ind.codigo, nome: ind.nome, descricao: ind.descricao, unidade: ind.unidade,
        valor: m?.valor == null ? null : Math.round(m.valor * 100) / 100,
        pontos: pts, pesoNaArea: ind.peso,
        pesoEfetivo: a.nota != null && pts != null ? (a.area.peso / res.pesoAreas) * (ind.peso / a.pesoValido) : 0,
        status: pts == null ? 'SEM_DADOS' : 'OK', motivo, amostra: m?.amostra || 0, ancoras: ind.ancoras,
      })),
    })),
    historico,
    insights: insights(coleta, estado),
  };
}
