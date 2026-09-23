import { useState } from 'react';
import { useQuery, keepPreviousData } from '@tanstack/react-query';
import { api } from '../api/http';
import { telefone } from '../api/formato';
import EstadoVazio from '../components/EstadoVazio';

export default function Clientes() {
  const [busca, setBusca] = useState('');
  const [termo, setTermo] = useState('');
  const [pagina, setPagina] = useState(0);

  const { data, isLoading } = useQuery({
    queryKey: ['clientes', termo, pagina],
    queryFn: () => api.get(`/api/clientes?pagina=${pagina}&tamanho=20${termo ? `&busca=${encodeURIComponent(termo)}` : ''}`),
    placeholderData: keepPreviousData,
  });

  function buscar(e) {
    e.preventDefault();
    setPagina(0);
    setTermo(busca.trim());
  }

  const clientes = data?.content || [];

  return (
    <div>
      <div className="page-header">
        <div>
          <h2>Clientes</h2>
          <p>Quem já pediu no seu cardápio. O cliente é identificado pelo telefone.</p>
        </div>
      </div>

      <form onSubmit={buscar} className="form-inline" style={{ marginBottom: 16 }}>
        <input placeholder="Buscar por nome ou telefone" value={busca} onChange={(e) => setBusca(e.target.value)} />
        <button className="btn btn-secundario">Buscar</button>
      </form>

      <div className="panel">
        {isLoading ? <p style={{ padding: 20 }}>Carregando...</p> : clientes.length === 0 ? (
          <EstadoVazio mensagem={termo ? 'Nenhum cliente encontrado.' : 'Nenhum cliente ainda. Eles aparecem aqui no primeiro pedido.'} />
        ) : (
          <table>
            <thead><tr><th>Nome</th><th>Telefone</th><th>Cliente desde</th></tr></thead>
            <tbody>
              {clientes.map((c) => (
                <tr key={c.id}>
                  <td>{c.nome}</td>
                  <td><a href={`https://wa.me/${c.telefone}`} target="_blank" rel="noreferrer">{telefone(c.telefone)}</a></td>
                  <td>{new Date(c.criadoEm).toLocaleDateString('pt-BR')}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {data && data.totalPages > 1 && (
        <div className="paginacao">
          <button className="btn btn-secundario" disabled={pagina === 0} onClick={() => setPagina(pagina - 1)}>Anterior</button>
          <span>Página {pagina + 1} de {data.totalPages}</span>
          <button className="btn btn-secundario" disabled={pagina + 1 >= data.totalPages} onClick={() => setPagina(pagina + 1)}>Próxima</button>
        </div>
      )}
    </div>
  );
}
