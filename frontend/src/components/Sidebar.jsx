import { NavLink, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import AssinaturaNexus from './AssinaturaNexus';
import Icone from './Icone';
import Logo from './Logo';
import { NOME_PLANO, PLANO_DO_RECURSO, usePlano } from '../plano';

// perfis: quem vê o item (sem a chave = todos). recurso: mostra o selo do plano quando não está liberado.
const ITENS = [
  { to: '/painel/pedidos', label: 'Pedidos', icone: '◱' },
  { to: '/painel/cardapio', label: 'Cardápio', icone: '☰' },
  { to: '/painel/clientes', label: 'Clientes', icone: '◍' },
  { to: '/painel/relatorios', label: 'Relatórios', icone: '▥', perfis: ['ADMINISTRADOR', 'GERENTE'] },
  { to: '/painel/nexus', label: 'Nexus Score', icone: '✦', perfis: ['ADMINISTRADOR', 'GERENTE'], recurso: 'NEXUS_SCORE' },
  { to: '/painel/configuracoes', label: 'Configurações', icone: '⚙', perfis: ['ADMINISTRADOR', 'GERENTE'] },
  { to: '/painel/equipe', label: 'Equipe', icone: '◎', perfis: ['ADMINISTRADOR'] },
  { to: '/painel/assinatura', label: 'Assinatura', icone: '◆', perfis: ['ADMINISTRADOR', 'GERENTE'] },
];

const NOME_PAPEL = { ADMINISTRADOR: 'Administrador', GERENTE: 'Gerente', ATENDENTE: 'Atendente' };

function iniciais(nome) {
  if (!nome) return '?';
  const partes = nome.trim().split(/\s+/);
  return (partes[0][0] + (partes[1]?.[0] || '')).toUpperCase();
}

export default function Sidebar() {
  const { usuario, sair } = useAuth();
  const navigate = useNavigate();

  function handleSair() {
    sair();
    navigate('/login');
  }

  const { liberado } = usePlano();

  // Atendente opera pedidos, cardápio (esgotado) e clientes; o resto é da gestão; equipe, só do administrador.
  const itens = ITENS.filter((item) => !item.perfis || item.perfis.includes(usuario?.papel));

  return (
    <aside className="sidebar">
      <div className="sidebar-brand"><Logo tom="escuro" tamanho={28} /></div>
      <nav>
        {itens.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            className={({ isActive }) => (isActive ? 'active' : '')}
          >
            <span aria-hidden="true" style={{ marginRight: 10, opacity: 0.85 }}>{item.icone}</span>
            {item.label}
            {item.recurso && !liberado(item.recurso) && (
              <span className="icone-plano" title={`Disponível no plano ${NOME_PLANO[PLANO_DO_RECURSO[item.recurso]]}`}>
                <Icone nome="cadeado" tamanho={13} />
              </span>
            )}
          </NavLink>
        ))}
      </nav>
      <AssinaturaNexus tom="escuro" className="sidebar-assinatura" />
      <div className="sidebar-footer">
        <div className="avatar">{iniciais(usuario?.nome)}</div>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ color: 'var(--papel)', fontSize: '0.86rem', fontWeight: 600, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
            {usuario?.nome}
          </div>
          <button
            className="sidebar-link"
            onClick={handleSair}
            style={{ padding: 0, color: 'rgba(239,235,226,0.55)', fontSize: '0.78rem' }}
          >
            {NOME_PAPEL[usuario?.papel] || usuario?.papel} · Sair
          </button>
        </div>
      </div>
    </aside>
  );
}
