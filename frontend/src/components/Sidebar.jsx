import { NavLink, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import AssinaturaNexus from './AssinaturaNexus';

const ITENS = [
  { to: '/painel/pedidos', label: 'Pedidos', icone: '◱' },
  { to: '/painel/cardapio', label: 'Cardápio', icone: '☰' },
  { to: '/painel/clientes', label: 'Clientes', icone: '◍' },
  { to: '/painel/relatorios', label: 'Relatórios', icone: '▥', gestao: true },
  { to: '/painel/nexus', label: 'Nexus Score', icone: '✦', gestao: true },
  { to: '/painel/configuracoes', label: 'Configurações', icone: '⚙', gestao: true },
  { to: '/painel/assinatura', label: 'Assinatura', icone: '◆', gestao: true },
];

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

  // Atendente opera pedidos, cardápio (esgotado) e clientes; relatórios, configurações e assinatura são da gestão.
  const itens = usuario?.papel === 'ATENDENTE' ? ITENS.filter((item) => !item.gestao) : ITENS;

  return (
    <aside className="sidebar">
      <div className="sidebar-brand">Nexus<span>Food</span></div>
      <nav>
        {itens.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            className={({ isActive }) => (isActive ? 'active' : '')}
          >
            <span aria-hidden="true" style={{ marginRight: 10, opacity: 0.85 }}>{item.icone}</span>
            {item.label}
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
            style={{ padding: 0, color: 'rgba(239,235,226,0.55)', fontSize: '0.78rem', textTransform: 'capitalize' }}
          >
            {usuario?.papel?.toLowerCase()} · Sair
          </button>
        </div>
      </div>
    </aside>
  );
}
