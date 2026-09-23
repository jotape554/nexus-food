import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { api } from '../../api/http';
import { moeda, MODALIDADE_LABEL, PAGAMENTO_LABEL } from '../../api/formato';
import '../../styles/public.css';

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

function novaChave() {
  return window.crypto?.randomUUID?.() || `${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

function Produto({ produto, quantidade, onAdicionar }) {
  return (
    <div className={`produto-publico ${produto.disponivel ? '' : 'esgotado'}`}>
      <div className="produto-publico-texto">
        <strong>{produto.nome}</strong>
        {produto.descricao && <p>{produto.descricao}</p>}
        <span className="preco">{moeda(produto.preco)}</span>
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
  const [etapa, setEtapa] = useState('cardapio'); // cardapio | sacola
  const [dados, setDados] = useState(() => ({
    nomeCliente: '', telefoneCliente: '', ...lerLocal('nexusfood_cliente', {}),
    modalidade: '', formaPagamento: 'PIX', trocoPara: '', enderecoEntrega: '', bairroId: '', observacao: '',
  }));
  const [erro, setErro] = useState('');
  const [enviando, setEnviando] = useState(false);
  const chaveIdempotencia = useRef(novaChave());

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

  const itens = carrinho
    .map((item) => ({ ...item, produto: produtosPorId[item.produtoId] }))
    .filter((item) => item.produto && item.produto.disponivel);

  const subtotal = itens.reduce((soma, i) => soma + Number(i.produto.preco) * i.quantidade, 0);
  const quantidadeTotal = itens.reduce((soma, i) => soma + i.quantidade, 0);

  let taxaEntrega = 0;
  if (restaurante && dados.modalidade === 'ENTREGA') {
    if (restaurante.tipoTaxaEntrega === 'FIXA') taxaEntrega = Number(restaurante.taxaEntregaFixa);
    else taxaEntrega = Number(restaurante.bairros.find((b) => String(b.id) === String(dados.bairroId))?.taxa || 0);
  }
  const total = subtotal + taxaEntrega;
  const faltaParaMinimo = restaurante ? Number(restaurante.pedidoMinimo) - subtotal : 0;

  function adicionar(produto) {
    setCarrinho((c) => {
      const existente = c.find((i) => i.produtoId === produto.id && !i.observacao);
      if (existente) return c.map((i) => (i === existente ? { ...i, quantidade: Math.min(99, i.quantidade + 1) } : i));
      return [...c, { produtoId: produto.id, quantidade: 1, observacao: '' }];
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

  const set = (campo) => (e) => setDados({ ...dados, [campo]: e.target.value });

  async function enviarPedido(e) {
    e.preventDefault();
    setErro('');
    setEnviando(true);
    try {
      const pedido = await api.publica.post(`/public/restaurantes/${slug}/pedidos`, {
        chaveIdempotencia: chaveIdempotencia.current,
        nomeCliente: dados.nomeCliente,
        telefoneCliente: dados.telefoneCliente,
        modalidade: dados.modalidade,
        formaPagamento: dados.formaPagamento,
        trocoPara: dados.formaPagamento === 'DINHEIRO' && dados.trocoPara ? Number(dados.trocoPara) : null,
        enderecoEntrega: dados.modalidade === 'ENTREGA' ? dados.enderecoEntrega : null,
        bairroId: dados.modalidade === 'ENTREGA' && dados.bairroId ? Number(dados.bairroId) : null,
        observacao: dados.observacao,
        itens: itens.map((i) => ({ produtoId: i.produtoId, quantidade: i.quantidade, observacao: i.observacao })),
      });
      gravarLocal('nexusfood_cliente', { nomeCliente: dados.nomeCliente, telefoneCliente: dados.telefoneCliente });
      setCarrinho([]);
      chaveIdempotencia.current = novaChave();
      navigate(`/pedido/${pedido.codigoPublico}`);
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
    <div className="pub-shell">
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
          {quantidadeTotal > 0 && (
            <button className="barra-sacola" onClick={() => { setErro(''); setEtapa('sacola'); }}>
              <span>Ver sacola ({quantidadeTotal})</span>
              <strong>{moeda(subtotal)}</strong>
            </button>
          )}
        </main>
      ) : (
        <main className="pub-content">
          <form className="pub-card" onSubmit={enviarPedido}>
            <button type="button" className="btn-link" onClick={() => setEtapa('cardapio')}>← Voltar ao cardápio</button>
            <h2 style={{ marginTop: 12 }}>Sua sacola</h2>

            {itens.length === 0 && <p>Sua sacola está vazia.</p>}
            {carrinho.map((item, indice) => {
              const produto = produtosPorId[item.produtoId];
              if (!produto || !produto.disponivel) return null;
              return (
                <div key={indice} className="item-sacola">
                  <div className="item-sacola-linha">
                    <div className="controle-qtd">
                      <button type="button" onClick={() => alterarQuantidade(indice, -1)} aria-label="Diminuir">−</button>
                      <span>{item.quantidade}</span>
                      <button type="button" onClick={() => alterarQuantidade(indice, 1)} aria-label="Aumentar">+</button>
                    </div>
                    <span className="nome">{produto.nome}</span>
                    <span>{moeda(Number(produto.preco) * item.quantidade)}</span>
                  </div>
                  <input
                    className="obs-item"
                    placeholder="Alguma observação? (ex.: sem cebola)"
                    maxLength={300}
                    value={item.observacao}
                    onChange={(e) => alterarObservacao(indice, e.target.value)}
                  />
                </div>
              );
            })}

            {!restaurante.aceitandoPedidos && <div className="erro">O restaurante está fechado agora e não está aceitando pedidos.</div>}

            <h3>Como você quer receber?</h3>
            <div className="opcao-lista opcoes-linha">
              {modalidades.map((m) => (
                <button type="button" key={m} className={`opcao ${dados.modalidade === m ? 'selecionada' : ''}`} onClick={() => setDados({ ...dados, modalidade: m })}>
                  {MODALIDADE_LABEL[m]}
                </button>
              ))}
            </div>

            {dados.modalidade === 'ENTREGA' && (
              <>
                {restaurante.tipoTaxaEntrega === 'POR_BAIRRO' && (
                  <div className="campo">
                    <label>Bairro</label>
                    <select value={dados.bairroId} onChange={set('bairroId')} required>
                      <option value="">Escolha o bairro</option>
                      {restaurante.bairros.map((b) => <option key={b.id} value={b.id}>{b.nome} — {moeda(b.taxa)}</option>)}
                    </select>
                  </div>
                )}
                <div className="campo">
                  <label>Endereço de entrega</label>
                  <input value={dados.enderecoEntrega} onChange={set('enderecoEntrega')} placeholder="Rua, número, complemento, referência" maxLength={500} required />
                </div>
              </>
            )}

            <h3>Seus dados</h3>
            <div className="campo"><label>Nome</label><input value={dados.nomeCliente} onChange={set('nomeCliente')} autoComplete="name" required /></div>
            <div className="campo"><label>Telefone (WhatsApp)</label><input value={dados.telefoneCliente} onChange={set('telefoneCliente')} inputMode="tel" autoComplete="tel" placeholder="(11) 99999-0000" required /></div>

            <h3>Pagamento na entrega/retirada</h3>
            <div className="campo">
              <select value={dados.formaPagamento} onChange={set('formaPagamento')}>
                {Object.entries(PAGAMENTO_LABEL).map(([valor, rotulo]) => <option key={valor} value={valor}>{rotulo}</option>)}
              </select>
            </div>
            {dados.formaPagamento === 'DINHEIRO' && (
              <div className="campo"><label>Troco para quanto? (opcional)</label><input type="number" step="0.01" min="0" value={dados.trocoPara} onChange={set('trocoPara')} /></div>
            )}
            <div className="campo"><label>Observação do pedido (opcional)</label><textarea rows={2} maxLength={500} value={dados.observacao} onChange={set('observacao')} /></div>

            <div className="resumo-sacola">
              <div><span>Itens</span><span>{moeda(subtotal)}</span></div>
              {dados.modalidade === 'ENTREGA' && <div><span>Entrega</span><span>{moeda(taxaEntrega)}</span></div>}
              <div className="total"><span>Total</span><span>{moeda(total)}</span></div>
            </div>
            {faltaParaMinimo > 0 && <p className="aviso-minimo">Faltam {moeda(faltaParaMinimo)} para o pedido mínimo.</p>}

            {erro && <div className="erro">{erro}</div>}
            <button
              className="btn btn-latao btn-enviar"
              disabled={enviando || itens.length === 0 || faltaParaMinimo > 0 || !restaurante.aceitandoPedidos}
            >
              {enviando ? 'Enviando...' : `Fazer pedido · ${moeda(total)}`}
            </button>
          </form>
        </main>
      )}
    </div>
  );
}
