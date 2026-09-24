import { Link } from 'react-router-dom';
import Icone from './Icone';
import { NOME_PLANO } from '../plano';
import { useAuth } from '../context/AuthContext';

/**
 * Quando o recurso não está no plano: explica o que ele faz e leva aos planos. Nunca um erro cru.
 * Só o administrador muda o plano; os outros perfis recebem o recado para falar com ele.
 */
export default function RecursoBloqueado({ planoNecessario, titulo, mensagem, beneficios = [], compacto = false }) {
  const { usuario } = useAuth();
  const nomePlano = NOME_PLANO[planoNecessario] || planoNecessario;
  const admin = usuario?.papel === 'ADMINISTRADOR';

  return (
    <section className={`panel recurso-bloqueado ${compacto ? 'compacto' : ''}`}>
      <span className="recurso-bloqueado-icone"><Icone nome="cadeado" tamanho={compacto ? 18 : 22} /></span>
      <div className="recurso-bloqueado-corpo">
        <span className="tag-plano">Plano {nomePlano}</span>
        <h3>{titulo || `Disponível a partir do plano ${nomePlano}`}</h3>
        {mensagem && <p>{mensagem}</p>}
        {beneficios.length > 0 && (
          <ul>
            {beneficios.map((b) => <li key={b}><Icone nome="check" tamanho={14} /> {b}</li>)}
          </ul>
        )}
        {admin ? (
          <Link to="/painel/assinatura" className="btn btn-latao">Ver planos</Link>
        ) : (
          <p className="suave">Para liberar, fale com o administrador do restaurante.</p>
        )}
      </div>
    </section>
  );
}
