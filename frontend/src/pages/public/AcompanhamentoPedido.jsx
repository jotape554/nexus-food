import { useParams, Link, useLocation, useNavigate } from 'react-router-dom';
import Icone from '../../components/Icone';
import AssinaturaNexus from '../../components/AssinaturaNexus';
import { useQuery } from '@tanstack/react-query';
import { api } from '../../api/http';
import { moeda, hora, numeroPedido, MODALIDADE_LABEL, PAGAMENTO_LABEL, MOTIVO_CANCELAMENTO_LABEL } from '../../api/formato';
import OpcoesDoItem from '../../components/OpcoesDoItem';
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

/** Mostrada uma única vez, logo depois de o cliente fazer o pedido. */
function Confirmacao({ pedido, endereco, onAcompanhar }) {
  return (
    <div className="pub-shell">
      <main className="pub-content confirmacao">
        <div className="pub-card">
          <div className="confirmacao-icone"><Icone nome="check" tamanho={30} /></div>
          <h1>Pedido realizado!</h1>
          <p className="confirmacao-numero">Pedido {numeroPedido(pedido.numeroDia)}</p>
          <p className="ajuda">Seu pedido foi enviado para <strong>{pedido.restauranteNome}</strong>. Assim que o restaurante aceitar, você vê aqui.</p>

          <dl className="confirmacao-dados">
            <div>
              <dt>{MODALIDADE_LABEL[pedido.modalidade]}</dt>
              <dd>{endereco || (pedido.modalidade === 'RETIRADA' ? `Pronto por volta das ${hora(pedido.prontoPrevistoPara)}` : 'No restaurante')}</dd>
            </div>
            <div>
              <dt>Pagamento</dt>
              <dd>{PAGAMENTO_LABEL[pedido.formaPagamento]}, {pedido.modalidade === 'ENTREGA' ? 'na entrega' : 'no balcão'}</dd>
            </div>
            <div className="total">
              <dt>Total</dt>
              <dd>{moeda(pedido.total)}</dd>
            </div>
          </dl>

          <button className="btn btn-latao btn-enviar" onClick={onAcompanhar}>Acompanhar pedido →</button>
          <p className="ajuda pequena">Guarde o link desta página: por ele você acompanha o pedido a qualquer momento.</p>
        </div>
      </main>
    </div>
  );
}

export default function AcompanhamentoPedido() {
  const { codigo } = useParams();
  const location = useLocation();
  const navigate = useNavigate();
  const confirmacao = location.state?.confirmacao;
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

  if (confirmacao) {
    return (
      <Confirmacao
        pedido={pedido}
        endereco={confirmacao.endereco}
        onAcompanhar={() => navigate(location.pathname, { replace: true, state: null })}
      />
    );
  }

  const cancelado = pedido.status === 'CANCELADO';
  const lista = etapas(pedido);
  // Uma etapa pulada pelo restaurante (sem horário) conta como feita se alguma posterior já aconteceu.
  const ultimaFeita = lista.reduce((ultima, etapa, i) => (etapa.em ? i : ultima), 0);

  return (
    <div className="pub-shell">
      <header className="pub-header">
        <h1>Pedido {numeroPedido(pedido.numeroDia)}</h1>
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
                  <td>{item.nomeProduto}<OpcoesDoItem opcoes={item.opcoes} />{item.observacao && <div className="obs">{item.observacao}</div>}</td>
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
        <footer className="rodape-publico">
          <AssinaturaNexus prefixo="Pedidos online com Nexus Food · um produto" />
        </footer>
      </main>
    </div>
  );
}
