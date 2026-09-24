import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { api } from '../../api/http';
import { moeda, MODALIDADE_LABEL, PAGAMENTO_LABEL } from '../../api/formato';
import Icone, { ICONE_MODALIDADE, ICONE_PAGAMENTO } from '../../components/Icone';
import AssinaturaNexus from '../../components/AssinaturaNexus';
import OpcoesDoItem from '../../components/OpcoesDoItem';
import { opcoesDaSacola, precoComOpcoes, problemaDaEscolha, temPrecoVariavel } from '../../api/opcoes';
import EscolhaOpcoes from './EscolhaOpcoes';
import '../../styles/public.css';

const ETAPAS_CHECKOUT = [
  { id: 'sacola', titulo: 'Sacola' },
  { id: 'entrega', titulo: 'Entrega' },
  { id: 'pagamento', titulo: 'Pagamento' },
];

function lerLocal(chave, padrao) {
  try {
    const valor = localStorage.getItem(chave);
    return valor ? JSON.parse(valor) : padrao;
  } catch {
    return padrao;
  }
}

function gravarLocal(chave, valor) {
  try { localStorage.setItem(chave, JSON.stringify(valor)); } catch { /* navegação privada: segue sem salvar */ }
}

/**
 * Volta ao topo do que está rolando: a janela no celular do cliente, ou a tela do celular
 * quando o cardápio aparece dentro de uma moldura (demonstração) — sem arrastar a página junto.
 */
function rolarParaTopo(elemento) {
  for (let no = elemento?.parentElement; no; no = no.parentElement) {
    const rolagem = getComputedStyle(no).overflowY;
    if ((rolagem === 'auto' || rolagem === 'scroll') && no.scrollHeight > no.clientHeight) {
      no.scrollTop = 0;
      return;
    }
  }
  window.scrollTo({ top: 0 });
}

