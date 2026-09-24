import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../api/http';
import { moeda } from '../api/formato';
import Icone from '../components/Icone';
import Modal from '../components/Modal';
import { useAuth } from '../context/AuthContext';
import { NOME_PLANO, usePlano } from '../plano';
import { DEMO } from '../demo/modo';

const ORDEM = ['BASICO', 'PROFISSIONAL', 'PREMIUM'];

function dataCompleta(iso) {
  const [a, m, d] = iso.split('-');
  return `${d}/${m}/${a}`;
}

function historico(dias) {
  if (dias == null) return 'Relatórios com todo o histórico';
  if (dias >= 365) return 'Relatórios dos últimos 12 meses';
  return `Relatórios dos últimos ${dias} dias`;
}

/** O que cada plano inclui, na ordem em que aparece no cartão. */
function itensDoPlano(p) {
  const tem = (r) => p.recursos.includes(r);
  return [
    { texto: 'Cardápio digital com pedidos pelo celular', incluido: tem('CARDAPIO') },
    { texto: 'Painel de pedidos e cadastro de clientes', incluido: tem('PEDIDOS') },
    { texto: historico(p.historicoDias), incluido: tem('RELATORIOS') },
    { texto: p.limiteUsuarios == null ? 'Usuários sem limite' : `Até ${p.limiteUsuarios} usuários na equipe`, incluido: tem('EQUIPE') },
    { texto: 'Nexus Score: nota de 0 a 100 e notas por área', incluido: tem('NEXUS_SCORE') },
    { texto: 'Indicadores detalhados e dicas do Nexus', incluido: tem('NEXUS_DETALHES') },
  ];
}

/** O que deixa de aparecer ao descer de plano (para a confirmação ser honesta). */
function perdas(atual, novo) {
  const itensNovo = itensDoPlano(novo);
  return itensDoPlano(atual)
    .map((item, i) => ({ antes: item, depois: itensNovo[i] }))
    .filter(({ antes, depois }) => antes.incluido && (!depois.incluido || antes.texto !== depois.texto))
    .map(({ antes, depois }) => (depois.incluido ? `${antes.texto} (passa a ser: ${depois.texto.toLowerCase()})` : antes.texto));
}

function Resumo({ status }) {
  const trial = status.status === 'TRIAL';
  const rotuloStatus = {
    TRIAL: status.acessoLiberado ? 'Teste grátis' : 'Teste grátis encerrado',
    ATIVA: 'Assinatura ativa',
    INATIVA: 'Assinatura inativa',
    CANCELADA: 'Assinatura cancelada',
  }[status.status];

  return (
    <section className="panel assinatura-resumo">
      <div className="assinatura-resumo-topo">
        <div>
          <div className="rotulo-secao">Situação</div>
          <h3>{rotuloStatus}</h3>
          <p className="suave">
            {trial && status.acessoLiberado && (
              <>
                {status.diasRestantesTrial > 0
                  ? `Termina em ${status.diasRestantesTrial} ${status.diasRestantesTrial === 1 ? 'dia' : 'dias'} (${dataCompleta(status.dataFimTrial)}). `
                  : 'Termina hoje. '}
                No teste você usa tudo o que o Premium tem. Escolha um plano para continuar depois.
              </>
            )}
            {!status.acessoLiberado && 'O painel está bloqueado. Escolha um plano abaixo para voltar a usar; nada foi apagado.'}
            {status.status === 'ATIVA' && <>Plano {NOME_PLANO[status.plano]}, {moeda(status.precoMensal)} por mês.</>}
          </p>
        </div>
        <span className={`badge ${status.acessoLiberado ? 'badge-confirmado' : 'badge-cancelado'}`}>
          {status.acessoLiberado ? 'Acesso liberado' : 'Acesso bloqueado'}
        </span>
      </div>

      {status.acessoLiberado && (
        <dl className="assinatura-uso">
          <div>
            <dt>Usuários</dt>
            <dd>{status.limiteUsuarios == null ? `${status.usuariosAtivos} ${status.usuariosAtivos === 1 ? 'ativo' : 'ativos'} · sem limite` : `${status.usuariosAtivos} de ${status.limiteUsuarios}`}</dd>
          </div>
          <div>
            <dt>Relatórios</dt>
            <dd>{status.primeiroDiaRelatorio ? `desde ${dataCompleta(status.primeiroDiaRelatorio)}` : 'todo o histórico'}</dd>
          </div>
          <div>
            <dt>Nexus Score</dt>
            <dd>{status.recursos.NEXUS_DETALHES ? 'completo' : status.recursos.NEXUS_SCORE ? 'nota e áreas' : 'não incluído'}</dd>
          </div>
        </dl>
      )}
    </section>
  );
}

