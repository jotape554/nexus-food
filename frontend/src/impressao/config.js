/**
 * Configuração de impressão DESTE aparelho (a impressora é do computador ou tablet do balcão,
 * não da conta). Fica no navegador; se ele não deixar guardar, vale o padrão.
 */

const CHAVE = 'nexusfood_impressora';
const CHAVE_IMPRESSOS = 'nexusfood_impressos';
const MAX_IMPRESSOS = 300;

export const PADRAO = {
  papel: '80',          // '80' | '58' | 'A4'
  vias: 'ambas',        // 'cozinha' | 'entrega' | 'ambas'
  automatico: 'aceitar', // 'nao' | 'aceitar' | 'chegar'
};

export function lerConfig() {
  try {
    return { ...PADRAO, ...JSON.parse(localStorage.getItem(CHAVE) || '{}') };
  } catch {
    return { ...PADRAO };
  }
}

export function gravarConfig(config) {
  try { localStorage.setItem(CHAVE, JSON.stringify(config)); } catch { /* segue só nesta sessão */ }
}

/** Pedidos que já saíram na impressão automática (para não imprimir duas vezes). */
function lerImpressos() {
  try { return JSON.parse(localStorage.getItem(CHAVE_IMPRESSOS) || '[]'); } catch { return []; }
}

export function jaImpresso(id) {
  return lerImpressos().includes(id);
}

export function marcarImpresso(id) {
  const lista = lerImpressos().filter((x) => x !== id);
  lista.push(id);
  try { localStorage.setItem(CHAVE_IMPRESSOS, JSON.stringify(lista.slice(-MAX_IMPRESSOS))); } catch { /* ignora */ }
}