function novaChave() {
  return window.crypto?.randomUUID?.() || `${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

const mesmasOpcoes = (a = [], b = []) => a.length === b.length && [...a].sort().join() === [...b].sort().join();

function Produto({ produto, quantidade, onAdicionar }) {
  const temOpcoes = produto.grupos?.length > 0;
  return (
    <div className={`produto-publico ${produto.disponivel ? '' : 'esgotado'}`}>
      <div className="produto-publico-texto">
        <strong>{produto.nome}</strong>
        {produto.descricao && <p>{produto.descricao}</p>}
        <span className="preco">
          {temOpcoes && temPrecoVariavel(produto) && <small>a partir de </small>}
          {moeda(produto.precoMinimo ?? produto.preco)}
        </span>
      </div>
      {produto.imagemUrl && <img src={produto.imagemUrl} alt="" loading="lazy" />}
      {produto.disponivel ? (
        <button className="btn-adicionar" onClick={() => onAdicionar(produto)} aria-label={`Adicionar ${produto.nome}`}>
          {quantidade > 0 ? quantidade : '+'}
        </button>
      ) : <span className="selo-esgotado">Esgotado</span>}
    </div>
  );
}

export default function CardapioPublico() {
  const { slug } = useParams();
  const navigate = useNavigate();
  const chaveCarrinho = `nexusfood_carrinho_${slug}`;

  const restauranteQuery = useQuery({ queryKey: ['pub-restaurante', slug], queryFn: () => api.publica.get(`/public/restaurantes/${slug}`) });
  const cardapioQuery = useQuery({ queryKey: ['pub-cardapio', slug], queryFn: () => api.publica.get(`/public/restaurantes/${slug}/cardapio`) });
  const restaurante = restauranteQuery.data;
  const categorias = cardapioQuery.data?.categorias || [];

  const [carrinho, setCarrinho] = useState(() => lerLocal(chaveCarrinho, []));
  const [etapa, setEtapa] = useState('cardapio'); // cardapio | sacola | entrega | pagamento
  const [abertaObs, setAbertaObs] = useState(null);
  const [escolhendo, setEscolhendo] = useState(null); // produto com opções aberto na folha
  const [dados, setDados] = useState(() => ({
    nomeCliente: '', telefoneCliente: '', ...lerLocal('nexusfood_cliente', {}),
    modalidade: '', formaPagamento: 'PIX', precisaTroco: false, trocoPara: '', enderecoEntrega: '', bairroId: '', observacao: '',
  }));
  const [erro, setErro] = useState('');
  const [enviando, setEnviando] = useState(false);
  const chaveIdempotencia = useRef(novaChave());
  const topoRef = useRef(null);

  useEffect(() => gravarLocal(chaveCarrinho, carrinho), [carrinho]);

  const modalidades = useMemo(() => {
    if (!restaurante) return [];
    return [
      restaurante.aceitaRetirada && 'RETIRADA',
      restaurante.aceitaEntrega && 'ENTREGA',
      restaurante.aceitaConsumoLocal && 'CONSUMO_LOCAL',
    ].filter(Boolean);
  }, [restaurante]);

  useEffect(() => {
    if (modalidades.length && !modalidades.includes(dados.modalidade)) {
      setDados((d) => ({ ...d, modalidade: modalidades[0] }));
    }
  }, [modalidades]);

  // Mantém o carrinho coerente com o cardápio atual (preço novo, produto removido ou esgotado).
  const produtosPorId = useMemo(() => {
    const mapa = {};
    categorias.forEach((c) => c.produtos.forEach((p) => { mapa[p.id] = p; }));
    return mapa;
  }, [categorias]);

  // Item vale se o produto segue disponível e as opções escolhidas ainda batem com o cardápio.
  const itemValido = (item) => {
    const produto = produtosPorId[item.produtoId];
    return produto && produto.disponivel && !problemaDaEscolha(produto, item.opcoes || []);
  };
  const itens = carrinho
    .filter(itemValido)
    .map((item) => ({ ...item, produto: produtosPorId[item.produtoId], unitario: precoComOpcoes(produtosPorId[item.produtoId], item.opcoes || []) }));

  const subtotal = itens.reduce((soma, i) => soma + i.unitario * i.quantidade, 0);
  const quantidadeTotal = itens.reduce((soma, i) => soma + i.quantidade, 0);

  let taxaEntrega = 0;
  if (restaurante && dados.modalidade === 'ENTREGA') {
    if (restaurante.tipoTaxaEntrega === 'FIXA') taxaEntrega = Number(restaurante.taxaEntregaFixa);
    else taxaEntrega = Number(restaurante.bairros.find((b) => String(b.id) === String(dados.bairroId))?.taxa || 0);
  }
  const total = subtotal + taxaEntrega;
  const faltaParaMinimo = restaurante ? Number(restaurante.pedidoMinimo) - subtotal : 0;

  /** Produto sem opções entra direto; com opções, abre a folha de escolha. */
  function adicionar(produto) {
    if (produto.grupos?.length > 0) {
      setEscolhendo(produto);
      return;
    }
    colocarNaSacola({ produtoId: produto.id, opcoes: [], quantidade: 1, observacao: '' });
  }

  /** Mesmo produto com as mesmas opções e sem observação soma na mesma linha. */
  function colocarNaSacola(novo) {
    setCarrinho((c) => {
      const existente = !novo.observacao && c.find((i) => i.produtoId === novo.produtoId && !i.observacao && mesmasOpcoes(i.opcoes, novo.opcoes));
      if (existente) return c.map((i) => (i === existente ? { ...i, quantidade: Math.min(99, i.quantidade + novo.quantidade) } : i));
      return [...c, novo];
    });
  }

  function alterarQuantidade(indice, delta) {
    setCarrinho((c) => c
      .map((item, i) => (i === indice ? { ...item, quantidade: Math.min(99, item.quantidade + delta) } : item))
      .filter((item) => item.quantidade > 0));
  }

  function alterarObservacao(indice, observacao) {
    setCarrinho((c) => c.map((item, i) => (i === indice ? { ...item, observacao } : item)));
  }

  const set = (campo) => (e) => { setErro(''); setDados({ ...dados, [campo]: e.target.value }); };

  const indiceEtapa = ETAPAS_CHECKOUT.findIndex((e) => e.id === etapa);

  function descricaoModalidade(m) {
    if (m === 'RETIRADA') return `Buscar no balcão · ~${restaurante.tempoPreparoEstimadoMin} min`;
    if (m === 'CONSUMO_LOCAL') return 'Comer no restaurante';
    if (restaurante.tipoTaxaEntrega === 'POR_BAIRRO') return 'Receba em casa · taxa por bairro';
    return Number(restaurante.taxaEntregaFixa) > 0 ? `Receba em casa · ${moeda(restaurante.taxaEntregaFixa)}` : 'Receba em casa · grátis';
  }

  /** Validação de cada etapa antes de seguir — o servidor valida tudo de novo ao receber o pedido. */
  function problemaDaEtapa() {
    if (etapa === 'sacola') {
      if (itens.length === 0) return 'Sua sacola está vazia.';
      if (faltaParaMinimo > 0) return `Faltam ${moeda(faltaParaMinimo)} para o pedido mínimo.`;
    }
    if (etapa === 'entrega') {
      if (!dados.modalidade) return 'Escolha como você quer receber.';
      if (dados.modalidade === 'ENTREGA' && restaurante.tipoTaxaEntrega === 'POR_BAIRRO' && !dados.bairroId) return 'Escolha o bairro de entrega.';
      if (dados.modalidade === 'ENTREGA' && !dados.enderecoEntrega.trim()) return 'Informe o endereço de entrega.';
      if (!dados.nomeCliente.trim()) return 'Informe seu nome.';
      if (dados.telefoneCliente.replace(/\D/g, '').length < 10) return 'Informe seu telefone com DDD.';
    }
    if (etapa === 'pagamento' && dados.formaPagamento === 'DINHEIRO' && dados.precisaTroco) {
      if (!dados.trocoPara || Number(dados.trocoPara) < total) return `O troco deve ser para um valor maior que ${moeda(total)}.`;
    }
    return null;
  }

  function irPara(novaEtapa) {
    setErro('');
    setEtapa(novaEtapa);
    rolarParaTopo(topoRef.current);
  }

  function avancar() {
    const problema = problemaDaEtapa();
    if (problema) { setErro(problema); return; }
    irPara(ETAPAS_CHECKOUT[indiceEtapa + 1].id);
  }

  function voltar() {
    irPara(indiceEtapa <= 0 ? 'cardapio' : ETAPAS_CHECKOUT[indiceEtapa - 1].id);
  }

  async function enviarPedido() {
    const problema = problemaDaEtapa();
    if (problema) { setErro(problema); return; }
    setErro('');
    setEnviando(true);
    try {
      const pedido = await api.publica.post(`/public/restaurantes/${slug}/pedidos`, {
        chaveIdempotencia: chaveIdempotencia.current,
        nomeCliente: dados.nomeCliente,
        telefoneCliente: dados.telefoneCliente,
        modalidade: dados.modalidade,
        formaPagamento: dados.formaPagamento,
        trocoPara: dados.formaPagamento === 'DINHEIRO' && dados.precisaTroco && dados.trocoPara ? Number(dados.trocoPara) : null,
        enderecoEntrega: dados.modalidade === 'ENTREGA' ? dados.enderecoEntrega : null,
        bairroId: dados.modalidade === 'ENTREGA' && dados.bairroId ? Number(dados.bairroId) : null,
        observacao: dados.observacao,
        itens: itens.map((i) => ({ produtoId: i.produtoId, quantidade: i.quantidade, observacao: i.observacao, opcoes: i.opcoes || [] })),
      });
      gravarLocal('nexusfood_cliente', { nomeCliente: dados.nomeCliente, telefoneCliente: dados.telefoneCliente });
      setCarrinho([]);
      chaveIdempotencia.current = novaChave();
      // O endereço vai só no estado da navegação: a API de acompanhamento nunca devolve dados do cliente.
      navigate(`/pedido/${pedido.codigoPublico}`, {
        state: {
          confirmacao: {
            endereco: dados.modalidade === 'ENTREGA'
              ? [dados.enderecoEntrega, restaurante.bairros.find((b) => String(b.id) === String(dados.bairroId))?.nome].filter(Boolean).join(' · ')
              : null,
          },
        },
      });
    } catch (err) {
      setErro(err.message);
      cardapioQuery.refetch();
      restauranteQuery.refetch();
    } finally {
      setEnviando(false);
    }
  }

  if (restauranteQuery.isError) {
    return <div className="pub-shell"><div className="pub-content" style={{ marginTop: 40 }}><div className="pub-card">Restaurante não encontrado.</div></div></div>;
  }
  if (!restaurante || cardapioQuery.isLoading) {
    return <div style={{ padding: 40, textAlign: 'center' }}>Carregando cardápio...</div>;
  }

  return (
    <div className="pub-shell" ref={topoRef}>
      <header className="pub-header cardapio-header">
        {restaurante.logoUrl && <img className="logo-restaurante" src={restaurante.logoUrl} alt="" />}
        <h1>{restaurante.nome}</h1>
        <p>
          <span className={`status-loja ${restaurante.aceitandoPedidos ? 'aberta' : ''}`}>
            {restaurante.aceitandoPedidos ? 'Aberto agora' : 'Fechado no momento'}
          </span>
          {' · '}~{restaurante.tempoPreparoEstimadoMin} min
          {Number(restaurante.pedidoMinimo) > 0 && <> · pedido mínimo {moeda(restaurante.pedidoMinimo)}</>}
        </p>
      </header>

      {etapa === 'cardapio' ? (
        <main className="pub-content cardapio-conteudo">
          {categorias.length > 1 && (
            <nav className="chips-categorias">
              {categorias.map((c) => <a key={c.id} href={`#cat-${c.id}`}>{c.nome}</a>)}
            </nav>
          )}
          {categorias.length === 0 && <div className="pub-card">O cardápio ainda está sendo montado.</div>}
          {categorias.map((c) => (
            <section key={c.id} id={`cat-${c.id}`} className="pub-card categoria-publica">
              <h2>{c.nome}</h2>
              {c.produtos.map((p) => (
                <Produto
                  key={p.id}
                  produto={p}
                  quantidade={carrinho.filter((i) => i.produtoId === p.id).reduce((s, i) => s + i.quantidade, 0)}
                  onAdicionar={adicionar}
                />
              ))}
            </section>
          ))}
          <footer className="rodape-publico">
            <AssinaturaNexus prefixo="Pedidos online com Nexus Food · um produto" />
          </footer>
          {escolhendo && (
            <EscolhaOpcoes
              produto={escolhendo}
              onFechar={() => setEscolhendo(null)}
              onAdicionar={(novo) => { colocarNaSacola(novo); setEscolhendo(null); }}
            />
          )}
          {quantidadeTotal > 0 && (
            <button className="barra-sacola" onClick={() => irPara('sacola')}>
              <span>Ver sacola ({quantidadeTotal})</span>
              <strong>{moeda(subtotal)}</strong>
            </button>
          )}
        </main>
      ) : (
        <main className="pub-content checkout">
          <div className="pub-card">
            <div className="checkout-topo">
              <button type="button" className="btn-link" onClick={voltar}>← Voltar</button>
              <ol className="checkout-passos" aria-label="Etapas do pedido">
                {ETAPAS_CHECKOUT.map((e, i) => (
                  <li key={e.id} className={i === indiceEtapa ? 'ativo' : i < indiceEtapa ? 'feito' : ''}>{e.titulo}</li>
                ))}
              </ol>
            </div>

            {!restaurante.aceitandoPedidos && <div className="erro">O restaurante está fechado agora e não está aceitando pedidos.</div>}

            {etapa === 'sacola' && (
              <section>
                <h2 className="checkout-titulo">Sua sacola</h2>
                {itens.length === 0 && <p className="ajuda">Sua sacola está vazia.</p>}
                {carrinho.map((item, indice) => {
                  if (!itemValido(item)) return null;
                  const produto = produtosPorId[item.produtoId];
                  return (
                    <div key={indice} className="item-sacola">
                      <div className="item-sacola-linha">
                        <span className="nome">
                          {produto.nome}
                          <OpcoesDoItem opcoes={opcoesDaSacola(produto, item.opcoes || [])} />
                        </span>
                        <span className="valor">{moeda(precoComOpcoes(produto, item.opcoes || []) * item.quantidade)}</span>
                      </div>
                      <div className="item-sacola-linha">
                        <div className="controle-qtd">
                          <button type="button" onClick={() => alterarQuantidade(indice, -1)} aria-label="Diminuir">−</button>
                          <span>{item.quantidade}</span>
                          <button type="button" onClick={() => alterarQuantidade(indice, 1)} aria-label="Aumentar">+</button>
                        </div>
                        {abertaObs === indice || item.observacao ? (
                          <input
                            className="obs-item"
                            placeholder="Ex.: sem cebola"
                            maxLength={300}
                            autoFocus={abertaObs === indice}
                            value={item.observacao}
                            onChange={(e) => alterarObservacao(indice, e.target.value)}
                          />
                        ) : (
                          <button type="button" className="btn-link" onClick={() => setAbertaObs(indice)}>+ Observação</button>
                        )}
                      </div>
                    </div>
                  );
                })}
                <button type="button" className="btn-link" style={{ marginTop: 8 }} onClick={() => setEtapa('cardapio')}>+ Adicionar mais itens</button>
                {faltaParaMinimo > 0 && <p className="aviso-minimo">Faltam {moeda(faltaParaMinimo)} para o pedido mínimo de {moeda(restaurante.pedidoMinimo)}.</p>}
              </section>
            )}

            {etapa === 'entrega' && (
              <section>
                <h2 className="checkout-titulo">Como você quer receber?</h2>
                <div className="cartoes-escolha">
                  {modalidades.map((m) => (
                    <button type="button" key={m} className={`cartao-escolha ${dados.modalidade === m ? 'selecionado' : ''}`} onClick={() => setDados({ ...dados, modalidade: m })} aria-pressed={dados.modalidade === m}>
                      <Icone nome={ICONE_MODALIDADE[m]} tamanho={26} />
                      <strong>{MODALIDADE_LABEL[m]}</strong>
                      <span>{descricaoModalidade(m)}</span>
                    </button>
                  ))}
                </div>

                {dados.modalidade === 'ENTREGA' && (
                  <div className="bloco-campos">
                    {restaurante.tipoTaxaEntrega === 'POR_BAIRRO' && (
                      <div className="campo">
                        <label htmlFor="bairro">Bairro</label>
                        <select id="bairro" value={dados.bairroId} onChange={set('bairroId')}>
                          <option value="">Escolha o bairro</option>
                          {restaurante.bairros.map((b) => <option key={b.id} value={b.id}>{b.nome} — {moeda(b.taxa)}</option>)}
                        </select>
                        <small>Não achou seu bairro? O restaurante ainda não entrega aí.</small>
                      </div>
                    )}
                    <div className="campo">
                      <label htmlFor="endereco">Endereço de entrega</label>
                      <input id="endereco" value={dados.enderecoEntrega} onChange={set('enderecoEntrega')} placeholder="Rua, número e complemento" autoComplete="street-address" maxLength={500} />
                      <small>Inclua um ponto de referência se ajudar o entregador.</small>
                    </div>
                  </div>
                )}

                <h2 className="checkout-titulo">Seus dados</h2>
                <div className="bloco-campos">
                  <div className="campo">
                    <label htmlFor="nome">Nome</label>
                    <input id="nome" value={dados.nomeCliente} onChange={set('nomeCliente')} autoComplete="name" />
                  </div>
                  <div className="campo">
                    <label htmlFor="telefone">Telefone com DDD</label>
                    <input id="telefone" value={dados.telefoneCliente} onChange={set('telefoneCliente')} inputMode="tel" autoComplete="tel" placeholder="(11) 99999-0000" />
                    <small>O restaurante usa para falar com você sobre o pedido.</small>
                  </div>
                </div>
              </section>
            )}

            {etapa === 'pagamento' && (
              <section>
                <h2 className="checkout-titulo">Pagamento</h2>
                <p className="ajuda">Você paga {dados.modalidade === 'ENTREGA' ? 'na entrega' : 'no balcão'}. Nada é cobrado agora.</p>
                <div className="cartoes-escolha pagamento">
                  {Object.entries(PAGAMENTO_LABEL).map(([valor, rotulo]) => (
                    <button type="button" key={valor} className={`cartao-escolha ${dados.formaPagamento === valor ? 'selecionado' : ''}`} onClick={() => setDados({ ...dados, formaPagamento: valor })} aria-pressed={dados.formaPagamento === valor}>
                      <Icone nome={ICONE_PAGAMENTO[valor]} tamanho={22} />
                      <strong>{rotulo}</strong>
                    </button>
                  ))}
                </div>

                {dados.formaPagamento === 'DINHEIRO' && (
                  <div className="bloco-campos">
                    <div className="opcoes-troco">
                      <label><input type="radio" name="troco" checked={!dados.precisaTroco} onChange={() => setDados({ ...dados, precisaTroco: false, trocoPara: '' })} /> Não preciso de troco</label>
                      <label><input type="radio" name="troco" checked={dados.precisaTroco} onChange={() => setDados({ ...dados, precisaTroco: true })} /> Preciso de troco</label>
                    </div>
                    {dados.precisaTroco && (
                      <div className="campo">
                        <label htmlFor="troco">Troco para quanto?</label>
                        <input id="troco" type="number" inputMode="decimal" step="0.01" min={total} value={dados.trocoPara} onChange={set('trocoPara')} placeholder={`Valor maior que ${moeda(total)}`} />
                      </div>
                    )}
                  </div>
                )}

                <div className="campo">
                  <label htmlFor="obs">Observação para o restaurante <span className="opcional">(opcional)</span></label>
                  <textarea id="obs" rows={2} maxLength={500} value={dados.observacao} onChange={set('observacao')} placeholder="Ex.: interfone quebrado, bater na porta" />
                </div>

                <div className="resumo-sacola">
                  <div><span>Itens ({quantidadeTotal})</span><span>{moeda(subtotal)}</span></div>
                  {dados.modalidade === 'ENTREGA' && <div><span>Entrega</span><span>{moeda(taxaEntrega)}</span></div>}
                  <div className="total"><span>Total</span><strong>{moeda(total)}</strong></div>
                </div>
              </section>
            )}

          </div>

          <div className="rodape-checkout">
            {erro && <div className="erro" role="alert">{erro}</div>}
            {etapa !== 'pagamento' ? (
              <button type="button" className="btn btn-latao btn-enviar" onClick={avancar} disabled={itens.length === 0}>
                Continuar · {moeda(etapa === 'sacola' ? subtotal : total)}
              </button>
            ) : (
              <button type="button" className="btn btn-latao btn-enviar" onClick={enviarPedido} disabled={enviando || !restaurante.aceitandoPedidos}>
                {enviando ? 'Enviando...' : `Fazer pedido · ${moeda(total)}`}
              </button>
            )}
          </div>
        </main>
      )}
    </div>
  );
}
