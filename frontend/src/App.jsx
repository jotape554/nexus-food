import { lazy, Suspense, useEffect, useState } from 'react';
import { BrowserRouter, Routes, Route, Navigate, Outlet, Link, useLocation, useNavigate } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import ProtectedRoute from './components/ProtectedRoute';
import Sidebar from './components/Sidebar';
import { api } from './api/http';

import Login from './pages/auth/Login';
import Registro from './pages/auth/Registro';
import EsqueciSenha from './pages/auth/EsqueciSenha';
import RedefinirSenha from './pages/auth/RedefinirSenha';

// Cada tela carrega sob demanda: quem abre o cardápio pelo celular não baixa o código do painel.
const CardapioPublico = lazy(() => import('./pages/public/CardapioPublico'));
const AcompanhamentoPedido = lazy(() => import('./pages/public/AcompanhamentoPedido'));
const Pedidos = lazy(() => import('./pages/Pedidos'));
const Cardapio = lazy(() => import('./pages/Cardapio'));
const Clientes = lazy(() => import('./pages/Clientes'));
const Relatorios = lazy(() => import('./pages/Relatorios'));
const Configuracoes = lazy(() => import('./pages/Configuracoes'));
const Assinatura = lazy(() => import('./pages/Assinatura'));

function TrialBanner({ status }) {
  if (!status || status.status !== 'TRIAL' || status.diasRestantesTrial == null) return null;
  if (!status.acessoLiberado) return null;
  if (status.diasRestantesTrial > 3) return null;

  return (
    <div className="aviso-trial">
      <span>
        {status.diasRestantesTrial > 0
          ? `Seu período de teste termina em ${status.diasRestantesTrial} dia${status.diasRestantesTrial === 1 ? '' : 's'}.`
          : 'Seu período de teste termina hoje.'}
      </span>
      <Link to="/painel/assinatura" className="btn btn-latao" style={{ padding: '6px 14px' }}>Escolher plano</Link>
    </div>
  );
}

function PainelLayout() {
  const location = useLocation();
  const navigate = useNavigate();
  const [status, setStatus] = useState(null);

  useEffect(() => {
    api.get('/api/assinatura')
      .then((s) => {
        setStatus(s);
        if (!s.acessoLiberado && location.pathname !== '/painel/assinatura') {
          navigate('/painel/assinatura', { replace: true });
        }
      })
      .catch(() => {});
  }, [location.pathname]);

  return (
    <div className="app-shell">
      <Sidebar />
      <main className={`main ${location.pathname.startsWith('/painel/pedidos') ? 'main-largo' : ''}`}>
        <TrialBanner status={status} />
        <Suspense fallback={<CarregandoPagina />}>
          <Outlet />
        </Suspense>
      </main>
    </div>
  );
}

function CarregandoPagina() {
  return <div style={{ padding: 40, textAlign: 'center', color: 'var(--texto-suave)' }}>Carregando...</div>;
}

/** Todas as rotas do sistema. A demonstração monta estas mesmas rotas em dois roteadores. */
export function Rotas() {
  return (
    <Suspense fallback={<CarregandoPagina />}>
      <Routes>
        <Route path="/" element={<Navigate to="/login" replace />} />
        <Route path="/login" element={<Login />} />
        <Route path="/registro" element={<Registro />} />
        <Route path="/esqueci-senha" element={<EsqueciSenha />} />
        <Route path="/redefinir-senha" element={<RedefinirSenha />} />
        <Route path="/r/:slug" element={<CardapioPublico />} />
        <Route path="/pedido/:codigo" element={<AcompanhamentoPedido />} />

        <Route
          path="/painel"
          element={
            <ProtectedRoute>
              <PainelLayout />
            </ProtectedRoute>
          }
        >
          <Route index element={<Navigate to="/painel/pedidos" replace />} />
          <Route path="pedidos" element={<Pedidos />} />
          <Route path="cardapio" element={<Cardapio />} />
          <Route path="clientes" element={<Clientes />} />
          <Route path="relatorios" element={<Relatorios />} />
          <Route path="configuracoes" element={<Configuracoes />} />
          <Route path="assinatura" element={<Assinatura />} />
        </Route>

        <Route path="*" element={<Navigate to="/painel" replace />} />
      </Routes>
    </Suspense>
  );
}

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Rotas />
      </BrowserRouter>
    </AuthProvider>
  );
}
