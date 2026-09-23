const NOME_PLANO = { BASICO: 'Básico', PROFISSIONAL: 'Profissional', PREMIUM: 'Premium' };

/** Tela "elegante" mostrada quando o recurso não está incluído no plano atual — nunca um erro cru. */
export default function RecursoBloqueado({ planoNecessario, mensagem }) {
  const nomePlano = NOME_PLANO[planoNecessario] || planoNecessario;

  return (
    <div className="panel" style={{ padding: '56px 32px', textAlign: 'center' }}>
      <div aria-hidden="true" style={{ fontSize: 32, color: 'var(--latao)', marginBottom: 16 }}>◆</div>
      <h3 style={{ marginBottom: 8 }}>Disponível a partir do plano {nomePlano}</h3>
      <p style={{ color: 'var(--texto-suave)', maxWidth: 420, margin: '0 auto 24px' }}>
        {mensagem || `Este recurso faz parte do plano ${nomePlano} ou superior.`}
      </p>
      <a href="/painel/assinatura" className="btn btn-latao">Ver planos e fazer upgrade</a>
    </div>
  );
}
