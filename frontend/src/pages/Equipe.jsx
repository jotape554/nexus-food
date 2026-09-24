import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../api/http';
import Modal from '../components/Modal';
import Icone from '../components/Icone';
import RecursoBloqueado from '../components/RecursoBloqueado';
import { useConfirmacao } from '../components/Confirmacao';
import { NOME_PLANO } from '../plano';

const PERFIS = [
  { valor: 'ATENDENTE', nome: 'Atendente', faz: 'Pedidos, cardápio (marcar esgotado) e clientes.' },
  { valor: 'GERENTE', nome: 'Gerente', faz: 'Tudo do atendente, mais relatórios, Nexus Score, cardápio completo e configurações da loja.' },
  { valor: 'ADMINISTRADOR', nome: 'Administrador', faz: 'Tudo, inclusive a equipe e a assinatura.' },
];
const NOME_PERFIL = Object.fromEntries(PERFIS.map((p) => [p.valor, p.nome]));

function EscolhaPerfil({ valor, onChange, bloqueado }) {
  return (
    <fieldset className="escolha-perfil" disabled={bloqueado}>
      <legend>Perfil</legend>
      {PERFIS.map((p) => (
        <label key={p.valor} className={`opcao-perfil ${valor === p.valor ? 'marcada' : ''}`}>
          <input type="radio" name="perfil" value={p.valor} checked={valor === p.valor} onChange={() => onChange(p.valor)} />
          <span>
            <strong>{p.nome}</strong>
            <span className="suave">{p.faz}</span>
          </span>
        </label>
      ))}
    </fieldset>
  );
}

/** Depois de convidar (ou gerar um link novo): o link para a pessoa criar a senha. */
function LinkGerado({ convite, onFechar }) {
  const [copiado, setCopiado] = useState(false);
  const { usuario, link } = convite;
  const validade = new Date(convite.expiraEm).toLocaleString('pt-BR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' });
  const texto = `Olá, ${usuario.nome.split(' ')[0]}! Crie sua senha para entrar no painel do restaurante: ${link}`;

  function copiar() {
    if (!navigator.clipboard) return;
    navigator.clipboard.writeText(link)
      .then(() => { setCopiado(true); setTimeout(() => setCopiado(false), 2000); })
      .catch(() => { /* o link continua visível para copiar à mão */ });
  }

  return (
    <Modal titulo={usuario.convitePendente ? 'Convite criado' : 'Link para nova senha'} onFechar={onFechar}>
      <p className="modal-texto">
        {usuario.convitePendente
          ? <>Mandamos o convite para <strong>{usuario.email}</strong>. Se preferir, envie o link abaixo direto para {usuario.nome.split(' ')[0]}.</>
          : <>Envie este link para <strong>{usuario.nome}</strong> criar uma senha nova. A senha antiga continua valendo até lá.</>}
      </p>
      <div className="link-convite">
        <code>{link}</code>
      </div>
      <p className="suave" style={{ fontSize: '0.85rem', margin: '6px 0 0' }}>Vale até {validade}. Só funciona uma vez.</p>
      <div className="modal-acoes">
        <a className="btn btn-secundario" href={`https://wa.me/?text=${encodeURIComponent(texto)}`} target="_blank" rel="noreferrer">Enviar pelo WhatsApp</a>
        <button type="button" className="btn btn-latao" onClick={copiar}>{copiado ? 'Copiado!' : 'Copiar link'}</button>
      </div>
      <button type="button" className="btn-link" onClick={onFechar} style={{ marginTop: 12 }}>Fechar</button>
    </Modal>
  );
}

