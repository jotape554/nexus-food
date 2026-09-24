import { useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../../api/http';
import AssinaturaNexus from '../../components/AssinaturaNexus';

export default function EsqueciSenha() {
  const [email, setEmail] = useState('');
  const [enviado, setEnviado] = useState(false);
  const [erro, setErro] = useState('');
  const [carregando, setCarregando] = useState(false);

  async function handleSubmit(e) {
    e.preventDefault();
    setErro('');
    setCarregando(true);
    try {
      await api.post('/auth/esqueci-senha', { email }, { autenticado: false });
      setEnviado(true);
    } catch (err) {
      setErro(err.message || 'Não foi possível enviar o e-mail.');
    } finally {
      setCarregando(false);
    }
  }

  return (
    <div className="auth-shell">
      <div className="auth-card">
        <h1>Esqueci minha senha</h1>
        <p className="sub">Enviamos um link pra você criar uma senha nova.</p>

        {erro && <div className="erro">{erro}</div>}

        {enviado ? (
          <div className="sucesso">
            Se esse e-mail estiver cadastrado, você vai receber um link pra redefinir a senha em instantes.
          </div>
        ) : (
          <form onSubmit={handleSubmit}>
            <div className="campo">
              <label>E-mail</label>
              <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
            </div>
            <button className="btn btn-latao" style={{ width: '100%', justifyContent: 'center' }} disabled={carregando}>
              {carregando ? 'Enviando...' : 'Enviar link'}
            </button>
          </form>
        )}

        <div className="auth-troca">
          <Link to="/login">Voltar para o login</Link>
        </div>
      </div>
      <AssinaturaNexus tom="escuro" />
    </div>
  );
}
