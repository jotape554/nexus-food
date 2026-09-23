import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { api } from '../api/http';

function formatarMoeda(valor) {
  return Number(valor ?? 0).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
}

const NOME_PLANO = { BASICO: 'Básico', PROFISSIONAL: 'Profissional', PREMIUM: 'Premium' };

const STATUS_LABEL = {
  TRIAL: 'Período de teste',
  ATIVA: 'Ativa',
  INATIVA: 'Inativa',
  CANCELADA: 'Cancelada',
};

export default function Assinatura() {
  const [searchParams, setSearchParams] = useSearchParams();
  const [status, setStatus] = useState(null);
  const [planos, setPlanos] = useState([]);
  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState('');
  const [processando, setProcessando] = useState(null);

  const checkoutParam = searchParams.get('checkout');

  function carregar() {
    setCarregando(true);
    Promise.all([api.get('/api/assinatura'), api.get('/api/assinatura/planos')])
      .then(([s, p]) => {
        setStatus(s);
        setPlanos(p);
      })
      .catch((e) => setErro(e.message))
      .finally(() => setCarregando(false));
  }

  useEffect(carregar, []);

  useEffect(() => {
    if (checkoutParam === 'sucesso') {
      // A ativação chega pelo webhook da Stripe, que pode levar alguns segundos.
      const t = setTimeout(carregar, 2000);
      return () => clearTimeout(t);
    }
  }, [checkoutParam]);

  async function assinar(plano) {
    setProcessando(plano);
    setErro('');
    try {
      const { url } = await api.post(`/api/assinatura/checkout?plano=${plano}`);
      window.location.href = url;
    } catch (e) {
      setErro(e.message);
      setProcessando(null);
    }
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

  if (carregando) {
    return (
      <div>
        <div className="page-header"><div><h2>Assinatura</h2></div></div>
        <p>Carregando...</p>
      </div>
    );
  }

  return (
    <div>
      <div className="page-header">
        <div>
          <h2>Assinatura</h2>
          <p>Seu plano no Nexus Food.</p>
        </div>
        {status?.status === 'ATIVA' && (
          <button className="btn btn-secundario" disabled={processando === 'portal'} onClick={abrirPortal}>
            {processando === 'portal' ? 'Abrindo...' : 'Gerenciar cobrança'}
          </button>
        )}
      </div>

      {erro && <div className="erro">{erro}</div>}

      {checkoutParam === 'sucesso' && (
        <div className="panel" style={{ padding: '14px 20px', marginBottom: 20, borderColor: 'var(--verde-navalha)', background: '#EAF3EC' }}>
          Pagamento confirmado! Sua assinatura está sendo ativada — isso leva só alguns segundos.
        </div>
      )}
      {checkoutParam === 'cancelado' && (
        <div className="panel" style={{ padding: '14px 20px', marginBottom: 20 }}>
          Checkout cancelado. Nenhuma cobrança foi feita.
        </div>
      )}

      {status && (
        <div className="panel" style={{ padding: 24, marginBottom: 28 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', flexWrap: 'wrap', gap: 16, alignItems: 'center' }}>
            <div>
              <div className="rotulo" style={{ color: 'var(--texto-suave)', fontSize: '0.82rem', textTransform: 'uppercase', letterSpacing: '0.03em' }}>
                Status atual
              </div>
              <div style={{ fontFamily: 'var(--fonte-titulo)', fontSize: '1.4rem', marginTop: 4 }}>
                {STATUS_LABEL[status.status] || status.status}
                {status.status === 'TRIAL' && status.diasRestantesTrial != null && (
                  <span style={{ fontFamily: 'var(--fonte-corpo)', fontSize: '0.95rem', color: 'var(--texto-suave)', marginLeft: 10 }}>
                    {!status.acessoLiberado
                      ? '· terminou'
                      : status.diasRestantesTrial > 0
                        ? `· termina em ${status.diasRestantesTrial} dia${status.diasRestantesTrial === 1 ? '' : 's'}`
                        : '· termina hoje'}
                  </span>
                )}
              </div>
            </div>
            <span className={`badge ${status.acessoLiberado ? 'badge-confirmado' : 'badge-cancelado'}`}>
              {status.acessoLiberado ? 'Acesso liberado' : 'Acesso bloqueado'}
            </span>
          </div>

          {!status.acessoLiberado && (
            <div className="erro" style={{ marginTop: 18, marginBottom: 0 }}>
              Seu período de teste terminou e o acesso ao painel está bloqueado. Assine um plano abaixo para continuar.
            </div>
          )}
        </div>
      )}

      <div className="form-grid">
        {planos.map((p) => {
          const ehPlanoAtivo = status?.plano === p.plano && status?.status === 'ATIVA';
          return (
            <div key={p.plano} className="metric-card" style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
              <div>
                <div className="rotulo">{NOME_PLANO[p.plano] || p.plano}</div>
                <div className="valor" style={{ fontSize: '1.6rem' }}>
                  {formatarMoeda(p.precoMensal)}<span style={{ fontFamily: 'var(--fonte-corpo)', fontSize: '0.85rem', color: 'var(--texto-suave)' }}>/mês</span>
                </div>
              </div>
              <p style={{ color: 'var(--texto-suave)', fontSize: '0.9rem', margin: 0, flex: 1 }}>{p.descricao}</p>
              <button
                className={ehPlanoAtivo ? 'btn btn-secundario' : 'btn btn-latao'}
                disabled={ehPlanoAtivo || processando === p.plano}
                onClick={() => assinar(p.plano)}
              >
                {ehPlanoAtivo ? 'Plano atual' : processando === p.plano ? 'Redirecionando...' : 'Assinar este plano'}
              </button>
            </div>
          );
        })}
      </div>
    </div>
  );
}
