import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../api/http';
import { useAuth } from '../context/AuthContext';
import { moeda } from '../api/formato';
import Modal from '../components/Modal';
import EstadoVazio from '../components/EstadoVazio';

const PRODUTO_VAZIO = { categoriaId: '', nome: '', descricao: '', preco: '', imagemUrl: '', disponivel: true, ordem: 0 };
const CATEGORIA_VAZIA = { nome: '', ordem: 0, ativa: true };

export default function Cardapio() {
  const { usuario } = useAuth();
  const podeEditar = usuario?.papel !== 'ATENDENTE';
  const queryClient = useQueryClient();

  const [modal, setModal] = useState(null); // { tipo: 'produto'|'categoria', editando }
  const [form, setForm] = useState({});
  const [erro, setErro] = useState('');

  const categoriasQuery = useQuery({ queryKey: ['categorias'], queryFn: () => api.get('/api/categorias') });
  const produtosQuery = useQuery({ queryKey: ['produtos'], queryFn: () => api.get('/api/produtos') });
  const categorias = categoriasQuery.data || [];
  const produtos = produtosQuery.data || [];

  function recarregar() {
    queryClient.invalidateQueries({ queryKey: ['categorias'] });
    queryClient.invalidateQueries({ queryKey: ['produtos'] });
  }

  const salvar = useMutation({
    mutationFn: ({ tipo, editando, dados }) => {
      const base = tipo === 'produto' ? '/api/produtos' : '/api/categorias';
      return editando ? api.put(`${base}/${editando.id}`, dados) : api.post(base, dados);
    },
    onSuccess: () => { setModal(null); recarregar(); },
    onError: (e) => setErro(e.message),
  });

  const alternarDisponivel = useMutation({
    mutationFn: (p) => api.patch(`/api/produtos/${p.id}/disponivel?valor=${!p.disponivel}`),
    onSuccess: recarregar,
  });

  function abrirProduto(produto, categoriaId) {
    setErro('');
    setForm(produto
      ? { ...PRODUTO_VAZIO, ...produto, descricao: produto.descricao || '', imagemUrl: produto.imagemUrl || '' }
      : { ...PRODUTO_VAZIO, categoriaId: categoriaId || categorias[0]?.id || '' });
    setModal({ tipo: 'produto', editando: produto });
  }

  function abrirCategoria(categoria) {
    setErro('');
    setForm(categoria ? { nome: categoria.nome, ordem: categoria.ordem, ativa: categoria.ativa } : CATEGORIA_VAZIA);
    setModal({ tipo: 'categoria', editando: categoria });
  }

  function enviar(e) {
    e.preventDefault();
    setErro('');
    const dados = modal.tipo === 'produto'
      ? { ...form, categoriaId: Number(form.categoriaId), preco: Number(form.preco), ordem: Number(form.ordem) || 0 }
      : { ...form, ordem: Number(form.ordem) || 0 };
    salvar.mutate({ tipo: modal.tipo, editando: modal.editando, dados });
  }

  async function remover(tipo, item) {
    const texto = tipo === 'produto'
      ? `Remover "${item.nome}" do cardápio? Os pedidos antigos continuam com ele no histórico.`
      : `Remover a categoria "${item.nome}"?`;
    if (!confirm(texto)) return;
    try {
      await api.delete(`/api/${tipo === 'produto' ? 'produtos' : 'categorias'}/${item.id}`);
      recarregar();
    } catch (e) {
      alert(e.message);
    }
  }

  const carregando = categoriasQuery.isLoading || produtosQuery.isLoading;

  return (
    <div>
      <div className="page-header">
        <div>
          <h2>Cardápio</h2>
          <p>O que aparece no link do seu restaurante. Marque "esgotado" sem precisar apagar nada.</p>
        </div>
        {podeEditar && (
          <div style={{ display: 'flex', gap: 8 }}>
            <button className="btn btn-secundario" onClick={() => abrirCategoria(null)}>Nova categoria</button>
            <button className="btn btn-latao" onClick={() => abrirProduto(null)} disabled={categorias.length === 0}>Novo produto</button>
          </div>
        )}
      </div>

      {carregando ? <p>Carregando...</p> : categorias.length === 0 ? (
        <div className="panel">
          <EstadoVazio mensagem="Comece criando uma categoria (ex.: Pizzas, Bebidas) e depois os produtos." />
        </div>
      ) : categorias.map((categoria) => {
        const daCategoria = produtos.filter((p) => p.categoriaId === categoria.id);
        return (
          <div className="panel" key={categoria.id} style={{ marginBottom: 20 }}>
            <div className="categoria-cabecalho">
              <h3>
                {categoria.nome}
                {!categoria.ativa && <span className="badge badge-pedido-cancelado" style={{ marginLeft: 8 }}>Oculta</span>}
              </h3>
              {podeEditar && (
                <div>
                  <button className="btn-link" onClick={() => abrirProduto(null, categoria.id)}>+ Produto</button>
                  <button className="btn-link" onClick={() => abrirCategoria(categoria)}>Editar</button>
                  <button className="btn-link perigo" onClick={() => remover('categoria', categoria)}>Remover</button>
                </div>
              )}
            </div>
            {daCategoria.length === 0 ? (
              <EstadoVazio mensagem="Nenhum produto nesta categoria." />
            ) : (
              <table className="tabela-cardapio">
                <colgroup>
                  <col />
                  <col style={{ width: 120 }} />
                  <col style={{ width: 130 }} />
                  {podeEditar && <col style={{ width: 240 }} />}
                </colgroup>
                <tbody>
                  {daCategoria.map((p) => (
                    <tr key={p.id}>
                      <td>
                        <strong>{p.nome}</strong>
                        {p.descricao && <div className="descricao-produto">{p.descricao}</div>}
                      </td>
                      <td style={{ whiteSpace: 'nowrap' }}>{moeda(p.preco)}</td>
                      <td>
                        <button
                          className={`badge ${p.disponivel ? 'badge-pedido-concluido' : 'badge-pedido-cancelado'} badge-botao`}
                          onClick={() => alternarDisponivel.mutate(p)}
                          title="Clique para alternar"
                        >
                          {p.disponivel ? 'Disponível' : 'Esgotado'}
                        </button>
                      </td>
                      {podeEditar && (
                        <td style={{ textAlign: 'right', whiteSpace: 'nowrap' }}>
                          <button className="btn btn-secundario" onClick={() => abrirProduto(p)} style={{ marginRight: 8 }}>Editar</button>
                          <button className="btn btn-perigo" onClick={() => remover('produto', p)}>Remover</button>
                        </td>
                      )}
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        );
      })}

      {modal?.tipo === 'categoria' && (
        <Modal titulo={modal.editando ? 'Editar categoria' : 'Nova categoria'} onFechar={() => setModal(null)}>
          {erro && <div className="erro">{erro}</div>}
          <form onSubmit={enviar}>
            <div className="campo">
              <label>Nome</label>
              <input value={form.nome} onChange={(e) => setForm({ ...form, nome: e.target.value })} required />
            </div>
            <div className="campo">
              <label>Ordem no cardápio (menor aparece primeiro)</label>
              <input type="number" value={form.ordem} onChange={(e) => setForm({ ...form, ordem: e.target.value })} />
            </div>
            <div className="campo">
              <label>
                <input type="checkbox" style={{ width: 'auto', marginRight: 8 }} checked={form.ativa} onChange={(e) => setForm({ ...form, ativa: e.target.checked })} />
                Mostrar no cardápio
              </label>
            </div>
            <div className="modal-acoes">
              <button type="button" className="btn btn-secundario" onClick={() => setModal(null)}>Cancelar</button>
              <button className="btn btn-latao" disabled={salvar.isPending}>Salvar</button>
            </div>
          </form>
        </Modal>
      )}

      {modal?.tipo === 'produto' && (
        <Modal titulo={modal.editando ? 'Editar produto' : 'Novo produto'} onFechar={() => setModal(null)}>
          {erro && <div className="erro">{erro}</div>}
          <form onSubmit={enviar}>
            <div className="campo">
              <label>Categoria</label>
              <select value={form.categoriaId} onChange={(e) => setForm({ ...form, categoriaId: e.target.value })} required>
                {categorias.map((c) => <option key={c.id} value={c.id}>{c.nome}</option>)}
              </select>
            </div>
            <div className="campo">
              <label>Nome</label>
              <input value={form.nome} onChange={(e) => setForm({ ...form, nome: e.target.value })} required />
            </div>
            <div className="campo">
              <label>Descrição</label>
              <textarea rows={2} value={form.descricao} onChange={(e) => setForm({ ...form, descricao: e.target.value })} />
            </div>
            <div className="form-grid">
              <div className="campo">
                <label>Preço (R$)</label>
                <input type="number" step="0.01" min="0.01" value={form.preco} onChange={(e) => setForm({ ...form, preco: e.target.value })} required />
              </div>
              <div className="campo">
                <label>Ordem</label>
                <input type="number" value={form.ordem} onChange={(e) => setForm({ ...form, ordem: e.target.value })} />
              </div>
            </div>
            <div className="campo">
              <label>Endereço da foto (opcional)</label>
              <input value={form.imagemUrl} onChange={(e) => setForm({ ...form, imagemUrl: e.target.value })} placeholder="https://..." />
            </div>
            <div className="campo">
              <label>
                <input type="checkbox" style={{ width: 'auto', marginRight: 8 }} checked={form.disponivel} onChange={(e) => setForm({ ...form, disponivel: e.target.checked })} />
                Disponível para pedido
              </label>
            </div>
            <div className="modal-acoes">
              <button type="button" className="btn btn-secundario" onClick={() => setModal(null)}>Cancelar</button>
              <button className="btn btn-latao" disabled={salvar.isPending}>Salvar</button>
            </div>
          </form>
        </Modal>
      )}
    </div>
  );
}
