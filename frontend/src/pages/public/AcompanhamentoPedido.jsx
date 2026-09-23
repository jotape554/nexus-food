import { useParams, Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { api } from '../../api/http';
import { moeda, hora, MODALIDADE_LABEL, PAGAMENTO_LABEL, MOTIVO_CANCELAMENTO_LABEL } from '../../api/formato';
import '../../styles/public.css';

function etapas(pedido) {
  const lista = [
    { rotulo: 'Pedido recebido', em: pedido.criadoEm },
    { rotulo: 'Aceito pelo restaurante', em: pedido.confirmadoEm },
    { rotulo: 'Em preparo', em: pedido.emPreparoEm },
    { rotulo: pedido.modalidade === 'RETIRADA' ? 'Pronto para retirar' : 'Pronto', em: pedido.prontoEm },
  ];
  if (pedido.modalidade === 'ENTREGA') lista.push({ rotulo: 'Saiu para entrega', em: pedido.saiuParaEntregaEm });
  lista.push({ rotulo: pedido.modalidade === 'ENTREGA' ? 'Entregue' : 'Concluído', em: pedido.concluidoEm });
  return lista;
}

export default function AcompanhamentoPedido() {
  const { codigo } = useParams();
  const { data: pedido, isError } = useQuery({
    queryKey: ['acompanhamento', codigo],
    queryFn: () => api.publica.get(`/public/pedidos/${codigo}`),
    refetchInterval: (query) => {
      const status = query.state.data?.status;
      return status === 'CONCLUIDO' || status === 'CANCELADO' ? false : 10000;
    },
  });

  if (isError) {
    return <div className="pub-shell"><div className="pub-content" style={{ marginTop: 40 }}><div className="pub-card">Pedido não encontrado.</div></div></div>;
  }
  if (!pedido) return <div style={{ padding: 40, textAlign: 'center' }}>Carregando...</div>;

  const cancelado = pedido.status === 'CANCELADO';
  const lista = etapas(pedido);
  // Uma etapa pulada pelo restaurante (sem horário) conta como feita se alguma posterior já aconteceu.
  const ultimaFeita = lista.reduce((ultima, etapa, i) => (etapa.em ? i : ultima), 0);

  return (
    <div className="pub-shell">
      <header className="pub-header">
        <h1>Pedido #{pedido.numeroDia}</h1>
        <p>{pedido.restauranteNome} · {MODALIDADE_LABEL[pedido.modalidade]}</p>
      </header>
      <main className="pub-content">
        <div className="pub-card">
          {cancelado ? (
            <div className="erro">
              Este pedido foi cancelado{pedido.motivoCancelamento ? `: ${MOTIVO_CANCELAMENTO_LABEL[pedido.motivoCancelamento].toLowerCase()}` : ''}.
            </div>
          ) : (
            <>
              {!pedido.concluidoEm && <p className="previsao">Previsão: pronto por volta das <strong>{hora(pedido.prontoPrevistoPara)}</strong></p>}
              <ol className="etapas-pedido">
                {lista.map((etapa, i) => (
                  <li key={etapa.rotulo} className={i <= ultimaFeita ? 'feita' : ''}>
                    <span className="marcador" />
                    <span className="etapa-nome">{etapa.rotulo}</span>
                    {etapa.em && <span className="hora">{hora(etapa.em)}</span>}
                  </li>
                ))}
              </ol>
              {!pedido.concluidoEm && <p className="atualiza">Esta página se atualiza sozinha.</p>}
            </>
          )}

          <h3>Resumo</h3>
          <table className="tabela-itens">
            <tbody>
              {pedido.itens.map((item, i) => (
                <tr key={i}>
                  <td>{item.quantidade}×</td>
                  <td>{item.nomeProduto}{item.observacao && <div className="obs">{item.observacao}</div>}</td>
                  <td style={{ textAlign: 'right' }}>{moeda(item.subtotal)}</td>
                </tr>
              ))}
              {Number(pedido.taxaEntrega) > 0 && <tr><td></td><td>Entrega</td><td style={{ textAlign: 'right' }}>{moeda(pedido.taxaEntrega)}</td></tr>}
              <tr className="total"><td></td><td>Total</td><td style={{ textAlign: 'right' }}>{moeda(pedido.total)}</td></tr>
            </tbody>
          </table>
          <p>Pagamento: {PAGAMENTO_LABEL[pedido.formaPagamento]} (na {pedido.modalidade === 'ENTREGA' ? 'entrega' : 'retirada'})</p>

          <div className="acoes-acompanhamento">
            {pedido.restauranteTelefone && (
              <a className="btn btn-secundario" href={`https://wa.me/55${pedido.restauranteTelefone.replace(/\D/g, '').replace(/^55/, '')}`} target="_blank" rel="noreferrer">
                Falar com o restaurante
              </a>
            )}
            <Link className="btn btn-latao" to={`/r/${pedido.restauranteSlug}`}>Fazer outro pedido</Link>
          </div>
        </div>
      </main>
    </div>
  );
}
