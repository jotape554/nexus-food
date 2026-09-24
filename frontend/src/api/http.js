import { DEMO } from '../demo/modo';

const TOKEN_KEY = 'nexusfood_token';
export const EVENTO_SESSAO_ENCERRADA = 'nexusfood:sessao-encerrada';
const API_BASE = import.meta.env.VITE_API_URL || '';

// O navegador pode recusar o armazenamento (janela anônima, dados bloqueados): sem ele o
// usuário só precisa entrar de novo, a tela não pode quebrar.
export function getToken() {
  try { return localStorage.getItem(TOKEN_KEY); } catch { return null; }
}

export function setToken(token) {
  try {
    if (token) localStorage.setItem(TOKEN_KEY, token);
    else localStorage.removeItem(TOKEN_KEY);
  } catch { /* segue sem guardar */ }
}

async function request(path, { method = 'GET', body, autenticado = true } = {}) {
  if (DEMO) {
    const { responder } = await import('../demo/apiFalsa');
    return responder(path, { method, body });
  }

  const headers = { 'Content-Type': 'application/json' };
  if (autenticado) {
    const token = getToken();
    if (token) headers['Authorization'] = `Bearer ${token}`;
  }

  const resp = await fetch(`${API_BASE}${path}`, {
    method,
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });

  if (resp.status === 204) return null;

  let data = null;
  const texto = await resp.text();
  if (texto) {
    try { data = JSON.parse(texto); } catch { data = texto; }
  }

  // Token vencido ou usuário desativado: encerra a sessão e a tela volta para o login.
  if (resp.status === 401 && autenticado && getToken()) {
    setToken(null);
    window.dispatchEvent(new CustomEvent(EVENTO_SESSAO_ENCERRADA, { detail: data?.mensagem }));
  }

  if (!resp.ok) {
    const mensagem = (data && data.mensagem) || `Erro ${resp.status}`;
    const erro = new Error(mensagem);
    erro.status = resp.status;
    erro.dados = data;
    throw erro;
  }

  return data;
}

export const api = {
  get: (path) => request(path),
  post: (path, body, opts) => request(path, { method: 'POST', body, ...opts }),
  put: (path, body) => request(path, { method: 'PUT', body }),
  patch: (path, body) => request(path, { method: 'PATCH', body }),
  delete: (path) => request(path, { method: 'DELETE' }),
  publica: {
    get: (path) => request(path, { autenticado: false }),
    post: (path, body) => request(path, { method: 'POST', body, autenticado: false }),
  },
};
