import AssinaturaNexus from './AssinaturaNexus';
import Icone from './Icone';
import Logo from './Logo';

const RECURSOS = [
  { icone: 'sacola', titulo: 'Cardápio pelo link', texto: 'O cliente pede pelo celular, sem baixar aplicativo e sem taxa por pedido.' },
  { icone: 'relogio', titulo: 'Painel de pedidos', texto: 'Cada pedido muda de cor conforme o tempo, do novo até o entregue.' },
  { icone: 'nota', titulo: 'Relatórios de vendas', texto: 'Faturamento, ticket médio e horários de pico por dia, semana e mês.' },
  { icone: 'estrela', titulo: 'Nexus Score', texto: 'Uma nota de 0 a 100 para a saúde do restaurante, com dicas do que melhorar.' },
];

/** Amostra do painel: as mesmas peças visuais do sistema, com dados de exemplo. */
function Amostra() {
  return (
    <figure className="auth-amostra" aria-label="Exemplo do painel">
      <div className="amostra-pedido">
        <div className="amostra-linha">
          <strong>Pedido #014</strong>
          <span className="chip-tempo ok"><Icone nome="relogio" tamanho={12} /> 7 min</span>
        </div>
        <div className="amostra-linha suave">
          <span>Entrega · Pix</span>
          <strong>R$ 69,40</strong>
        </div>
      </div>
      <div className="amostra-nota">
        <span className="rotulo-secao">Nexus Score</span>
        <div className="amostra-linha">
          <span className="amostra-numero">89</span>
          <span className="faixa-selo faixa-muito_bom">Muito bom</span>
        </div>
        <div className="amostra-escala" aria-hidden="true">
          <span style={{ flexGrow: 40 }} /><span style={{ flexGrow: 20 }} /><span style={{ flexGrow: 15 }} />
          <span className="atual" style={{ flexGrow: 15 }} /><span style={{ flexGrow: 11 }} />
        </div>
      </div>
      <figcaption>Exemplo do painel</figcaption>
    </figure>
  );
}

/**
 * Telas de entrada (login, cadastro, senha): apresentação do Nexus Food à esquerda e o
 * formulário à direita. No celular a apresentação vira um cabeçalho curto em cima do formulário.
 */
export default function AuthLayout({ children }) {
  return (
    <div className="auth-shell">
      <aside className="auth-apresentacao">
        <Logo tom="escuro" tamanho={34} className="auth-marca" />

        <div className="auth-chamada">
          <h2>Pedidos, vendas e a saúde do seu restaurante em um só lugar.</h2>
          <p>Do cardápio no celular do cliente até a nota do Nexus Score, tudo no mesmo painel.</p>
        </div>

        <ul className="auth-recursos">
          {RECURSOS.map((r) => (
            <li key={r.titulo}>
              <span className="auth-recurso-icone"><Icone nome={r.icone} tamanho={18} /></span>
              <div>
                <strong>{r.titulo}</strong>
                <p>{r.texto}</p>
              </div>
            </li>
          ))}
        </ul>

        <Amostra />

        <div className="auth-rodape">
          <span>Teste grátis por 14 dias, sem cartão de crédito.</span>
          <AssinaturaNexus tom="escuro" />
        </div>
      </aside>

      <main className="auth-formulario">
        {children}
      </main>
    </div>
  );
}
