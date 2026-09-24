import { createContext, useContext, useEffect, useState } from 'react';
import { api, getToken, setToken, EVENTO_SESSAO_ENCERRADA } from '../api/http';
import { DEMO } from '../demo/modo';

const CHAVE_USUARIO = 'nexusfood_usuario';

// Na demonstração a pessoa já entra logada como a dona do restaurante de exemplo.
const USUARIO_DEMO = { nome: 'Joana Martins', papel: 'ADMINISTRADOR', restauranteId: 1, restauranteSlug: 'cantina-da-nona' };

function lerUsuarioSalvo() {
  try {
    const salvo = localStorage.getItem(CHAVE_USUARIO);
    return salvo ? JSON.parse(salvo) : null;
  } catch {
    return null;
  }
}

function gravarUsuario(dados) {
  try {
    if (dados) localStorage.setItem(CHAVE_USUARIO, JSON.stringify(dados));
    else localStorage.removeItem(CHAVE_USUARIO);
  } catch { /* segue sem guardar */ }
}

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [usuario, setUsuario] = useState(() => (DEMO ? USUARIO_DEMO : lerUsuarioSalvo()));
  // Recado para a tela de login quando a sessão cai sozinha (ex.: acesso desativado).
  const [aviso, setAviso] = useState('');

  useEffect(() => {
    function encerrar(e) {
      gravarUsuario(null);
      setUsuario(null);
      setAviso(e.detail || 'Sua sessão terminou. Entre de novo.');
    }
    window.addEventListener(EVENTO_SESSAO_ENCERRADA, encerrar);
    return () => window.removeEventListener(EVENTO_SESSAO_ENCERRADA, encerrar);
  }, []);

  function salvarSessao(resposta) {
    setToken(resposta.token);
    const dados = {
      nome: resposta.nome,
      papel: resposta.papel,
      restauranteId: resposta.restauranteId,
      restauranteSlug: resposta.restauranteSlug,
    };
    gravarUsuario(dados);
    setUsuario(dados);
  }

  async function login(email, senha) {
    setAviso('');
    const resposta = await api.post('/auth/login', { email, senha }, { autenticado: false });
    salvarSessao(resposta);
  }

  async function registrar(dados) {
    const resposta = await api.post('/auth/registro', dados, { autenticado: false });
    salvarSessao(resposta);
  }

  function sair() {
    setToken(null);
    gravarUsuario(null);
    setUsuario(null);
  }

  const autenticado = DEMO ? !!usuario : !!getToken() && !!usuario;

  return (
    <AuthContext.Provider value={{ usuario, autenticado, login, registrar, sair, aviso }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  return useContext(AuthContext);
}
