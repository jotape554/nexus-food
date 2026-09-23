import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../api/http';
import { moeda } from '../api/formato';
import EstadoVazio from '../components/EstadoVazio';

function paraFormulario(r) {
  return {
    nome: r.nome || '',
    telefone: r.telefone || '',
    endereco: r.endereco || '',
    logoUrl: r.logoUrl || '',
    fusoHorario: r.fusoHorario,
    horaViradaDia: (r.horaViradaDia || '04:00').slice(0, 5),
    aceitaRetirada: r.aceitaRetirada,
    aceitaEntrega: r.aceitaEntrega,
    aceitaConsumoLocal: r.aceitaConsumoLocal,
    tipoTaxaEntrega: r.tipoTaxaEntrega,
    taxaEntregaFixa: r.taxaEntregaFixa,
    pedidoMinimo: r.pedidoMinimo,
    tempoPreparoEstimadoMin: r.tempoPreparoEstimadoMin,
  };
}

function Bairros() {
  const queryClient = useQueryClient();
  const { data: bairros = [] } = useQuery({ queryKey: ['bairros'], queryFn: () => api.get('/api/restaurante/bairros') });
  const [novo, setNovo] = useState({ nome: '', taxa: '' });
  const [erro, setErro] = useState('');

  const recarregar = () => queryClient.invalidateQueries({ queryKey: ['bairros'] });

  async function adicionar(e) {
    e.preventDefault();
    setErro('');
    try {
      await api.post('/api/restaurante/bairros', { nome: novo.nome, taxa: Number(novo.taxa), ativo: true });
      setNovo({ nome: '', taxa: '' });
      recarregar();
    } catch (err) {
      setErro(err.message);
    }
  }

  async function alternar(b) {
    await api.put(`/api/restaurante/bairros/${b.id}`, { nome: b.nome, taxa: b.taxa, ativo: !b.ativo });
    recarregar();
  }

  async function excluir(b) {
    if (!confirm(`Excluir o bairro "${b.nome}"?`)) return;
    await api.delete(`/api/restaurante/bairros/${b.id}`);
    recarregar();
  }

  return (
    <div className="panel" style={{ marginTop: 20, padding: 24 }}>
      <h3>Bairros atendidos</h3>
      <p style={{ color: 'var(--texto-suave)', marginTop: 0 }}>O cliente escolhe o bairro no pedido. Bairro fora da lista não recebe entrega.</p>
      {erro && <div className="erro">{erro}</div>}
      <form onSubmit={adicionar} className="form-inline">
        <input placeholder="Nome do bairro" value={novo.nome} onChange={(e) => setNovo({ ...novo, nome: e.target.value })} required />
        <input type="number" step="0.01" min="0" placeholder="Taxa (R$)" value={novo.taxa} onChange={(e) => setNovo({ ...novo, taxa: e.target.value })} required />
        <button className="btn btn-latao">Adicionar</button>
      </form>
      {bairros.length === 0 ? <EstadoVazio mensagem="Nenhum bairro cadastrado." /> : (
        <table>
          <tbody>
            {bairros.map((b) => (
              <tr key={b.id}>
                <td>{b.nome}</td>
                <td>{moeda(b.taxa)}</td>
                <td>
                  <button className={`badge badge-botao ${b.ativo ? 'badge-pedido-concluido' : 'badge-pedido-cancelado'}`} onClick={() => alternar(b)}>
                    {b.ativo ? 'Atendido' : 'Pausado'}
                  </button>
                </td>
                <td style={{ textAlign: 'right' }}><button className="btn-link perigo" onClick={() => excluir(b)}>Excluir</button></td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}

export default function Configuracoes() {
  const queryClient = useQueryClient();
  const { data: restaurante, isLoading } = useQuery({ queryKey: ['restaurante'], queryFn: () => api.get('/api/restaurante') });
  const [form, setForm] = useState(null);
  const [mensagem, setMensagem] = useState('');
  const [erro, setErro] = useState('');

  useEffect(() => {
    if (restaurante && !form) setForm(paraFormulario(restaurante));
  }, [restaurante]);

  const salvar = useMutation({
    mutationFn: (dados) => api.put('/api/restaurante', dados),
    onSuccess: (r) => {
      queryClient.setQueryData(['restaurante'], r);
      setForm(paraFormulario(r));
      setErro('');
      setMensagem('Configurações salvas.');
    },
    onError: (e) => { setMensagem(''); setErro(e.message); },
  });

  if (isLoading || !form) return <p>Carregando...</p>;

  const set = (campo) => (e) => setForm({ ...form, [campo]: e.target.type === 'checkbox' ? e.target.checked : e.target.value });

  function enviar(e) {
    e.preventDefault();
    setMensagem('');
    salvar.mutate({
      ...form,
      taxaEntregaFixa: Number(form.taxaEntregaFixa) || 0,
      pedidoMinimo: Number(form.pedidoMinimo) || 0,
      tempoPreparoEstimadoMin: Number(form.tempoPreparoEstimadoMin),
    });
  }

  return (
    <div>
      <div className="page-header">
        <div>
          <h2>Configurações</h2>
          <p>Como o seu restaurante atende e cobra. Vale para os próximos pedidos.</p>
        </div>
      </div>

      <form onSubmit={enviar} className="panel" style={{ padding: 24 }}>
        {erro && <div className="erro">{erro}</div>}
        {mensagem && <div className="sucesso">{mensagem}</div>}

        <h3>Restaurante</h3>
        <div className="form-grid">
          <div className="campo"><label>Nome</label><input value={form.nome} onChange={set('nome')} required /></div>
          <div className="campo"><label>Telefone / WhatsApp</label><input value={form.telefone} onChange={set('telefone')} /></div>
        </div>
        <div className="campo"><label>Endereço</label><input value={form.endereco} onChange={set('endereco')} /></div>
        <div className="campo"><label>Endereço do logo (opcional)</label><input value={form.logoUrl} onChange={set('logoUrl')} placeholder="https://..." /></div>

        <h3 style={{ marginTop: 24 }}>Como você atende</h3>
        <div className="checks">
          <label><input type="checkbox" checked={form.aceitaRetirada} onChange={set('aceitaRetirada')} /> Retirada no balcão</label>
          <label><input type="checkbox" checked={form.aceitaEntrega} onChange={set('aceitaEntrega')} /> Entrega</label>
          <label><input type="checkbox" checked={form.aceitaConsumoLocal} onChange={set('aceitaConsumoLocal')} /> Consumo no local</label>
        </div>

        {form.aceitaEntrega && (
          <>
            <h3 style={{ marginTop: 24 }}>Taxa de entrega</h3>
            <div className="checks">
              <label><input type="radio" name="taxa" checked={form.tipoTaxaEntrega === 'FIXA'} onChange={() => setForm({ ...form, tipoTaxaEntrega: 'FIXA' })} /> Mesmo valor para todos</label>
              <label><input type="radio" name="taxa" checked={form.tipoTaxaEntrega === 'POR_BAIRRO'} onChange={() => setForm({ ...form, tipoTaxaEntrega: 'POR_BAIRRO' })} /> Valor por bairro</label>
            </div>
            {form.tipoTaxaEntrega === 'FIXA' && (
              <div className="campo" style={{ maxWidth: 220 }}>
                <label>Taxa de entrega (R$)</label>
                <input type="number" step="0.01" min="0" value={form.taxaEntregaFixa} onChange={set('taxaEntregaFixa')} />
              </div>
            )}
          </>
        )}

        <h3 style={{ marginTop: 24 }}>Pedidos</h3>
        <div className="form-grid">
          <div className="campo">
            <label>Pedido mínimo (R$, sem a taxa)</label>
            <input type="number" step="0.01" min="0" value={form.pedidoMinimo} onChange={set('pedidoMinimo')} />
          </div>
          <div className="campo">
            <label>Tempo estimado de preparo (min)</label>
            <input type="number" min="1" max="300" value={form.tempoPreparoEstimadoMin} onChange={set('tempoPreparoEstimadoMin')} required />
          </div>
        </div>

        <h3 style={{ marginTop: 24 }}>Horário</h3>
        <div className="form-grid">
          <div className="campo">
            <label>Fuso horário</label>
            <select value={form.fusoHorario} onChange={set('fusoHorario')}>
              <option value="America/Sao_Paulo">Brasília (SP, RJ, MG, Sul, NE…)</option>
              <option value="America/Manaus">Amazonas</option>
              <option value="America/Cuiaba">Mato Grosso / MS</option>
              <option value="America/Belem">Pará / Amapá</option>
              <option value="America/Fortaleza">Ceará / RN / PB / PI / MA</option>
              <option value="America/Recife">Pernambuco</option>
              <option value="America/Bahia">Bahia</option>
              <option value="America/Porto_Velho">Rondônia</option>
              <option value="America/Boa_Vista">Roraima</option>
              <option value="America/Rio_Branco">Acre</option>
              <option value="America/Noronha">Fernando de Noronha</option>
            </select>
          </div>
          <div className="campo">
            <label>O dia vira às</label>
            <input type="time" value={form.horaViradaDia} onChange={set('horaViradaDia')} required />
            <small>Pedidos antes desse horário contam no dia anterior (útil se você fecha de madrugada).</small>
          </div>
        </div>

        <div className="modal-acoes">
          <button className="btn btn-latao" disabled={salvar.isPending}>{salvar.isPending ? 'Salvando...' : 'Salvar'}</button>
        </div>
      </form>

      {restaurante.aceitaEntrega && restaurante.tipoTaxaEntrega === 'POR_BAIRRO' && <Bairros />}
    </div>
  );
}
