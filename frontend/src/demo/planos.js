/**
 * Espelho de PlanoSaas + Recurso (backend) para a demonstração. Mesmos limites, mesmos
 * recursos por plano, mesmas mensagens.
 */

export const RECURSOS = {
  CARDAPIO: { plano: 'BASICO', nome: 'Cardápio digital' },
  PEDIDOS: { plano: 'BASICO', nome: 'Painel de pedidos' },
  CLIENTES: { plano: 'BASICO', nome: 'Clientes' },
  RELATORIOS: { plano: 'BASICO', nome: 'Relatórios de vendas' },
  EQUIPE: { plano: 'BASICO', nome: 'Usuários da equipe' },
  NEXUS_SCORE: { plano: 'PROFISSIONAL', nome: 'Nexus Score' },
  NEXUS_DETALHES: { plano: 'PREMIUM', nome: 'Indicadores detalhados e dicas do Nexus' },
};

export const ORDEM = ['BASICO', 'PROFISSIONAL', 'PREMIUM'];
export const NOME = { BASICO: 'Básico', PROFISSIONAL: 'Profissional', PREMIUM: 'Premium' };

const DADOS = {
  BASICO: { precoMensal: 69.9, descricao: 'Cardápio digital, painel de pedidos e relatórios dos últimos 30 dias', limiteUsuarios: 2, historicoDias: 30, mesesCheios: 0 },
  PROFISSIONAL: { precoMensal: 129.9, descricao: 'Relatórios de 12 meses e Nexus Score', limiteUsuarios: 5, historicoDias: 365, mesesCheios: 11 },
  PREMIUM: { precoMensal: 199.9, descricao: 'Histórico completo, indicadores detalhados e dicas do Nexus', limiteUsuarios: null, historicoDias: null, mesesCheios: null },
};

export const atende = (plano, minimo) => ORDEM.indexOf(plano) >= ORDEM.indexOf(minimo);
export const liberado = (plano, recurso) => atende(plano, RECURSOS[recurso].plano);
export const dados = (plano) => DADOS[plano];

export function listarPlanos() {
  return ORDEM.map((p) => ({
    plano: p, nome: NOME[p], precoMensal: DADOS[p].precoMensal, descricao: DADOS[p].descricao,
    limiteUsuarios: DADOS[p].limiteUsuarios, historicoDias: DADOS[p].historicoDias,
    recursos: Object.keys(RECURSOS).filter((r) => liberado(p, r)),
  }));
}

const DIA_MS = 86400000;
const paraMs = (iso) => Date.UTC(+iso.slice(0, 4), +iso.slice(5, 7) - 1, +iso.slice(8, 10));
const paraIso = (ms) => new Date(ms).toISOString().slice(0, 10);

function inicioDoMes(iso, mesesAtras) {
  const d = new Date(paraMs(iso));
  return paraIso(Date.UTC(d.getUTCFullYear(), d.getUTCMonth() - mesesAtras, 1));
}

/** PlanoSaas.primeiroDiaDoHistorico. */
export function primeiroDiaDoHistorico(plano, hoje) {
  const d = DADOS[plano];
  if (d.historicoDias == null) return null;
  const porDias = paraIso(paraMs(hoje) - (d.historicoDias - 1) * DIA_MS);
  const porMes = inicioDoMes(hoje, d.mesesCheios);
  return porDias < porMes ? porDias : porMes;
}

export function menorPlanoComHistoricoDesde(dia, hoje) {
  return ORDEM.find((p) => {
    const primeiro = primeiroDiaDoHistorico(p, hoje);
    return primeiro == null || dia >= primeiro;
  });
}

export function menorPlanoParaUsuarios(usuarios) {
  return ORDEM.find((p) => DADOS[p].limiteUsuarios == null || usuarios <= DADOS[p].limiteUsuarios);
}
