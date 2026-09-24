import { lazy, Suspense, useEffect } from 'react';
import { BrowserRouter, Routes, Route, Navigate, Outlet, Link, useLocation, useNavigate } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import ProtectedRoute from './components/ProtectedRoute';
import Sidebar from './components/Sidebar';
import { usePlano } from './plano';
import { useAuth } from './context/AuthContext';
import Icone from './components/Icone';

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
const Nexus = lazy(() => import('./pages/Nexus'));
const Configuracoes = lazy(() => import('./pages/Configuracoes'));
const Assinatura = lazy(() => import('./pages/Assinatura'));
const Equipe = lazy(() => import('./pages/Equipe'));

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

/**
 * O plano do restaurante ficou menor que a equipe e esta pessoa está entre as últimas a entrar:
 * o backend recusa as telas (402) até o administrador ajustar a equipe ou o plano.
 */
function ForaDoLimite() {
  const { sair } = useAuth();
  return (
    <section className="panel recurso-bloqueado" role="alert">
      <span className="recurso-bloqueado-icone"><Icone nome="equipe" tamanho={22} /></span>
      <div className="recurso-bloqueado-corpo">
        <h3>Seu acesso está pausado</h3>
        <p>
          O plano atual do restaurante permite menos usuários do que a equipe cadastrada. Peça ao
          administrador para desativar alguém em Equipe ou mudar de plano. Nenhum dado foi apagado.
        </p>
        <button type="button" className="btn btn-secundario" onClick={sair}>Sair</button>
      </div>
    </section>
  );
}

function PainelLayout() {
  const location = useLocation();
  const navigate = useNavigate();
  const { status, recarregar } = usePlano();
  const naAssinatura = location.pathname === '/painel/assinatura';

  // Ao trocar de tela, confere de novo (o plano pode ter mudado em outra aba ou pelo webhook).
  useEffect(() => { recarregar(); }, [location.pathname]);

  useEffect(() => {
    if (status && !status.acessoLiberado && !naAssinatura) {
      navigate('/painel/assinatura', { replace: true });
    }
  }, [status, naAssinatura]);

  const pausado = status?.usuarioForaDoLimite && !naAssinatura;

  return (
    <div className="app-shell">
      <Sidebar />
      <main className={`main ${location.pathname.startsWith('/painel/pedidos') ? 'main-largo' : ''}`}>
        <TrialBanner status={status} />
        <Suspense fallback={<CarregandoPagina />}>
          {pausado ? <ForaDoLimite /> : <Outlet />}
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
          <Route path="nexus" element={<Nexus />} />
          <Route path="configuracoes" element={<Configuracoes />} />
          <Route path="equipe" element={<Equipe />} />
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
