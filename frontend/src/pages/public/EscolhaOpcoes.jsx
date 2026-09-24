import { useEffect, useRef, useState } from 'react';
import { moeda } from '../../api/formato';
import { COBRANCA_TEXTO, precoComOpcoes, problemaDaEscolha, regraDoGrupo } from '../../api/opcoes';

/**
 * Folha que sobe do rodapé quando o produto tem opções (tamanho, borda, adicionais, sabores).
 * Grupo de uma escolha vira botão de rádio; de várias, caixa de marcar com limite.
 * O preço vai mudando conforme a escolha; o botão só libera quando os obrigatórios estão feitos.
 */
export default function EscolhaOpcoes({ produto, onAdicionar, onFechar }) {
  const [escolhidas, setEscolhidas] = useState(() => {
    // Grupo obrigatório de uma escolha só com uma opção disponível: já vem marcado.
    const iniciais = [];
    produto.grupos.forEach((g) => {
      const disponiveis = g.opcoes.filter((o) => o.disponivel);
      if (g.minimo === 1 && g.maximo === 1 && disponiveis.length === 1) iniciais.push(disponiveis[0].id);
    });
    return iniciais;
  });
  const [quantidade, setQuantidade] = useState(1);
  const [observacao, setObservacao] = useState('');
  const [tentou, setTentou] = useState(false);
  const painelRef = useRef(null);

  useEffect(() => {
    painelRef.current?.focus();
    const fechar = (e) => { if (e.key === 'Escape') onFechar(); };
    window.addEventListener('keydown', fechar);
    return () => window.removeEventListener('keydown', fechar);
  }, []);

  const marcadasNoGrupo = (g) => escolhidas.filter((id) => g.opcoes.some((o) => o.id === id));

  function alternar(g, opcao) {
    setEscolhidas((atual) => {
      const doGrupo = marcadasNoGrupo(g);
      if (g.maximo === 1) {
        const semGrupo = atual.filter((id) => !doGrupo.includes(id));
        return doGrupo.includes(opcao.id) && g.minimo === 0 ? semGrupo : [...semGrupo, opcao.id];
      }
      if (atual.includes(opcao.id)) return atual.filter((id) => id !== opcao.id);
      if (doGrupo.length >= g.maximo) return atual;
      return [...atual, opcao.id];
    });
  }

  const problema = problemaDaEscolha(produto, escolhidas);
  const unitario = precoComOpcoes(produto, escolhidas);

  function adicionar() {
    setTentou(true);
    if (problema) {
      // Leva até o primeiro grupo que falta.
      const faltando = produto.grupos.find((g) => marcadasNoGrupo(g).length < g.minimo);
      if (faltando) document.getElementById(`grupo-${faltando.id}`)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
      return;
    }
    onAdicionar({ produtoId: produto.id, opcoes: escolhidas, quantidade, observacao: observacao.trim() });
  }

  return (
    <div className="folha-fundo" onClick={onFechar}>
      <div
        className="folha"
        role="dialog"
        aria-modal="true"
        aria-labelledby="folha-titulo"
        tabIndex={-1}
        ref={painelRef}
        onClick={(e) => e.stopPropagation()}
      >
        <header className="folha-topo">
          {produto.imagemUrl && <img src={produto.imagemUrl} alt="" />}
          <div>
            <h2 id="folha-titulo">{produto.nome}</h2>
            {produto.descricao && <p>{produto.descricao}</p>}
          </div>
          <button type="button" className="folha-fechar" onClick={onFechar} aria-label="Fechar">×</button>
        </header>

        <div className="folha-corpo">
          {produto.grupos.map((g) => {
            const marcadas = marcadasNoGrupo(g);
            const faltaAqui = tentou && marcadas.length < g.minimo;
            const cheio = g.maximo > 1 && marcadas.length >= g.maximo;
            return (
              <fieldset key={g.id} id={`grupo-${g.id}`} className={`grupo-escolha ${faltaAqui ? 'faltando' : ''}`}>
                <legend>
                  <span className="grupo-nome">{g.nome}</span>
                  <span className="grupo-regra">
                    {regraDoGrupo(g)}
                    {g.maximo > 1 && g.cobranca !== 'SOMA' && ` · ${COBRANCA_TEXTO[g.cobranca]}`}
                  </span>
                  {g.minimo > 0 && (
                    <span className={`grupo-selo ${marcadas.length >= g.minimo ? 'ok' : ''}`}>
                      {marcadas.length >= g.minimo ? 'Pronto' : 'Obrigatório'}
                    </span>
                  )}
                </legend>
                {g.opcoes.map((o) => {
                  const marcada = escolhidas.includes(o.id);
                  const bloqueada = !o.disponivel || (cheio && !marcada);
                  return (
                    <label key={o.id} className={`escolha-opcao ${marcada ? 'marcada' : ''} ${bloqueada ? 'bloqueada' : ''}`}>
                      <input
                        type={g.maximo === 1 ? 'radio' : 'checkbox'}
                        name={`grupo-${g.id}`}
                        checked={marcada}
                        disabled={bloqueada}
                        onChange={() => alternar(g, o)}
                        onClick={() => { if (g.maximo === 1 && marcada && g.minimo === 0) alternar(g, o); }}
                      />
                      <span className="escolha-nome">{o.nome}</span>
                      <span className="escolha-preco">
                        {!o.disponivel ? 'Esgotado' : Number(o.preco) > 0 ? (g.cobranca === 'SOMA' ? `+ ${moeda(o.preco)}` : moeda(o.preco)) : ''}
                      </span>
                    </label>
                  );
                })}
                {faltaAqui && <p className="grupo-aviso" role="alert">{regraDoGrupo(g)} para continuar.</p>}
              </fieldset>
            );
          })}

          <div className="campo">
            <label htmlFor="folha-obs">Alguma observação? <span className="opcional">(opcional)</span></label>
            <input id="folha-obs" maxLength={300} value={observacao} onChange={(e) => setObservacao(e.target.value)} placeholder="Ex.: sem cebola, bem passada" />
          </div>
        </div>

        <footer className="folha-rodape">
          <div className="controle-qtd">
            <button type="button" onClick={() => setQuantidade((q) => Math.max(1, q - 1))} aria-label="Diminuir">−</button>
            <span>{quantidade}</span>
            <button type="button" onClick={() => setQuantidade((q) => Math.min(99, q + 1))} aria-label="Aumentar">+</button>
          </div>
          <button type="button" className={`btn btn-latao folha-adicionar ${problema ? 'incompleto' : ''}`} onClick={adicionar}>
            {problema && tentou ? problema : `Adicionar · ${moeda(unitario * quantidade)}`}
          </button>
        </footer>
      </div>
    </div>
  );
}