function FormUsuario({ inicial, onSalvar, onFechar }) {
  const editando = !!inicial;
  const [nome, setNome] = useState(inicial?.nome || '');
  const [email, setEmail] = useState('');
  const [papel, setPapel] = useState(inicial?.papel || 'ATENDENTE');
  const [erro, setErro] = useState(null);
  const [salvando, setSalvando] = useState(false);

  async function enviar(e) {
    e.preventDefault();
    setErro(null);
    setSalvando(true);
    try {
      await onSalvar({ nome: nome.trim(), email: email.trim(), papel });
    } catch (err) {
      setErro(err);
      setSalvando(false);
    }
  }

  return (
    <Modal titulo={editando ? `Editar ${inicial.nome}` : 'Adicionar pessoa'} onFechar={onFechar}>
      <form onSubmit={enviar}>
        {erro && (erro.status === 402 && erro.dados?.upgradeNecessario ? (
          <div className="erro">{erro.message} <Link to="/painel/assinatura">Ver planos</Link></div>
        ) : <div className="erro" role="alert">{erro.message}</div>)}
        <div className="campo">
          <label htmlFor="usuario-nome">Nome</label>
          <input id="usuario-nome" value={nome} onChange={(e) => setNome(e.target.value)} required maxLength={120} autoFocus />
        </div>
        {editando ? (
          <div className="campo">
            <label>E-mail</label>
            <p className="suave" style={{ margin: 0 }}>{inicial.email}</p>
          </div>
        ) : (
          <div className="campo">
            <label htmlFor="usuario-email">E-mail</label>
            <input id="usuario-email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} required autoComplete="off" />
            <p className="dica-campo">É com ele que a pessoa entra. O convite chega nesse endereço.</p>
          </div>
        )}
        <EscolhaPerfil valor={papel} onChange={setPapel} bloqueado={inicial?.voce} />
        {inicial?.voce && <p className="dica-campo">Você não pode mudar o seu próprio perfil.</p>}
        <div className="modal-acoes">
          <button type="button" className="btn btn-secundario" onClick={onFechar}>Cancelar</button>
          <button className="btn btn-latao" disabled={salvando}>
            {salvando ? 'Salvando...' : editando ? 'Salvar' : 'Criar convite'}
          </button>
        </div>
      </form>
    </Modal>
  );
}

function Situacao({ u }) {
  if (!u.ativo) return <span className="badge badge-inativo">Desativado</span>;
  if (u.foraDoLimite) return <span className="badge badge-cancelado" title="Acima do limite de usuários do plano">Sem acesso pelo plano</span>;
  if (u.convitePendente) return <span className="badge badge-pendente">Convite pendente</span>;
  return <span className="badge badge-confirmado">Ativo</span>;
}

