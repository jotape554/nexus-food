import { useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { api } from '../../api/http';

export default function RedefinirSenha() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const token = searchParams.get('token') || '';

  const [novaSenha, setNovaSenha] = useState('');
  const [confirmarSenha, setConfirmarSenha] = useState('');
  const [erro, setErro] = useState('');
  const [concluido, setConcluido] = useState(false);
  const [carregando, setCarregando] = useState(false);

  async function handleSubmit(e) {
    e.preventDefault();
    setErro('');

    if (novaSenha !== confirmarSenha) {
      setErro('As senhas não são iguais.');
      return;
    }

    setCarregando(true);
    try {
      await api.post('/auth/redefinir-senha', { token, novaSenha }, { autenticado: false });
      setConcluido(true);
      setTimeout(() => navigate('/login'), 2500);
    } catch (err) {
      setErro(err.message || 'Não foi possível redefinir a senha.');
    } finally {
      setCarregando(false);
    }
  }

  if (!token) {
    return (
      <div className="auth-shell">
        <div className="auth-card">
          <h1>Link inválido</h1>
          <p className="sub">Esse link de redefinição de senha está incompleto.</p>
          <div className="auth-troca">
            <Link to="/esqueci-senha">Pedir um novo link</Link>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="auth-shell">
      <div className="auth-card">
        <h1>Criar nova senha</h1>
        <p className="sub">Escolha uma senha nova para sua conta.</p>

        {erro && <div className="erro">{erro}</div>}

        {concluido ? (
          <div className="sucesso">Senha alterada! Redirecionando para o login...</div>
        ) : (
          <form onSubmit={handleSubmit}>
            <div className="campo">
              <label>Nova senha</label>
              <input type="password" value={novaSenha} onChange={(e) => setNovaSenha(e.target.value)} required />
            </div>
            <div className="campo">
              <label>Confirmar nova senha</label>
              <input type="password" value={confirmarSenha} onChange={(e) => setConfirmarSenha(e.target.value)} required />
            </div>
            <button className="btn btn-latao" style={{ width: '100%', justifyContent: 'center' }} disabled={carregando}>
              {carregando ? 'Salvando...' : 'Salvar nova senha'}
            </button>
          </form>
        )}

        <div className="auth-troca">
          <Link to="/login">Voltar para o login</Link>
        </div>
      </div>
    </div>
  );
}
