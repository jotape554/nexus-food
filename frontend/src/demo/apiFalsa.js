import { criarEstadoInicial, VERSAO_DADOS } from './dadosIniciais';
import { dinheiro, diaOperacional, ErroRegra } from './regras';
import { criarPedido, mudarStatus, paraAcompanhamento, paraPainel, proximoId, USUARIO_DEMO } from './servico';
import { gerarRelatorio } from './relatorio';
import { gerarNexus } from './nexus';

/**
 * "Backend" da demonstração: atende no navegador as mesmas rotas que o frontend chama, com os
 * mesmos formatos de resposta e mensagens de erro. O estado fica no localStorage (quando o
 * navegador deixa) para sobreviver a um recarregar de página.
 */

const CHAVE_ARMAZENAMENTO = 'nexusfood_demo';
// Horários dos pedidos de exemplo são relativos ao momento em que a demo foi criada; depois de
// algumas horas eles ficariam todos "atrasados", então a demo recomeça sozinha.
const VALIDADE_MS = 6 * 60 * 60 * 1000;
const LATENCIA_MS = 120;

const PLANOS = [
  { plano: 'BASICO', precoMensal: 69.9, descricao: 'Cardápio digital, painel de pedidos e relatório do dia' },
  { plano: 'PROFISSIONAL', precoMensal: 129.9, descricao: 'Relatórios de 12 meses e Nexus Score' },
  { plano: 'PREMIUM', precoMensal: 199.9, descricao: 'Histórico completo, indicadores detalhados e insights' },
];

const SESSAO = { token: 'demo', nome: USUARIO_DEMO, papel: 'ADMINISTRADOR', restauranteId: 1, restauranteSlug: 'cantina-da-nona' };

// ---------- estado ----------

function carregar() {
  try {
    const salvo = JSON.parse(localStorage.getItem(CHAVE_ARMAZENAMENTO));
    if (salvo && salvo.versao === VERSAO_DADOS && Date.now() - salvo.geradoEm < VALIDADE_MS) return salvo;
  } catch { /* sem armazenamento: começa do zero */ }
  return criarEstadoInicial(Date.now());
}

let estado = carregar();
const ouvintes = new Set();

function avisar() {
  ouvintes.forEach((fn) => fn());
}

function salvar() {
  try { localStorage.setItem(CHAVE_ARMAZENAMENTO, JSON.stringify(estado)); } catch { /* segue só em memória */ }
  avisar();
}

/** Chamado sempre que algo muda (ex.: pedido novo) — o painel usa para atualizar na hora. */
export function aoMudar(fn) {
  ouvintes.add(fn);
  return () => ouvintes.delete(fn);
}

export function reiniciarDemonstracao() {
  estado = criarEstadoInicial(Date.now());
  salvar();
}

// Outra aba com a demo aberta mudou os dados.
try {
  window.addEventListener('storage', (e) => {
    if (e.key !== CHAVE_ARMAZENAMENTO || !e.newValue) return;
    try { estado = JSON.parse(e.newValue); avisar(); } catch { /* ignora */ }
  });
} catch { /* ambiente sem window */ }

// ---------- erros no mesmo formato do GlobalExceptionHandler ----------

function erro(status, mensagem, extra = {}) {
  const e = new Error(mensagem);
  e.status = status;
  e.dados = { status, mensagem, ...extra };
  return e;
}
const naoEncontrado = (o) => erro(404, `${o} não encontrado`);
const naoEncontrada = (o) => erro(404, `${o} não encontrada`);
const invalido = (mensagem) => erro(400, mensagem);

function exigir(condicao, mensagem) {
  if (!condicao) throw invalido(mensagem);
}

// ---------- helpers ----------

const agora = () => Date.now();
const r = () => estado.restaurante;

