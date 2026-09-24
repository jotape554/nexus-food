import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import AssinaturaNexus from '../../components/AssinaturaNexus';

export default function Login() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [senha, setSenha] = useState('');
  const [erro, setErro] = useState('');
  const [carregando, setCarregando] = useState(false);

  async function handleSubmit(e) {
    e.preventDefault();
    setErro('');
    setCarregando(true);
    try {
      await login(email, senha);
      navigate('/painel/pedidos');
    } catch (err) {
      setErro(err.message || 'Não foi possível entrar.');
    } finally {
      setCarregando(false);
    }
  }

  return (
    <div className="auth-shell">
      <div className="auth-card">
        <h1>Entrar</h1>
        <p className="sub">Acesse o painel do seu restaurante.</p>

        {erro && <div className="erro">{erro}</div>}

        <form onSubmit={handleSubmit}>
          <div className="campo">
            <label>E-mail</label>
            <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
          </div>
          <div className="campo">
            <label>Senha</label>
            <input type="password" value={senha} onChange={(e) => setSenha(e.target.value)} required />
          </div>
          <div style={{ textAlign: 'right', marginTop: -8, marginBottom: 20 }}>
            <Link to="/esqueci-senha" style={{ fontSize: '0.85rem', color: 'var(--latao-forte)' }}>Esqueci minha senha</Link>
          </div>
          <button className="btn btn-latao" style={{ width: '100%', justifyContent: 'center' }} disabled={carregando}>
            {carregando ? 'Entrando...' : 'Entrar'}
          </button>
        </form>

        <div className="auth-troca">
          Ainda não tem conta? <Link to="/registro">Criar restaurante</Link>
        </div>
      </div>
      <AssinaturaNexus tom="escuro" />
    </div>
  );
}
