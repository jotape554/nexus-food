import { agruparOpcoes } from '../api/opcoes';

/** Opções escolhidas num item, uma linha por grupo: "Borda: Catupiry", "Adicionais: Bacon, Azeitona". */
export default function OpcoesDoItem({ opcoes, className = 'opcoes-item' }) {
  if (!opcoes || opcoes.length === 0) return null;
  return (
    <ul className={className}>
      {agruparOpcoes(opcoes).map((g) => (
        <li key={g.grupo}><span>{g.grupo}:</span> {g.nomes.join(', ')}</li>
      ))}
    </ul>
  );
}