function configuracao() {
  const x = r();
  return {
    id: x.id, nome: x.nome, slug: x.slug, telefone: x.telefone, endereco: x.endereco, logoUrl: x.logoUrl,
    fusoHorario: x.fusoHorario, horaViradaDia: x.horaViradaDia, aceitandoPedidos: x.aceitandoPedidos,
    aceitaRetirada: x.aceitaRetirada, aceitaEntrega: x.aceitaEntrega, aceitaConsumoLocal: x.aceitaConsumoLocal,
    tipoTaxaEntrega: x.tipoTaxaEntrega, taxaEntregaFixa: x.taxaEntregaFixa, pedidoMinimo: x.pedidoMinimo,
    tempoPreparoEstimadoMin: x.tempoPreparoEstimadoMin,
  };
}

function produtoResposta(p) {
  return { id: p.id, categoriaId: p.categoriaId, nome: p.nome, descricao: p.descricao, preco: p.preco, imagemUrl: p.imagemUrl, disponivel: p.disponivel, ordem: p.ordem };
}

const porOrdemENome = (a, b) => (a.ordem - b.ordem) || a.nome.localeCompare(b.nome, 'pt-BR');

function buscar(lista, id, rotulo, feminino = false) {
  const item = lista.find((x) => x.id === Number(id));
  if (!item) throw (feminino ? naoEncontrada(rotulo) : naoEncontrado(rotulo));
  return item;
}

function texto(v) {
  return v == null || String(v).trim() === '' ? null : String(v).trim();
}

function validarNumero(valor, minimo, mensagem) {
  const n = Number(valor);
  exigir(valor !== null && valor !== '' && !Number.isNaN(n) && n >= minimo, mensagem);
  return n;
}

function dadosProduto(body) {
  exigir(body.categoriaId, 'Escolha a categoria do produto.');
  exigir(texto(body.nome), 'Informe o nome do produto.');
  const preco = validarNumero(body.preco, 0.01, 'O preço deve ser maior que zero.');
  buscar(estado.categorias, body.categoriaId, 'Categoria', true);
  return {
    categoriaId: Number(body.categoriaId), nome: texto(body.nome), descricao: texto(body.descricao), preco: dinheiro(preco),
    imagemUrl: texto(body.imagemUrl), disponivel: body.disponivel !== false,
  };
}

function dadosBairro(body) {
  exigir(texto(body.nome), 'Informe o nome do bairro.');
  const taxa = validarNumero(body.taxa, 0, 'A taxa de entrega não pode ser negativa.');
  return { nome: texto(body.nome), taxa: dinheiro(taxa), ativo: body.ativo !== false };
}

const ordenadosPorCriacao = (lista) => [...lista].sort((a, b) => a.criadoEm.localeCompare(b.criadoEm));

// ---------- rotas ----------

