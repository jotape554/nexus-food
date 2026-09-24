import { useMemo, useState } from 'react';
import { api } from '../api/http';
import { moeda } from '../api/formato';
import { COBRANCA_TEXTO, precoComOpcoes, regraDoGrupo } from '../api/opcoes';
import Modal from './Modal';

let seq = 0;
const chave = () => `n${++seq}`;

/** Grupo/opção vindos da API ganham uma chave local (para listas React e itens ainda sem id). */
const paraEdicao = (grupos) => grupos.map((g) => ({
  ...g, chave: chave(), opcoes: g.opcoes.map((o) => ({ ...o, chave: chave(), preco: Number(o.preco).toFixed(2) })),
}));

const opcao = (nome = '', preco = '0') => ({ chave: chave(), nome, preco, disponivel: true });

/** Modelos prontos: o restaurante só ajusta nomes e preços. */
function modelos(produto, outrosDaCategoria) {
  return [
    { rotulo: 'Tamanho', grupo: { nome: 'Tamanho', minimo: 1, maximo: 1, cobranca: 'SOMA', opcoes: [opcao('Média'), opcao('Grande', '12')] } },
    { rotulo: 'Borda', grupo: { nome: 'Borda recheada', minimo: 0, maximo: 1, cobranca: 'SOMA', opcoes: [opcao('Catupiry', '8'), opcao('Cheddar', '8')] } },
    { rotulo: 'Adicionais', grupo: { nome: 'Adicionais', minimo: 0, maximo: 3, cobranca: 'SOMA', opcoes: [opcao('Bacon', '6'), opcao('Queijo extra', '5')] } },
    {
      rotulo: 'Meio a meio',
      dica: outrosDaCategoria.length ? `Sabores: os produtos de ${produto.categoriaNome || 'mesma categoria'}` : null,
      grupo: {
        nome: 'Sabores', minimo: 2, maximo: 2, cobranca: 'MAIOR',
        opcoes: outrosDaCategoria.length
          ? outrosDaCategoria.map((p) => opcao(p.nome, Number(p.preco).toFixed(2)))
          : [opcao('Sabor 1'), opcao('Sabor 2')],
      },
    },
    { rotulo: 'Em branco', grupo: { nome: '', minimo: 0, maximo: 1, cobranca: 'SOMA', opcoes: [opcao()] } },
  ];
}

function mover(lista, i, delta) {
  const j = i + delta;
  if (j < 0 || j >= lista.length) return lista;
  const nova = [...lista];
  [nova[i], nova[j]] = [nova[j], nova[i]];
  return nova;
}

/** Frase que confirma para o dono o que o cliente vai ver. */
function resumoDoGrupo(g) {
  const n = Number(g.minimo) || 0;
  const max = Number(g.maximo) || 1;
  const base = n === 0
    ? (max === 1 ? 'Opcional: o cliente pode escolher 1' : `Opcional: o cliente escolhe até ${max}`)
    : (n === max ? `Obrigatório: o cliente escolhe ${n}` : `Obrigatório: o cliente escolhe de ${n} a ${max}`);
  return max > 1 ? `${base}; ${COBRANCA_TEXTO[g.cobranca || 'SOMA']}.` : `${base}.`;
}

/**
 * Adicionais e variações de um produto. Quem edita o cardápio monta os grupos; a equipe toda
 * (inclusive atendente) pode marcar uma opção como esgotada, na hora, sem mexer no resto.
 */
