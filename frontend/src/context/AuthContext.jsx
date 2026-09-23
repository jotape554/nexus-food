import { createContext, useContext, useState } from 'react';
import { api, getToken, setToken } from '../api/http';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [usuario, setUsuario] = useState(() => {
    const salvo = localStorage.getItem('nexusfood_usuario');
    return salvo ? JSON.parse(salvo) : null;
  });

  function salvarSessao(resposta) {
    setToken(resposta.token);
    const dados = {
      nome: resposta.nome,
      papel: resposta.papel,
      restauranteId: resposta.restauranteId,
      restauranteSlug: resposta.restauranteSlug,
    };
    localStorage.setItem('nexusfood_usuario', JSON.stringify(dados));
    setUsuario(dados);
  }

  async function login(email, senha) {
    const resposta = await api.post('/auth/login', { email, senha }, { autenticado: false });
    salvarSessao(resposta);
  }

  async function registrar(dados) {
    const resposta = await api.post('/auth/registro', dados, { autenticado: false });
    salvarSessao(resposta);
  }

  function sair() {
    setToken(null);
    localStorage.removeItem('nexusfood_usuario');
    setUsuario(null);
  }

  const autenticado = !!getToken() && !!usuario;

  return (
    <AuthContext.Provider value={{ usuario, autenticado, login, registrar, sair }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  return useContext(AuthContext);
}
