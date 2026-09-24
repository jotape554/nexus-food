/**
 * Adicionais e variações no navegador: mesmas regras do backend (PedidoService.opcoesEscolhidas
 * e CobrancaGrupo), só para mostrar preço e avisos na hora. Quem decide é sempre o servidor.
 */

const centavos = (v) => Math.round(Number(v) * 100);

/** CobrancaGrupo.valor: SOMA, MAIOR (meio a meio pelo mais caro) ou MEDIA. Em centavos. */
function valorDoGrupo(cobranca, precos) {
  if (precos.length === 0) return 0;
  if (cobranca === 'MAIOR') return Math.max(...precos);
  if (cobranca === 'MEDIA') return Math.round(precos.reduce((a, b) => a + b, 0) / precos.length);
  return precos.reduce((a, b) => a + b, 0);
}

export function opcoesPorId(produto) {
  const mapa = {};
  (produto.grupos || []).forEach((g) => g.opcoes.forEach((o) => { mapa[o.id] = { ...o, grupoId: g.id, grupo: g.nome }; }));
  return mapa;
}

/** Preço unitário com as opções escolhidas (em reais). */
export function precoComOpcoes(produto, ids = []) {
  const mapa = opcoesPorId(produto);
  let total = centavos(produto.preco);
  (produto.grupos || []).forEach((g) => {
    const precos = ids.map((id) => mapa[id]).filter((o) => o && o.grupoId === g.id).map((o) => centavos(o.preco));
    total += valorDoGrupo(g.cobranca, precos);
  });
  return total / 100;
}

/** O que falta ou sobra na escolha (mesmas frases do servidor), ou null se está tudo certo. */
export function problemaDaEscolha(produto, ids = []) {
  const mapa = opcoesPorId(produto);
  if (ids.some((id) => !mapa[id])) return `As opções de "${produto.nome}" mudaram. Escolha de novo.`;
  const esgotada = ids.map((id) => mapa[id]).find((o) => !o.disponivel);
  if (esgotada) return `"${esgotada.nome}" esgotou. Escolha outra opção.`;
  for (const g of produto.grupos || []) {
    const n = ids.filter((id) => mapa[id].grupoId === g.id).length;
    if (n < g.minimo) return g.minimo === 1 && g.maximo === 1 ? `Escolha ${g.nome.toLowerCase()}.` : `Escolha pelo menos ${g.minimo} em ${g.nome}.`;
    if (n > g.maximo) return `Escolha no máximo ${g.maximo} em ${g.nome}.`;
  }
  return null;
}

/** "Escolha 1", "Até 3", "Escolha 2", "De 1 a 3". */
export function regraDoGrupo(g) {
  if (g.minimo === g.maximo) return `Escolha ${g.minimo}`;
  if (g.minimo === 0) return g.maximo === 1 ? 'Opcional' : `Até ${g.maximo}`;
  return `De ${g.minimo} a ${g.maximo}`;
}

export const COBRANCA_TEXTO = {
  SOMA: 'soma o preço de cada escolha',
  MAIOR: 'cobra o preço da mais cara',
  MEDIA: 'cobra a média dos preços',
};

/** Tem alguma opção que muda o preço? (para mostrar "a partir de"). */
export function temPrecoVariavel(produto) {
  return (produto.grupos || []).some((g) => g.opcoes.some((o) => Number(o.preco) > 0));
}

/**
 * Opções de um item agrupadas para exibir: [{ grupo: 'Borda', nomes: ['Catupiry'] }, ...].
 * Aceita a lista do pedido ({ grupo, nome }) ou ids + produto (sacola do cliente).
 */
export function agruparOpcoes(lista = []) {
  const grupos = [];
  lista.forEach(({ grupo, nome }) => {
    const existente = grupos.find((g) => g.grupo === grupo);
    if (existente) existente.nomes.push(nome);
    else grupos.push({ grupo, nomes: [nome] });
  });
  return grupos;
}

export function opcoesDaSacola(produto, ids = []) {
  const mapa = opcoesPorId(produto);
  return ids.map((id) => mapa[id]).filter(Boolean).map((o) => ({ grupo: o.grupo, nome: o.nome }));
}