const ROTAS = [
  // autenticação: na demo, qualquer login entra como a dona da Cantina da Nona
  ['POST', /^\/auth\/(login|registro)$/, () => SESSAO],
  ['POST', /^\/auth\/(esqueci-senha|redefinir-senha)$/, () => null],

  // assinatura
  ['GET', /^\/api\/assinatura$/, () => {
    const fim = new Date(estado.geradoEm + 14 * 86400000);
    return {
      plano: 'BASICO', precoMensal: 69.9, descricaoPlano: PLANOS[0].descricao, status: 'TRIAL',
      dataFimTrial: fim.toISOString().slice(0, 10), diasRestantesTrial: 14, acessoLiberado: true,
    };
  }],
  ['GET', /^\/api\/assinatura\/planos$/, () => PLANOS],
  ['POST', /^\/api\/assinatura\/(checkout|portal)/, () => {
    throw invalido('Na demonstração não dá para assinar. No sistema real, este botão abre o pagamento seguro da Stripe.');
  }],

  // configuração do restaurante
  ['GET', /^\/api\/restaurante$/, () => configuracao()],
  ['PUT', /^\/api\/restaurante$/, (_m, _q, b) => {
    exigir(texto(b.nome), 'Informe o nome do restaurante.');
    exigir(b.aceitaRetirada || b.aceitaEntrega || b.aceitaConsumoLocal,
      'Escolha pelo menos uma forma de atendimento: retirada, entrega ou consumo no local.');
    exigir(b.horaViradaDia, 'Informe o horário de virada do dia.');
    const minutos = validarNumero(b.tempoPreparoEstimadoMin, 1, 'O tempo de preparo deve ser de pelo menos 1 minuto.');
    exigir(minutos <= 300, 'O tempo de preparo deve ser de no máximo 300 minutos.');
    Object.assign(estado.restaurante, {
      nome: texto(b.nome), telefone: texto(b.telefone), endereco: texto(b.endereco), logoUrl: texto(b.logoUrl),
      fusoHorario: b.fusoHorario, horaViradaDia: String(b.horaViradaDia).length === 5 ? `${b.horaViradaDia}:00` : b.horaViradaDia,
      aceitaRetirada: !!b.aceitaRetirada, aceitaEntrega: !!b.aceitaEntrega, aceitaConsumoLocal: !!b.aceitaConsumoLocal,
      tipoTaxaEntrega: b.tipoTaxaEntrega,
      taxaEntregaFixa: dinheiro(validarNumero(b.taxaEntregaFixa, 0, 'A taxa de entrega não pode ser negativa.')),
      pedidoMinimo: dinheiro(validarNumero(b.pedidoMinimo, 0, 'O pedido mínimo não pode ser negativo.')),
      tempoPreparoEstimadoMin: Math.round(minutos),
    });
    salvar();
    return configuracao();
  }],
  ['PATCH', /^\/api\/restaurante\/aceitando-pedidos$/, (_m, q) => {
    estado.restaurante.aceitandoPedidos = q.get('valor') === 'true';
    salvar();
    return configuracao();
  }],
  ['GET', /^\/api\/restaurante\/bairros$/, () => [...estado.bairros].sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR'))],
  ['POST', /^\/api\/restaurante\/bairros$/, (_m, _q, b) => {
    const bairro = { id: proximoId(estado, 'bairro'), ...dadosBairro(b) };
    estado.bairros.push(bairro);
    salvar();
    return bairro;
  }],
  ['PUT', /^\/api\/restaurante\/bairros\/(\d+)$/, (m, _q, b) => {
    const bairro = buscar(estado.bairros, m[1], 'Bairro');
    Object.assign(bairro, dadosBairro(b));
    salvar();
    return bairro;
  }],
  ['DELETE', /^\/api\/restaurante\/bairros\/(\d+)$/, (m) => {
    buscar(estado.bairros, m[1], 'Bairro');
    estado.bairros = estado.bairros.filter((x) => x.id !== Number(m[1]));
    salvar();
    return null;
  }],

  // cardápio
  ['GET', /^\/api\/categorias$/, () => [...estado.categorias].sort(porOrdemENome)],
  ['POST', /^\/api\/categorias$/, (_m, _q, b) => {
    exigir(texto(b.nome), 'Informe o nome da categoria.');
    const categoria = { id: proximoId(estado, 'categoria'), nome: texto(b.nome), ordem: Number(b.ordem) || 0, ativa: b.ativa !== false };
    estado.categorias.push(categoria);
    salvar();
    return categoria;
  }],
  ['PUT', /^\/api\/categorias\/(\d+)$/, (m, _q, b) => {
    const categoria = buscar(estado.categorias, m[1], 'Categoria', true);
    exigir(texto(b.nome), 'Informe o nome da categoria.');
    Object.assign(categoria, { nome: texto(b.nome), ordem: b.ordem != null ? Number(b.ordem) || 0 : categoria.ordem, ativa: b.ativa !== false });
    salvar();
    return categoria;
  }],
  ['DELETE', /^\/api\/categorias\/(\d+)$/, (m) => {
    const categoria = buscar(estado.categorias, m[1], 'Categoria', true);
    if (estado.produtos.some((p) => p.categoriaId === categoria.id && p.ativo)) {
      throw invalido('Esta categoria ainda tem produtos. Mova ou remova os produtos antes, ou apenas desative a categoria.');
    }
    categoria.ativa = false;
    salvar();
    return null;
  }],
  ['GET', /^\/api\/produtos$/, () => estado.produtos.filter((p) => p.ativo).sort(porOrdemENome).map(produtoResposta)],
  ['POST', /^\/api\/produtos$/, (_m, _q, b) => {
    const produto = { id: proximoId(estado, 'produto'), ...dadosProduto(b), ativo: true, ordem: Number(b.ordem) || 0, criadoEm: new Date().toISOString() };
    estado.produtos.push(produto);
    salvar();
    return produtoResposta(produto);
  }],
  ['PUT', /^\/api\/produtos\/(\d+)$/, (m, _q, b) => {
    const produto = buscar(estado.produtos.filter((p) => p.ativo), m[1], 'Produto');
    Object.assign(produto, dadosProduto(b), b.ordem != null ? { ordem: Number(b.ordem) || 0 } : {});
    salvar();
    return produtoResposta(produto);
  }],
  ['PATCH', /^\/api\/produtos\/(\d+)\/disponivel$/, (m, q) => {
    const produto = buscar(estado.produtos.filter((p) => p.ativo), m[1], 'Produto');
    produto.disponivel = q.get('valor') === 'true';
    salvar();
    return produtoResposta(produto);
  }],
  ['DELETE', /^\/api\/produtos\/(\d+)$/, (m) => {
    buscar(estado.produtos.filter((p) => p.ativo), m[1], 'Produto').ativo = false;
    salvar();
    return null;
  }],

  // clientes
  ['GET', /^\/api\/clientes$/, (_m, q) => {
    const pagina = Math.max(0, Number(q.get('pagina')) || 0);
    const tamanho = Math.max(1, Math.min(100, Number(q.get('tamanho')) || 20));
    let termo = texto(q.get('busca'));
    let lista = [...estado.clientes].sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR'));
    if (termo) {
      const digitos = termo.replace(/\D/g, '');
      if (digitos && digitos.length === termo.replace(/[\s()+-]/g, '').length) termo = digitos;
      const t = termo.toLowerCase();
      lista = lista.filter((c) => c.nome.toLowerCase().includes(t) || c.telefone.includes(termo));
    }
    return {
      content: lista.slice(pagina * tamanho, (pagina + 1) * tamanho),
      totalElements: lista.length,
      totalPages: Math.ceil(lista.length / tamanho),
      number: pagina,
      size: tamanho,
    };
  }],

  // painel de pedidos
  ['GET', /^\/api\/pedidos\/em-andamento$/, () => ordenadosPorCriacao(estado.pedidos.filter((p) => !['CONCLUIDO', 'CANCELADO'].includes(p.status)))
    .map((p) => paraPainel(estado, p, false))],
  ['GET', /^\/api\/pedidos$/, (_m, q) => {
    const dia = q.get('dia') || diaOperacional(agora(), r().fusoHorario, r().horaViradaDia);
    return estado.pedidos.filter((p) => p.diaOperacional === dia).sort((a, b) => b.numeroDia - a.numeroDia)
      .map((p) => paraPainel(estado, p, false));
  }],
  ['GET', /^\/api\/pedidos\/(\d+)$/, (m) => paraPainel(estado, buscar(estado.pedidos, m[1], 'Pedido'), true)],
  ['PATCH', /^\/api\/pedidos\/(\d+)\/status$/, (m, _q, b) => {
    const pedido = buscar(estado.pedidos, m[1], 'Pedido');
    exigir(b?.status, 'Informe o novo status.');
    mudarStatus(pedido, b.status, agora(), b.motivoCancelamento);
    salvar();
    return paraPainel(estado, pedido, true);
  }],

  // relatórios
  ['GET', /^\/api\/relatorios\/vendas$/, (_m, q) => {
    try {
      return gerarRelatorio(estado, q, agora());
    } catch (e) {
      if (e.regra) throw invalido(e.message);
      throw e;
    }
  }],

  // Nexus Score
  ['GET', /^\/api\/nexus$/, () => gerarNexus(estado, agora())],

  // páginas do cliente final
  ['GET', /^\/public\/restaurantes\/([^/]+)$/, (m) => {
    if (m[1] !== r().slug) throw naoEncontrado('Restaurante');
    const x = r();
    return {
      nome: x.nome, slug: x.slug, telefone: x.telefone, endereco: x.endereco, logoUrl: x.logoUrl,
      aceitandoPedidos: x.aceitandoPedidos, aceitaRetirada: x.aceitaRetirada, aceitaEntrega: x.aceitaEntrega,
      aceitaConsumoLocal: x.aceitaConsumoLocal, tipoTaxaEntrega: x.tipoTaxaEntrega, taxaEntregaFixa: x.taxaEntregaFixa,
      pedidoMinimo: x.pedidoMinimo, tempoPreparoEstimadoMin: x.tempoPreparoEstimadoMin,
      bairros: x.tipoTaxaEntrega === 'POR_BAIRRO'
        ? estado.bairros.filter((b) => b.ativo).sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR')).map(({ id, nome, taxa }) => ({ id, nome, taxa }))
        : [],
    };
  }],
  ['GET', /^\/public\/restaurantes\/([^/]+)\/cardapio$/, (m) => {
    if (m[1] !== r().slug) throw naoEncontrado('Restaurante');
    const produtos = estado.produtos.filter((p) => p.ativo).sort(porOrdemENome);
    return {
      categorias: estado.categorias.filter((c) => c.ativa).sort(porOrdemENome)
        .map((c) => ({ id: c.id, nome: c.nome, produtos: produtos.filter((p) => p.categoriaId === c.id).map(produtoResposta) }))
        .filter((c) => c.produtos.length > 0),
    };
  }],
  ['POST', /^\/public\/restaurantes\/([^/]+)\/pedidos$/, (m, _q, b) => {
    if (m[1] !== r().slug) throw naoEncontrado('Restaurante');
    exigir(texto(b.chaveIdempotencia), 'Pedido sem identificador. Recarregue a página e tente de novo.');
    exigir(texto(b.nomeCliente), 'Informe seu nome.');
    exigir(texto(b.telefoneCliente), 'Informe seu telefone.');
    exigir(b.modalidade, 'Escolha retirada, entrega ou consumo no local.');
    exigir(b.formaPagamento, 'Escolha a forma de pagamento.');
    const pedido = criarPedido(estado, { ...b, bairroId: b.bairroId != null ? Number(b.bairroId) : null }, agora());
    salvar();
    return paraAcompanhamento(estado, pedido);
  }],
  ['GET', /^\/public\/pedidos\/([^/]+)$/, (m) => {
    const pedido = estado.pedidos.find((p) => p.codigoPublico === m[1]);
    if (!pedido) throw naoEncontrado('Pedido');
    return paraAcompanhamento(estado, pedido);
  }],
];

/** Substitui o fetch do api/http.js no modo demonstração. */
export async function responder(caminho, { method = 'GET', body } = {}) {
  await new Promise((ok) => setTimeout(ok, LATENCIA_MS));
  const url = new URL(caminho, 'https://demo.nexusfood.local');
  for (const [metodo, padrao, tratar] of ROTAS) {
    if (metodo !== method) continue;
    const m = url.pathname.match(padrao);
    if (!m) continue;
    try {
      const resultado = tratar(m, url.searchParams, body || {});
      return resultado == null ? null : JSON.parse(JSON.stringify(resultado));
    } catch (e) {
      if (e instanceof ErroRegra) throw invalido(e.message);
      throw e;
    }
  }
  throw erro(404, 'Esta função não faz parte da demonstração.');
}