export default function Equipe() {
  const queryClient = useQueryClient();
  const [confirmar, confirmacao] = useConfirmacao();
  const [form, setForm] = useState(null); // null | 'novo' | usuário
  const [convite, setConvite] = useState(null);
  const [erroAcao, setErroAcao] = useState('');

  const { data, isLoading, error } = useQuery({ queryKey: ['equipe'], queryFn: () => api.get('/api/usuarios') });

  function atualizar() {
    queryClient.invalidateQueries({ queryKey: ['equipe'] });
    queryClient.invalidateQueries({ queryKey: ['assinatura'] });
  }

  async function salvar(dados) {
    if (form === 'novo') {
      const criado = await api.post('/api/usuarios', dados);
      setForm(null);
      setConvite(criado);
    } else {
      await api.put(`/api/usuarios/${form.id}`, { nome: dados.nome, papel: dados.papel });
      setForm(null);
    }
    atualizar();
  }

  async function alternarAtivo(u) {
    setErroAcao('');
    if (u.ativo) {
      const ok = await confirmar({
        titulo: `Desativar ${u.nome}?`,
        mensagem: 'A pessoa perde o acesso na hora, inclusive se estiver com o painel aberto. O histórico de pedidos continua igual e dá para reativar depois.',
        acao: 'Desativar',
      });
      if (!ok) return;
    }
    try {
      await api.patch(`/api/usuarios/${u.id}/ativo?valor=${!u.ativo}`);
      atualizar();
    } catch (e) {
      setErroAcao(e.message);
    }
  }

  async function novoLink(u) {
    setErroAcao('');
    try {
      setConvite(await api.post(`/api/usuarios/${u.id}/link`));
    } catch (e) {
      setErroAcao(e.message);
    }
  }

  if (error?.status === 402 && error.dados?.upgradeNecessario) {
    return <RecursoBloqueado planoNecessario={error.dados.planoNecessario} mensagem={error.dados.mensagem} />;
  }

  const cheio = data && data.limiteUsuarios != null && data.usuariosAtivos >= data.limiteUsuarios;
  const acimaDoLimite = data && data.limiteUsuarios != null && data.usuariosAtivos > data.limiteUsuarios;

  return (
    <div className="equipe">
      <div className="page-header">
        <div>
          <h2>Equipe</h2>
          <p>Quem usa o painel do restaurante. Cada pessoa entra com o próprio e-mail e senha.</p>
        </div>
        <button className="btn btn-latao" onClick={() => setForm('novo')} disabled={!data || cheio}>
          Adicionar pessoa
        </button>
      </div>

      {data && (
        <section className={`panel equipe-limite ${cheio ? 'cheio' : ''}`}>
          <div>
            <strong>
              {data.limiteUsuarios == null
                ? `${data.usuariosAtivos} ${data.usuariosAtivos === 1 ? 'usuário ativo' : 'usuários ativos'}`
                : `${data.usuariosAtivos} de ${data.limiteUsuarios} usuários ativos`}
            </strong>
            <span className="suave">
              {data.planoEfetivo && ` · Plano ${NOME_PLANO[data.planoEfetivo]}`}
              {data.limiteUsuarios == null && ' · sem limite de usuários'}
            </span>
          </div>
          {data.limiteUsuarios != null && (
            <div className="medidor"><span style={{ width: `${Math.min(100, (data.usuariosAtivos / data.limiteUsuarios) * 100)}%` }} /></div>
          )}
          {acimaDoLimite ? (
            <p className="equipe-limite-aviso">
              A equipe passou do limite do plano: quem aparece como “Sem acesso pelo plano” não consegue entrar.
              Desative alguém ou <Link to="/painel/assinatura">mude de plano</Link>.
            </p>
          ) : cheio && (
            <p className="equipe-limite-aviso">
              Limite do plano atingido. Desativados não contam: desative alguém para abrir vaga
              {data.proximoPlano && <> ou <Link to="/painel/assinatura">mude para o {NOME_PLANO[data.proximoPlano]}</Link></>}.
            </p>
          )}
        </section>
      )}

      {erroAcao && <div className="erro" role="alert">{erroAcao}</div>}

      <div className="panel">
        {isLoading ? <p style={{ padding: 20 }}>Carregando...</p> : error ? (
          <div className="erro" role="alert" style={{ margin: 16 }}>{error.message}</div>
        ) : (
          <ul className="lista-equipe">
            {[...data.usuarios].sort((a, b) => b.voce - a.voce).map((u) => (
              <li key={u.id} className={u.ativo ? '' : 'inativo'}>
                <div className="equipe-pessoa">
                  <span className="avatar avatar-claro">{u.nome.trim().split(/\s+/).slice(0, 2).map((p) => p[0]).join('').toUpperCase()}</span>
                  <div>
                    <div className="equipe-nome">{u.nome} {u.voce && <span className="tag-voce">você</span>}</div>
                    <div className="suave equipe-email">{u.email}</div>
                  </div>
                </div>
                <div className="equipe-perfil">{NOME_PERFIL[u.papel]}</div>
                <div className="equipe-situacao"><Situacao u={u} /></div>
                <div className="equipe-acoes">
                  <button type="button" className="btn-link" onClick={() => setForm(u)}>Editar</button>
                  {!u.voce && u.ativo && (
                    <button type="button" className="btn-link" onClick={() => novoLink(u)}>
                      {u.convitePendente ? 'Reenviar convite' : 'Link de nova senha'}
                    </button>
                  )}
                  {!u.voce && (
                    <button type="button" className={`btn-link ${u.ativo ? 'perigo' : ''}`} onClick={() => alternarAtivo(u)}>
                      {u.ativo ? 'Desativar' : 'Reativar'}
                    </button>
                  )}
                </div>
              </li>
            ))}
          </ul>
        )}
      </div>

      <section className="equipe-perfis">
        <h3>O que cada perfil faz</h3>
        <dl>
          {PERFIS.map((p) => (
            <div key={p.valor}><dt>{p.nome}</dt><dd>{p.faz}</dd></div>
          ))}
        </dl>
      </section>

      {form && <FormUsuario inicial={form === 'novo' ? null : form} onSalvar={salvar} onFechar={() => setForm(null)} />}
      {convite && <LinkGerado convite={convite} onFechar={() => setConvite(null)} />}
      {confirmacao}
    </div>
  );
}