export default function EditorOpcoes({ produto, produtos, categoriaNome, podeEditar, onSalvo, onFechar }) {
  const [grupos, setGrupos] = useState(() => paraEdicao(produto.grupos || []));
  const [erro, setErro] = useState('');
  const [salvando, setSalvando] = useState(false);
  const [copiarDe, setCopiarDe] = useState('');

  const outrosDaCategoria = useMemo(
    () => produtos.filter((p) => p.categoriaId === produto.categoriaId && p.id !== produto.id && Number(p.preco) > 0),
    [produtos, produto],
  );
  const comOpcoes = produtos.filter((p) => p.id !== produto.id && (p.grupos || []).length > 0);

  const alterarGrupo = (i, campos) => setGrupos((gs) => gs.map((g, k) => (k === i ? { ...g, ...campos } : g)));
  const alterarOpcao = (i, j, campos) => setGrupos((gs) => gs.map((g, k) => (k !== i ? g : {
    ...g, opcoes: g.opcoes.map((o, m) => (m === j ? { ...o, ...campos } : o)),
  })));

  function adicionarModelo(m) {
    setGrupos((gs) => [...gs, { ...m.grupo, chave: chave(), opcoes: m.grupo.opcoes.map((o) => ({ ...o, chave: chave() })) }]);
  }

  function copiar() {
    const origem = produtos.find((p) => String(p.id) === copiarDe);
    if (!origem) return;
    // Cópia sem ids: vira grupo novo deste produto.
    setGrupos((gs) => [...gs, ...origem.grupos.map((g) => ({
      nome: g.nome, minimo: g.minimo, maximo: g.maximo, cobranca: g.cobranca, chave: chave(),
      opcoes: g.opcoes.map((o) => ({ nome: o.nome, preco: Number(o.preco).toFixed(2), disponivel: o.disponivel, chave: chave() })),
    }))]);
    setCopiarDe('');
  }

  // Prévia do "a partir de" com o que está na tela (mesma conta do servidor).
  const previa = useMemo(() => {
    const comIds = {
      preco: produto.preco,
      grupos: grupos.map((g, i) => ({
        id: `g${i}`, cobranca: g.cobranca || 'SOMA', minimo: Number(g.minimo) || 0,
        opcoes: g.opcoes.map((o, j) => ({ id: `g${i}o${j}`, preco: Number(o.preco) || 0, disponivel: o.disponivel })),
      })),
    };
    const minimos = comIds.grupos.flatMap((g) => [...g.opcoes].filter((o) => o.disponivel)
      .sort((a, b) => a.preco - b.preco).slice(0, g.minimo).map((o) => o.id));
    return precoComOpcoes(comIds, minimos);
  }, [grupos, produto.preco]);

  async function salvar() {
    setErro('');
    setSalvando(true);
    try {
      const salvo = await api.put(`/api/produtos/${produto.id}/opcoes`, {
        grupos: grupos.map((g) => ({
          id: g.id, nome: g.nome.trim(), minimo: Number(g.minimo) || 0, maximo: Number(g.maximo) || 1, cobranca: g.cobranca || 'SOMA',
          opcoes: g.opcoes.map((o) => ({ id: o.id, nome: o.nome.trim(), preco: Number(String(o.preco).replace(',', '.')) || 0, disponivel: o.disponivel })),
        })),
      });
      onSalvo(salvo);
    } catch (e) {
      setErro(e.message);
      setSalvando(false);
    }
  }

  async function alternarDisponivel(g, o) {
    setErro('');
    try {
      const salvo = await api.patch(`/api/produtos/${produto.id}/opcoes/${o.id}/disponivel?valor=${!o.disponivel}`);
      setGrupos(paraEdicao(salvo.grupos));
      onSalvo(salvo, { manterAberto: true });
    } catch (e) {
      setErro(e.message);
    }
  }

  // Atendente: só marca esgotado/disponível, direto, opção por opção.
  if (!podeEditar) {
    return (
      <Modal titulo={`Opções de ${produto.nome}`} onFechar={onFechar}>
        {erro && <div className="erro" role="alert">{erro}</div>}
        <p className="modal-texto">Toque numa opção para marcar como esgotada ou disponível.</p>
        {grupos.map((g) => (
          <div key={g.chave} className="opcoes-leitura">
            <strong>{g.nome}</strong>
            {g.opcoes.map((o) => (
              <div key={o.chave} className="opcoes-leitura-linha">
                <span>{o.nome}</span>
                <button type="button" className={`badge badge-botao ${o.disponivel ? 'badge-pedido-concluido' : 'badge-pedido-cancelado'}`} onClick={() => alternarDisponivel(g, o)}>
                  {o.disponivel ? 'Disponível' : 'Esgotada'}
                </button>
              </div>
            ))}
          </div>
        ))}
        <div className="modal-acoes"><button type="button" className="btn btn-secundario" onClick={onFechar}>Fechar</button></div>
      </Modal>
    );
  }

  return (
    <Modal titulo={`Opções de ${produto.nome}`} onFechar={onFechar} largo>
      <div className="editor-opcoes">
        <p className="modal-texto">
          Tamanhos, bordas, adicionais e sabores. O cliente escolhe no cardápio e o preço é calculado sozinho.
        </p>

        {grupos.length === 0 && <p className="editor-vazio">Este produto ainda não tem opções. Comece por um modelo:</p>}

        {grupos.map((g, i) => (
          <section key={g.chave} className="grupo-editor">
            <div className="grupo-editor-topo">
              <input
                className="grupo-editor-nome"
                value={g.nome}
                onChange={(e) => alterarGrupo(i, { nome: e.target.value })}
                placeholder="Nome do grupo (ex.: Tamanho)"
                aria-label="Nome do grupo"
                maxLength={80}
              />
              <div className="grupo-editor-acoes">
                <button type="button" className="btn-icone-mini" onClick={() => setGrupos((gs) => mover(gs, i, -1))} disabled={i === 0} aria-label="Subir grupo">↑</button>
                <button type="button" className="btn-icone-mini" onClick={() => setGrupos((gs) => mover(gs, i, 1))} disabled={i === grupos.length - 1} aria-label="Descer grupo">↓</button>
                <button type="button" className="btn-link perigo" onClick={() => setGrupos((gs) => gs.filter((_, k) => k !== i))}>Remover grupo</button>
              </div>
            </div>

            <div className="grupo-editor-regras">
              <label className="regra-check">
                <input
                  type="checkbox"
                  checked={Number(g.minimo) > 0}
                  onChange={(e) => alterarGrupo(i, { minimo: e.target.checked ? 1 : 0 })}
                />
                Obrigatório
              </label>
              {Number(g.minimo) > 0 && (
                <label className="regra-numero">
                  Mínimo
                  <input type="number" min={1} max={g.opcoes.length} value={g.minimo} onChange={(e) => alterarGrupo(i, { minimo: e.target.value })} />
                </label>
              )}
              <label className="regra-numero">
                Máximo de escolhas
                <input type="number" min={1} max={30} value={g.maximo} onChange={(e) => alterarGrupo(i, { maximo: e.target.value })} />
              </label>
              {Number(g.maximo) > 1 && (
                <label className="regra-cobranca">
                  Preço
                  <select value={g.cobranca || 'SOMA'} onChange={(e) => alterarGrupo(i, { cobranca: e.target.value })}>
                    <option value="SOMA">Somar cada escolha</option>
                    <option value="MAIOR">Cobrar a mais cara (meio a meio)</option>
                    <option value="MEDIA">Cobrar a média</option>
                  </select>
                </label>
              )}
            </div>
            <p className="grupo-editor-resumo">{resumoDoGrupo(g)}</p>

            <div className="opcoes-editor">
              {g.opcoes.map((o, j) => (
                <div key={o.chave} className={`opcao-editor ${o.disponivel ? '' : 'esgotada'}`}>
                  <input value={o.nome} onChange={(e) => alterarOpcao(i, j, { nome: e.target.value })} placeholder="Nome da opção" aria-label="Nome da opção" maxLength={80} />
                  <label className="opcao-editor-preco">
                    <span>{(g.cobranca || 'SOMA') === 'SOMA' ? '+ R$' : 'R$'}</span>
                    <input type="number" step="0.01" min="0" inputMode="decimal" value={o.preco} onChange={(e) => alterarOpcao(i, j, { preco: e.target.value })} aria-label={`Preço de ${o.nome || 'opção'}`} />
                  </label>
                  <label className="opcao-editor-disp" title="Desmarque quando acabar">
                    <input type="checkbox" checked={o.disponivel} onChange={(e) => alterarOpcao(i, j, { disponivel: e.target.checked })} />
                    Disponível
                  </label>
                  <button type="button" className="btn-icone-mini" onClick={() => alterarGrupo(i, { opcoes: g.opcoes.filter((_, m) => m !== j) })} aria-label={`Remover ${o.nome || 'opção'}`}>×</button>
                </div>
              ))}
              <button type="button" className="btn-link" onClick={() => alterarGrupo(i, { opcoes: [...g.opcoes, opcao()] })}>+ Opção</button>
            </div>
          </section>
        ))}

        <div className="editor-modelos">
          <span>Adicionar grupo:</span>
          {modelos({ ...produto, categoriaNome }, outrosDaCategoria).map((m) => (
            <button key={m.rotulo} type="button" className="chip" onClick={() => adicionarModelo(m)} title={m.dica || undefined}>+ {m.rotulo}</button>
          ))}
        </div>
        {comOpcoes.length > 0 && (
          <div className="editor-copiar">
            <select value={copiarDe} onChange={(e) => setCopiarDe(e.target.value)} aria-label="Copiar opções de outro produto">
              <option value="">Copiar opções de outro produto...</option>
              {comOpcoes.map((p) => <option key={p.id} value={p.id}>{p.nome}</option>)}
            </select>
            <button type="button" className="btn btn-secundario" onClick={copiar} disabled={!copiarDe}>Copiar</button>
          </div>
        )}

        <div className="editor-previa">
          No cardápio: <strong>{grupos.length && previa !== Number(produto.preco) ? `a partir de ${moeda(previa)}` : moeda(previa)}</strong>
          {grupos.map((g) => g.nome && <span key={g.chave} className="tag-previa">{g.nome} · {regraDoGrupo({ minimo: Number(g.minimo) || 0, maximo: Number(g.maximo) || 1 })}</span>)}
        </div>

        {erro && <div className="erro" role="alert">{erro}</div>}
        <div className="modal-acoes">
          <button type="button" className="btn btn-secundario" onClick={onFechar}>Cancelar</button>
          <button type="button" className="btn btn-latao" onClick={salvar} disabled={salvando}>{salvando ? 'Salvando...' : 'Salvar opções'}</button>
        </div>
      </div>
    </Modal>
  );
}