export default function Assinatura() {
  const [searchParams] = useSearchParams();
  const queryClient = useQueryClient();
  const { usuario } = useAuth();
  const { status, carregando, recarregar } = usePlano();
  const { data: planos = [] } = useQuery({ queryKey: ['planos'], queryFn: () => api.get('/api/assinatura/planos'), staleTime: Infinity });

  const [erro, setErro] = useState('');
  const [sucesso, setSucesso] = useState('');
  const [processando, setProcessando] = useState(null);
  const [trocando, setTrocando] = useState(null); // plano escolhido aguardando confirmação

  const admin = usuario?.papel === 'ADMINISTRADOR';
  const checkout = searchParams.get('checkout');

  useEffect(() => {
    if (checkout === 'sucesso') {
      // A ativação chega pelo webhook da Stripe, que pode levar alguns segundos.
      const t = setTimeout(recarregar, 2500);
      return () => clearTimeout(t);
    }
  }, [checkout]);

  const assinando = status?.assinaturaNaStripe;
  const planoAtual = planos.find((p) => p.plano === status?.plano);

  async function escolher(plano) {
    setProcessando(plano.plano);
    setErro('');
    setSucesso('');
    try {
      const r = await api.post(`/api/assinatura/plano?plano=${plano.plano}`);
      if (r.url) {
        window.location.href = r.url;
        return;
      }
      setSucesso(r.mensagem);
      setTrocando(null);
      queryClient.invalidateQueries();
    } catch (e) {
      setErro(e.message);
      setTrocando(null);
    }
    setProcessando(null);
  }

  async function abrirPortal() {
    setProcessando('portal');
    setErro('');
    try {
      const { url } = await api.post('/api/assinatura/portal');
      window.location.href = url;
    } catch (e) {
      setErro(e.message);
      setProcessando(null);
    }
  }

  if (carregando || !status) {
    return (
      <div>
        <div className="page-header"><div><h2>Assinatura</h2></div></div>
        <p>Carregando...</p>
      </div>
    );
  }

  const descendo = trocando && planoAtual && ORDEM.indexOf(trocando.plano) < ORDEM.indexOf(planoAtual.plano);

  return (
    <div className="assinatura">
      <div className="page-header">
        <div>
          <h2>Assinatura</h2>
          <p>Seu plano no Nexus Food. Sem fidelidade: dá para mudar ou cancelar quando quiser.</p>
        </div>
        {admin && assinando && !DEMO && (
          <button className="btn btn-secundario" disabled={processando === 'portal'} onClick={abrirPortal}>
            {processando === 'portal' ? 'Abrindo...' : 'Cartão e faturas'}
          </button>
        )}
      </div>

      {erro && <div className="erro" role="alert">{erro}</div>}
      {sucesso && <div className="sucesso" role="status">{sucesso}</div>}
      {checkout === 'sucesso' && status.status !== 'ATIVA' && (
        <div className="sucesso" role="status">Pagamento recebido! A assinatura é ativada em alguns segundos.</div>
      )}
      {checkout === 'cancelado' && <div className="aviso-sessao">Pagamento cancelado. Nenhuma cobrança foi feita.</div>}

      <Resumo status={status} />

      <div className="assinatura-titulo-planos">
        <h3>Planos</h3>
        {!admin && <span className="suave">Só o administrador do restaurante muda o plano.</span>}
      </div>

      <div className="planos">
        {planos.map((p) => {
          const atual = status.status === 'ATIVA' && status.plano === p.plano;
          return (
            <article key={p.plano} className={`panel plano ${atual ? 'atual' : ''}`}>
              <header>
                <div className="plano-nome">
                  <h3>{p.nome}</h3>
                  {atual && <span className="tag-plano">Seu plano</span>}
                </div>
                <div className="plano-preco">{moeda(p.precoMensal)}<span>/mês</span></div>
                <p className="suave">{p.descricao}</p>
              </header>
              <ul>
                {itensDoPlano(p).map((item) => (
                  <li key={item.texto} className={item.incluido ? '' : 'fora'}>
                    <Icone nome={item.incluido ? 'check' : 'cadeado'} tamanho={14} />
                    <span>{item.texto}</span>
                  </li>
                ))}
              </ul>
              {admin && (
                <button
                  type="button"
                  className={atual ? 'btn btn-secundario' : 'btn btn-latao'}
                  disabled={atual || !!processando}
                  onClick={() => (assinando ? setTrocando(p) : escolher(p))}
                >
                  {atual ? 'Plano atual' : processando === p.plano ? 'Aguarde...' : assinando ? `Mudar para ${p.nome}` : `Assinar ${p.nome}`}
                </button>
              )}
            </article>
          );
        })}
      </div>

      {trocando && (
        <Modal titulo={`Mudar para o ${trocando.nome}?`} onFechar={() => setTrocando(null)}>
          <p className="modal-texto">
            Novo valor: <strong>{moeda(trocando.precoMensal)} por mês</strong>. A diferença entra proporcionalmente na próxima fatura.
          </p>
          {descendo && (
            <>
              <p className="modal-texto" style={{ marginBottom: 6 }}>Muda no painel:</p>
              <ul className="lista-perdas">
                {perdas(planoAtual, trocando).map((t) => <li key={t}>{t}</li>)}
              </ul>
              <p className="suave" style={{ fontSize: '0.88rem' }}>Nada é apagado: tudo volta se você mudar de plano de novo.</p>
            </>
          )}
          <div className="modal-acoes">
            <button type="button" className="btn btn-secundario" onClick={() => setTrocando(null)}>Voltar</button>
            <button type="button" className="btn btn-latao" disabled={!!processando} onClick={() => escolher(trocando)}>
              {processando ? 'Mudando...' : 'Confirmar mudança'}
            </button>
          </div>
        </Modal>
      )}
    </div>
  );
}
