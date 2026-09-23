import { useEffect, useRef, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../api/http';
import { useAuth } from '../context/AuthContext';
import Modal from '../components/Modal';
import RecursoBloqueado from '../components/RecursoBloqueado';
import Icone, { ICONE_MODALIDADE, ICONE_PAGAMENTO } from '../components/Icone';
import {
  moeda, hora, telefone, numeroPedido, urgencia, minutosDesde,
  STATUS_LABEL, MODALIDADE_LABEL, PAGAMENTO_LABEL, MOTIVO_CANCELAMENTO_LABEL,
} from '../api/formato';

const INTERVALO_ATUALIZACAO_MS = 8000;

const COLUNAS = [
  { titulo: 'Novos', status: ['RECEBIDO'] },
  { titulo: 'Aceitos', status: ['CONFIRMADO'] },
  { titulo: 'Em preparo', status: ['EM_PREPARO'] },
  { titulo: 'Prontos / em entrega', status: ['PRONTO', 'SAIU_PARA_ENTREGA'] },
];

/** O próximo passo "natural" de cada status — o botão principal do cartão. */
function proximaAcao(pedido) {
  switch (pedido.status) {
    case 'RECEBIDO': return { status: 'CONFIRMADO', rotulo: 'Aceitar pedido' };
    case 'CONFIRMADO': return { status: 'EM_PREPARO', rotulo: 'Iniciar preparo' };
    case 'EM_PREPARO': return { status: 'PRONTO', rotulo: 'Marcar pronto' };
    case 'PRONTO':
      return pedido.modalidade === 'ENTREGA'
        ? { status: 'SAIU_PARA_ENTREGA', rotulo: 'Saiu para entrega' }
        : { status: 'CONCLUIDO', rotulo: 'Concluir' };
    case 'SAIU_PARA_ENTREGA': return { status: 'CONCLUIDO', rotulo: 'Entregue' };
    default: return null;
  }
}

/** Bipe curto gerado na hora (sem arquivo de áudio). O navegador só libera som depois de um clique. */
function tocarAlerta(contexto) {
  if (!contexto) return;
  const agora = contexto.currentTime;
  [0, 0.25].forEach((atraso) => {
    const osc = contexto.createOscillator();
    const ganho = contexto.createGain();
    osc.frequency.value = 880;
    ganho.gain.setValueAtTime(0.0001, agora + atraso);
    ganho.gain.exponentialRampToValueAtTime(0.4, agora + atraso + 0.02);
    ganho.gain.exponentialRampToValueAtTime(0.0001, agora + atraso + 0.2);
    osc.connect(ganho).connect(contexto.destination);
    osc.start(agora + atraso);
    osc.stop(agora + atraso + 0.22);
  });
}

const ROTULO_URGENCIA = { ok: 'No prazo', atencao: 'Atenção', atrasado: 'Atrasado' };
const MAX_ITENS_CARTAO = 8;

function CartaoPedido({ pedido, agora, onAvancar, onAbrir, ocupado }) {
  const acao = proximaAcao(pedido);
  const nivel = urgencia(pedido, agora);
  const referenciaTempo = pedido.status === 'SAIU_PARA_ENTREGA' && pedido.saiuParaEntregaEm ? pedido.saiuParaEntregaEm : pedido.criadoEm;

  return (
    <article className={`pedido-card urgencia-${nivel}`}>
      <button className="pedido-card-corpo" onClick={() => onAbrir(pedido.id)} aria-label={`Abrir pedido ${numeroPedido(pedido.numeroDia)}`}>
        <div className="pedido-card-topo">
          <strong>Pedido {numeroPedido(pedido.numeroDia)}</strong>
          <span className={`chip-tempo ${nivel}`} title={ROTULO_URGENCIA[nivel]}>
            <Icone nome="relogio" tamanho={13} /> {minutosDesde(referenciaTempo, agora)}
          </span>
        </div>

        <div className="pedido-card-linha destaque"><Icone nome="pessoa" /> {pedido.cliente.nome}</div>
        <div className="pedido-card-linha">
          <Icone nome={ICONE_MODALIDADE[pedido.modalidade]} /> {MODALIDADE_LABEL[pedido.modalidade]}
          {pedido.status === 'SAIU_PARA_ENTREGA' && <span className="tag">na rua</span>}
        </div>
        <div className="pedido-card-linha">
          <Icone nome={ICONE_PAGAMENTO[pedido.formaPagamento]} />
          <strong>{moeda(pedido.total)}</strong>
          <span className="suave">· {PAGAMENTO_LABEL[pedido.formaPagamento]}</span>
        </div>
        {pedido.trocoPara && (
          <div className="pedido-card-linha troco">Troco: {moeda(pedido.trocoPara - pedido.total)} · paga com {moeda(pedido.trocoPara)}</div>
        )}

        <ul className="pedido-card-itens">
          {pedido.itens.slice(0, MAX_ITENS_CARTAO).map((item, i) => (
            <li key={i}>
              <span className="qtd">{item.quantidade}×</span> {item.nomeProduto}
              {item.observacao && <div className="obs">{item.observacao}</div>}
            </li>
          ))}
          {pedido.itens.length > MAX_ITENS_CARTAO && <li className="suave">+ {pedido.itens.length - MAX_ITENS_CARTAO} itens</li>}
        </ul>

        {pedido.enderecoEntrega && (
          <div className="pedido-card-linha endereco">
            <Icone nome="local" />
            <span>{pedido.enderecoEntrega}{pedido.bairroEntrega ? ` · ${pedido.bairroEntrega}` : ''}</span>
          </div>
        )}
        {pedido.observacao && (
          <div className="pedido-card-linha obs-geral"><Icone nome="nota" /> <span>{pedido.observacao}</span></div>
        )}
      </button>
      {acao && (
        <button className="btn btn-latao pedido-card-acao" disabled={ocupado} onClick={() => onAvancar(pedido, acao.status)}>
          {acao.rotulo}
        </button>
      )}
    </article>
  );
}

function DetalhePedido({ id, onFechar, onAvancar, onCancelar }) {
  const { data: pedido, isLoading } = useQuery({
    queryKey: ['pedido', id],
    queryFn: () => api.get(`/api/pedidos/${id}`),
  });

  if (isLoading || !pedido) {
    return <Modal titulo="Pedido" onFechar={onFechar}><p>Carregando...</p></Modal>;
  }

  const acao = proximaAcao(pedido);
  const emAndamento = !['CONCLUIDO', 'CANCELADO'].includes(pedido.status);

  return (
    <Modal titulo={`Pedido ${numeroPedido(pedido.numeroDia)} · ${STATUS_LABEL[pedido.status]}`} onFechar={onFechar}>
      <div className="detalhe-grid">
        <div>
          <div className="rotulo">Cliente</div>
          <div>{pedido.cliente.nome}</div>
          <a href={`https://wa.me/${pedido.cliente.telefone}`} target="_blank" rel="noreferrer">{telefone(pedido.cliente.telefone)}</a>
        </div>
        <div>
          <div className="rotulo">{MODALIDADE_LABEL[pedido.modalidade]}</div>
          {pedido.enderecoEntrega && <div>{pedido.enderecoEntrega}</div>}
          {pedido.bairroEntrega && <div>{pedido.bairroEntrega}</div>}
          <div>Previsto para {hora(pedido.prontoPrevistoPara)}</div>
        </div>
      </div>

      <table className="tabela-itens">
        <tbody>
          {pedido.itens.map((item, i) => (
            <tr key={i}>
              <td>{item.quantidade}×</td>
              <td>
                {item.nomeProduto}
                {item.observacao && <div className="obs">Obs.: {item.observacao}</div>}
              </td>
              <td style={{ textAlign: 'right' }}>{moeda(item.subtotal)}</td>
            </tr>
          ))}
          {Number(pedido.taxaEntrega) > 0 && (
            <tr><td></td><td>Taxa de entrega</td><td style={{ textAlign: 'right' }}>{moeda(pedido.taxaEntrega)}</td></tr>
          )}
          <tr className="total"><td></td><td>Total</td><td style={{ textAlign: 'right' }}>{moeda(pedido.total)}</td></tr>
        </tbody>
      </table>

      <p style={{ margin: '12px 0' }}>
        <strong>Pagamento:</strong> {PAGAMENTO_LABEL[pedido.formaPagamento]}
        {pedido.trocoPara && <> · troco para {moeda(pedido.trocoPara)}</>}
      </p>
      {pedido.observacao && <p className="obs-pedido"><strong>Observação:</strong> {pedido.observacao}</p>}
      {pedido.motivoCancelamento && (
        <p className="erro" style={{ marginTop: 8 }}>Cancelado: {MOTIVO_CANCELAMENTO_LABEL[pedido.motivoCancelamento]}</p>
      )}

      <div className="rotulo" style={{ marginTop: 16 }}>Histórico</div>
      <ol className="linha-do-tempo">
        {pedido.eventos.map((e, i) => (
          <li key={i}>
            <span>{hora(e.ocorridoEm)}</span> {STATUS_LABEL[e.statusNovo]}
            {e.usuario && <span className="quem"> · {e.usuario}</span>}
          </li>
        ))}
      </ol>

      <div className="modal-acoes">
        {emAndamento && <button className="btn btn-perigo" onClick={() => onCancelar(pedido)}>Cancelar pedido</button>}
        {acao && <button className="btn btn-latao" onClick={() => onAvancar(pedido, acao.status)}>{acao.rotulo}</button>}
        {!acao && <button className="btn btn-secundario" onClick={onFechar}>Fechar</button>}
      </div>
    </Modal>
  );
}

function CancelarPedido({ pedido, onFechar, onConfirmar, erro }) {
  const [motivo, setMotivo] = useState('');
  return (
    <Modal titulo={`Cancelar pedido ${numeroPedido(pedido.numeroDia)}`} onFechar={onFechar}>
      {erro && <div className="erro">{erro}</div>}
      <div className="campo">
        <label>Motivo</label>
        <select value={motivo} onChange={(e) => setMotivo(e.target.value)} required>
          <option value="">Escolha o motivo</option>
          {Object.entries(MOTIVO_CANCELAMENTO_LABEL).map(([valor, rotulo]) => (
            <option key={valor} value={valor}>{rotulo}</option>
          ))}
        </select>
      </div>
      <div className="modal-acoes">
        <button className="btn btn-secundario" onClick={onFechar}>Voltar</button>
        <button className="btn btn-perigo" disabled={!motivo} onClick={() => onConfirmar(motivo)}>Cancelar pedido</button>
      </div>
    </Modal>
  );
}

function PedidosDoDia({ onAbrir }) {
  const { data: pedidos = [], isLoading } = useQuery({
    queryKey: ['pedidos-dia'],
    queryFn: () => api.get('/api/pedidos'),
    refetchInterval: INTERVALO_ATUALIZACAO_MS * 2,
  });

  const concluidos = pedidos.filter((p) => p.status === 'CONCLUIDO');
  const faturamento = concluidos.reduce((soma, p) => soma + Number(p.total), 0);

  return (
    <div className="panel">
      <div className="resumo-dia">
        <div><span>{pedidos.length}</span> pedidos hoje</div>
        <div><span>{concluidos.length}</span> concluídos</div>
        <div><span>{moeda(faturamento)}</span> vendidos</div>
      </div>
      {isLoading ? <p style={{ padding: 20 }}>Carregando...</p> : pedidos.length === 0 ? (
        <div className="estado-vazio">Nenhum pedido hoje ainda.</div>
      ) : (
        <table>
          <thead>
            <tr><th>#</th><th>Hora</th><th>Cliente</th><th>Modalidade</th><th>Total</th><th>Status</th></tr>
          </thead>
          <tbody>
            {pedidos.map((p) => (
              <tr key={p.id} onClick={() => onAbrir(p.id)} style={{ cursor: 'pointer' }}>
                <td>{numeroPedido(p.numeroDia)}</td>
                <td>{hora(p.criadoEm)}</td>
                <td>{p.cliente.nome}</td>
                <td>{MODALIDADE_LABEL[p.modalidade]}</td>
                <td>{moeda(p.total)}</td>
                <td><span className={`badge badge-pedido-${p.status.toLowerCase()}`}>{STATUS_LABEL[p.status]}</span></td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}

export default function Pedidos() {
  const { usuario } = useAuth();
  const queryClient = useQueryClient();
  const [aba, setAba] = useState('painel');
  const [abertoId, setAbertoId] = useState(null);
  const [cancelando, setCancelando] = useState(null);
  const [erro, setErro] = useState('');
  const [agora, setAgora] = useState(Date.now());
  const [somAtivo, setSomAtivo] = useState(false);
  const [copiado, setCopiado] = useState(false);
  const audioRef = useRef(null);
  const novosVistosRef = useRef(null);

  const { data: pedidos = [], isLoading, error } = useQuery({
    queryKey: ['pedidos-em-andamento'],
    queryFn: () => api.get('/api/pedidos/em-andamento'),
    refetchInterval: INTERVALO_ATUALIZACAO_MS,
    refetchIntervalInBackground: true,
  });

  const { data: restaurante } = useQuery({
    queryKey: ['restaurante'],
    queryFn: () => api.get('/api/restaurante'),
  });

  // Relógio da tela: atualiza o "há X min" dos cartões.
  useEffect(() => {
    const t = setInterval(() => setAgora(Date.now()), 15000);
    return () => clearInterval(t);
  }, []);

  // Alerta sonoro + título da aba quando chega pedido novo.
  useEffect(() => {
    const novos = pedidos.filter((p) => p.status === 'RECEBIDO');
    document.title = novos.length > 0 ? `(${novos.length}) Novos pedidos · Nexus Food` : 'Pedidos · Nexus Food';
    const ids = new Set(novos.map((p) => p.id));
    if (novosVistosRef.current && [...ids].some((id) => !novosVistosRef.current.has(id)) && somAtivo) {
      tocarAlerta(audioRef.current);
    }
    novosVistosRef.current = ids;
  }, [pedidos, somAtivo]);

  useEffect(() => () => { document.title = 'Nexus Food'; }, []);

  function atualizarListas() {
    queryClient.invalidateQueries({ queryKey: ['pedidos-em-andamento'] });
    queryClient.invalidateQueries({ queryKey: ['pedidos-dia'] });
  }

  const mudarStatus = useMutation({
    mutationFn: ({ id, status, motivoCancelamento }) =>
      api.patch(`/api/pedidos/${id}/status`, { status, motivoCancelamento }),
    onSuccess: (pedido) => {
      setErro('');
      queryClient.setQueryData(['pedido', pedido.id], pedido);
      atualizarListas();
    },
    onError: (e) => {
      setErro(e.message);
      atualizarListas();
    },
  });

  const alternarLoja = useMutation({
    mutationFn: (valor) => api.patch(`/api/restaurante/aceitando-pedidos?valor=${valor}`),
    onSuccess: (r) => queryClient.setQueryData(['restaurante'], r),
  });

  function ativarSom() {
    const Contexto = window.AudioContext || window.webkitAudioContext;
    if (!Contexto) return;
    audioRef.current = audioRef.current || new Contexto();
    audioRef.current.resume();
    tocarAlerta(audioRef.current);
    setSomAtivo(true);
  }

  function avancar(pedido, status) {
    mudarStatus.mutate({ id: pedido.id, status });
  }

  function confirmarCancelamento(motivo) {
    mudarStatus.mutate(
      { id: cancelando.id, status: 'CANCELADO', motivoCancelamento: motivo },
      { onSuccess: () => { setCancelando(null); setAbertoId(null); } },
    );
  }

  if (error?.status === 402 && error.dados?.upgradeNecessario) {
    return <RecursoBloqueado planoNecessario={error.dados.planoNecessario} mensagem={error.dados.mensagem} />;
  }

  // Em produção é o domínio público do sistema (VITE_URL_PUBLICA); em desenvolvimento, o endereço local.
  const baseLink = import.meta.env.VITE_URL_PUBLICA || window.location.origin;
  const linkCardapio = restaurante ? `${baseLink}/r/${restaurante.slug || usuario?.restauranteSlug}` : '';

  function copiarLink() {
    navigator.clipboard?.writeText(linkCardapio).then(() => {
      setCopiado(true);
      setTimeout(() => setCopiado(false), 2000);
    });
  }

  return (
    <div>
      <div className="pedidos-cabecalho">
        <div>
          <h2>Pedidos</h2>
          {linkCardapio && (
            <p className="link-cardapio">
              Seu cardápio: <a href={linkCardapio} target="_blank" rel="noreferrer">{linkCardapio.replace(/^https?:\/\//, '')}</a>
              <button className="btn-link" onClick={copiarLink}>{copiado ? 'Copiado ✓' : 'Copiar'}</button>
            </p>
          )}
        </div>
        <div className="toolbar-pedidos">
          {restaurante && (
            <button
              className={`btn ${restaurante.aceitandoPedidos ? 'btn-aberto' : 'btn-fechado'}`}
              onClick={() => alternarLoja.mutate(!restaurante.aceitandoPedidos)}
              disabled={alternarLoja.isPending}
              title={restaurante.aceitandoPedidos ? 'Clique para fechar a loja' : 'Clique para abrir a loja'}
            >
              <span className="bolinha" /> {restaurante.aceitandoPedidos ? 'Loja aberta' : 'Loja fechada'}
            </button>
          )}
          <button
            className="btn btn-secundario btn-icone"
            onClick={somAtivo ? () => setSomAtivo(false) : ativarSom}
            title={somAtivo ? 'Som de pedido novo ligado (clique para desligar)' : 'Ligar som de pedido novo'}
          >
            <Icone nome={somAtivo ? 'som' : 'semSom'} tamanho={18} /> {somAtivo ? 'Som ligado' : 'Ligar som'}
          </button>
        </div>
      </div>

      {restaurante && !restaurante.aceitandoPedidos && (
        <div className="aviso-trial">
          <span>A loja está fechada: o cardápio aparece, mas ninguém consegue pedir.</span>
        </div>
      )}

      <div className="abas">
        <button className={aba === 'painel' ? 'ativa' : ''} onClick={() => setAba('painel')}>Em andamento ({pedidos.length})</button>
        <button className={aba === 'dia' ? 'ativa' : ''} onClick={() => setAba('dia')}>Todos de hoje</button>
        {aba === 'painel' && (
          <div className="legenda-urgencia" aria-label="Legenda de tempo">
            <span><i className="ok" /> no prazo</span>
            <span><i className="atencao" /> atenção</span>
            <span><i className="atrasado" /> atrasado</span>
          </div>
        )}
      </div>

      {erro && <div className="erro">{erro}</div>}

      {aba === 'painel' ? (
        isLoading ? <p>Carregando...</p> : (
          <div className="quadro-pedidos">
            {COLUNAS.map((coluna) => {
              const daColuna = pedidos.filter((p) => coluna.status.includes(p.status));
              return (
                <section key={coluna.titulo} className="coluna-pedidos">
                  <h3>{coluna.titulo} <span>{daColuna.length}</span></h3>
                  {daColuna.length === 0 && <div className="coluna-vazia">—</div>}
                  {daColuna.map((p) => (
                    <CartaoPedido
                      key={p.id}
                      pedido={p}
                      agora={agora}
                      onAvancar={avancar}
                      onAbrir={setAbertoId}
                      ocupado={mudarStatus.isPending}
                    />
                  ))}
                </section>
              );
            })}
          </div>
        )
      ) : (
        <PedidosDoDia onAbrir={setAbertoId} />
      )}

      {abertoId && !cancelando && (
        <DetalhePedido
          id={abertoId}
          onFechar={() => setAbertoId(null)}
          onAvancar={avancar}
          onCancelar={(p) => { setErro(''); setCancelando(p); }}
        />
      )}
      {cancelando && (
        <CancelarPedido
          pedido={cancelando}
          erro={mudarStatus.isError ? mudarStatus.error.message : ''}
          onFechar={() => setCancelando(null)}
          onConfirmar={confirmarCancelamento}
        />
      )}
    </div>
  );
}
