import { useEffect, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { api } from '../../api/http';
import AssinaturaNexus from '../../components/AssinaturaNexus';

export default function RedefinirSenha() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const token = searchParams.get('token') || '';

  const [novaSenha, setNovaSenha] = useState('');
  const [confirmarSenha, setConfirmarSenha] = useState('');
  const [erro, setErro] = useState('');
  const [concluido, setConcluido] = useState(false);
  const [carregando, setCarregando] = useState(false);
  // Quem está criando a senha: convite para a equipe (primeiro acesso) ou troca de senha.
  const [convite, setConvite] = useState(null);
  const [linkInvalido, setLinkInvalido] = useState(false);

  useEffect(() => {
    if (!token) return;
    api.publica.get(`/auth/convite?token=${encodeURIComponent(token)}`)
      .then(setConvite)
      .catch((e) => { if (e.status === 400) setLinkInvalido(true); });
  }, [token]);

  async function handleSubmit(e) {
    e.preventDefault();
    setErro('');

    if (novaSenha.length < 6) {
      setErro('Use pelo menos 6 caracteres.');
      return;
    }
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

  if (!token || linkInvalido) {
    return (
      <div className="auth-shell">
        <div className="auth-card">
          <h1>Link inválido</h1>
          <p className="sub">
            {linkInvalido
              ? 'Esse link já foi usado ou venceu. Peça um novo ao administrador do restaurante, ou use "Esqueci minha senha".'
              : 'Esse link de redefinição de senha está incompleto.'}
          </p>
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
        <h1>{convite?.convite ? 'Criar sua senha' : 'Criar nova senha'}</h1>
        <p className="sub">
          {convite?.convite
            ? <>Olá, {convite.nome.split(' ')[0]}! Você faz parte da equipe do restaurante <strong>{convite.restaurante}</strong>. Crie sua senha para entrar com <strong>{convite.email}</strong>.</>
            : convite
              ? <>Escolha uma senha nova para <strong>{convite.email}</strong>.</>
              : 'Escolha uma senha nova para sua conta.'}
        </p>

        {erro && <div className="erro">{erro}</div>}

        {concluido ? (
          <div className="sucesso">{convite?.convite ? 'Senha criada!' : 'Senha alterada!'} Redirecionando para o login...</div>
        ) : (
          <form onSubmit={handleSubmit}>
            <div className="campo">
              <label>Nova senha</label>
              <input type="password" value={novaSenha} onChange={(e) => setNovaSenha(e.target.value)} required minLength={6} autoComplete="new-password" />
            </div>
            <div className="campo">
              <label>Confirmar nova senha</label>
              <input type="password" value={confirmarSenha} onChange={(e) => setConfirmarSenha(e.target.value)} required autoComplete="new-password" />
            </div>
            <button className="btn btn-latao" style={{ width: '100%', justifyContent: 'center' }} disabled={carregando}>
              {carregando ? 'Salvando...' : convite?.convite ? 'Criar senha' : 'Salvar nova senha'}
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
