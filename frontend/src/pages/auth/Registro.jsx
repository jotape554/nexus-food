import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import AssinaturaNexus from '../../components/AssinaturaNexus';

export default function Registro() {
  const { registrar } = useAuth();
  const navigate = useNavigate();
  const [form, setForm] = useState({ nomeRestaurante: '', nomeAdmin: '', email: '', cpf: '', senha: '' });
  const [erro, setErro] = useState('');
  const [carregando, setCarregando] = useState(false);

  function atualizar(campo, valor) {
    setForm((f) => ({ ...f, [campo]: valor }));
  }

  function formatarCpf(valor) {
    const digitos = valor.replace(/\D/g, '').slice(0, 11);
    return digitos
      .replace(/(\d{3})(\d)/, '$1.$2')
      .replace(/(\d{3})(\d)/, '$1.$2')
      .replace(/(\d{3})(\d{1,2})$/, '$1-$2');
  }

  async function handleSubmit(e) {
    e.preventDefault();
    setErro('');
    setCarregando(true);
    try {
      await registrar(form);
      navigate('/painel/configuracoes');
    } catch (err) {
      setErro(err.message || 'Não foi possível criar a conta.');
    } finally {
      setCarregando(false);
    }
  }

  return (
    <div className="auth-shell">
      <div className="auth-card">
        <h1>Criar restaurante</h1>
        <p className="sub">Sua conta e seu painel ficam prontos em um passo.</p>

        {erro && <div className="erro">{erro}</div>}

        <form onSubmit={handleSubmit}>
          <div className="campo">
            <label>Nome do restaurante</label>
            <input value={form.nomeRestaurante} onChange={(e) => atualizar('nomeRestaurante', e.target.value)} required />
          </div>
          <div className="campo">
            <label>Seu nome</label>
            <input value={form.nomeAdmin} onChange={(e) => atualizar('nomeAdmin', e.target.value)} required />
          </div>
          <div className="campo">
            <label>E-mail</label>
            <input type="email" value={form.email} onChange={(e) => atualizar('email', e.target.value)} required />
          </div>
          <div className="campo">
            <label>CPF</label>
            <input
              value={form.cpf}
              onChange={(e) => atualizar('cpf', formatarCpf(e.target.value))}
              placeholder="000.000.000-00"
              inputMode="numeric"
              required
            />
          </div>
          <div className="campo">
            <label>Senha</label>
            <input type="password" value={form.senha} onChange={(e) => atualizar('senha', e.target.value)} required />
          </div>
          <button className="btn btn-latao" style={{ width: '100%', justifyContent: 'center' }} disabled={carregando}>
            {carregando ? 'Criando...' : 'Criar restaurante'}
          </button>
        </form>


        <div className="auth-troca">
          Já tem conta? <Link to="/login">Entrar</Link>
        </div>
      </div>
      <AssinaturaNexus />
    </div>
  );
}
