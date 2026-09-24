import { useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { api } from '../api/http';
import Icone from '../components/Icone';
import GraficoLinha from '../components/graficos/GraficoLinha';
import RecursoBloqueado from '../components/RecursoBloqueado';

// ---------- formatação ----------

const numero = (v, casas = 1) => Number(v).toLocaleString('pt-BR', { minimumFractionDigits: 0, maximumFractionDigits: casas });

function dataCurta(iso) {
  const [, m, d] = iso.split('-');
  return `${d}/${m}`;
}

/** Valor do indicador na linguagem do dono do restaurante. */
function formatarValor(ind) {
  if (ind.valor == null) return '—';
  const v = Number(ind.valor);
  if (ind.unidade === 'MINUTOS') return `${numero(v, v < 10 ? 1 : 0)} min`;
  if (ind.unidade === 'COEFICIENTE') return `${numero(v * 100, 0)}% de variação`;
  const comSinal = ind.ancoras.zero < 0; // indicadores de crescimento
  return `${comSinal && v > 0 ? '+' : ''}${numero(v, 1)}%`;
}

function formatarAncora(ind, valor) {
  if (ind.unidade === 'MINUTOS') return `${numero(valor)} min`;
  if (ind.unidade === 'COEFICIENTE') return `${numero(valor * 100, 0)}%`;
  return `${ind.ancoras.zero < 0 && valor > 0 ? '+' : ''}${numero(valor)}%`;
}

const SEVERIDADE = {
  ALERTA: { rotulo: 'Alerta', icone: 'alerta' },
  OPORTUNIDADE: { rotulo: 'Oportunidade', icone: 'lampada' },
  CONQUISTA: { rotulo: 'Conquista', icone: 'trofeu' },
};

// ---------- pedaços ----------

function EscalaFaixas({ faixas, nota }) {
  return (
    <div className="escala-faixas" aria-hidden="true">
      <div className="escala-trilho">
        {faixas.map((f) => (
          <span key={f.codigo} className={`escala-seg faixa-${f.codigo.toLowerCase()} ${nota != null && nota >= f.de && nota <= f.ate ? 'atual' : ''}`}
            style={{ flexGrow: f.ate - f.de + 1 }} />
        ))}
        {nota != null && <span className="escala-marcador" style={{ left: `${nota}%` }} />}
      </div>
      <div className="escala-rotulos">
        {faixas.map((f) => (
          <span key={f.codigo} style={{ flexGrow: f.ate - f.de + 1 }}>{f.nome}</span>
        ))}
      </div>
    </div>
  );
}

function CartaoNota({ n }) {
  if (n.situacao === 'COLETANDO') {
    const diasPct = Math.min(100, (n.diasHistorico / 28) * 100);
    const pedidosPct = Math.min(100, (n.pedidosConcluidosJanela / n.pedidosConcluidosMinimos) * 100);
    return (
      <section className="panel nexus-nota coletando">
        <div className="nexus-nota-principal">
          <div className="rotulo-secao">Nexus Score</div>
          <h3 className="coletando-titulo">Coletando dados</h3>
          <p className="suave">{n.motivoSemNota}</p>
        </div>
        <div className="progresso-coleta">
          <div>
            <div className="progresso-texto"><span>Dias de histórico</span><strong>{n.diasHistorico} de 28</strong></div>
            <div className="medidor"><span style={{ width: `${diasPct}%` }} /></div>
          </div>
          <div>
            <div className="progresso-texto"><span>Pedidos concluídos nas últimas 4 semanas</span><strong>{n.pedidosConcluidosJanela} de {n.pedidosConcluidosMinimos}</strong></div>
            <div className="medidor"><span style={{ width: `${pedidosPct}%` }} /></div>
          </div>
        </div>
      </section>
    );
  }

  return (
    <section className="panel nexus-nota">
      <div className="nexus-nota-principal">
        <div className="rotulo-secao">Nexus Score</div>
        <div className="nota-linha">
          <span className="nota-numero">{n.nota}</span>
          <div className="nota-lado">
            <span className={`faixa-selo faixa-${n.faixa.toLowerCase()}`}>{n.faixaNome}</span>
            {n.variacao7Dias != null && (
              <span className={`nota-variacao ${n.variacao7Dias > 0 ? 'positiva' : n.variacao7Dias < 0 ? 'negativa' : ''}`}>
                {n.variacao7Dias > 0 ? '▲' : n.variacao7Dias < 0 ? '▼' : '■'} {Math.abs(n.variacao7Dias)} {Math.abs(n.variacao7Dias) === 1 ? 'ponto' : 'pontos'} em 7 dias
              </span>
            )}
          </div>
        </div>
        {n.situacao === 'PROVISORIA' && (
          <p className="selo-provisoria">
            <Icone nome="relogio" tamanho={14} /> Nota em formação: completa em {n.diasParaOficial} {n.diasParaOficial === 1 ? 'dia' : 'dias'}.
            Os indicadores de crescimento entram quando houver 8 semanas de histórico.
          </p>
        )}
      </div>
      <EscalaFaixas faixas={n.faixas} nota={n.nota} />
      <ul className="resumo-areas" aria-label="Nota por área">
        {n.areas.map((a) => (
          <li key={a.codigo}>
            <span>{a.nome} <span className="suave">· {numero(a.peso * 100, 0)}%</span></span>
            <span className="resumo-areas-nota">{a.exibida ? numero(a.nota, 0) : '—'}</span>
          </li>
        ))}
      </ul>
      <p className="suave nota-rodape">
        Dados de {dataCurta(n.inicioJanela)} a {dataCurta(n.dia)} · {n.pedidosConcluidosJanela} pedidos concluídos · regra {n.regraVersao}
      </p>
    </section>
  );
}

function Area({ area }) {
  return (
    <section className={`panel nexus-area ${area.exibida ? '' : 'apagada'}`}>
      <header className="nexus-area-topo">
        <div>
          <h3>{area.nome}</h3>
          <span className="suave">Peso {numero(area.peso * 100, 0)}% da nota</span>
        </div>
        <div className="nexus-area-nota">
          {area.exibida ? <>{numero(area.nota, 0)}<small>/100</small></> : <span className="suave">sem nota</span>}
        </div>
      </header>
      <ul className="nexus-indicadores">
        {area.indicadores.map((ind) => (
          <li key={ind.codigo} className={ind.status === 'OK' ? '' : 'sem-dados'}>
            <div className="ind-linha">
              <span className="ind-nome" title={ind.descricao}>{ind.nome}</span>
              <span className="ind-valor">{formatarValor(ind)}</span>
            </div>
            {ind.status === 'OK' ? (
              <div className="ind-pontos">
                <div className="medidor"><span style={{ width: `${Math.max(1, Number(ind.pontos))}%` }} /></div>
                <span className="ind-pontos-numero">{numero(ind.pontos, 0)} pts</span>
              </div>
            ) : (
              <p className="ind-motivo">{ind.motivo}</p>
            )}
          </li>
        ))}
      </ul>
    </section>
  );
}

// ---------- página ----------

export default function Nexus() {
  const { data: n, error, isLoading } = useQuery({
    queryKey: ['nexus'],
    queryFn: () => api.get('/api/nexus'),
    staleTime: 5 * 60 * 1000,
  });

  // A evolução começa no primeiro dia com nota (antes disso só havia coleta de dados).
  const evolucao = useMemo(() => (n ? n.historico.slice(Math.max(0, n.historico.findIndex((h) => h.nota != null))).map((h) => ({
    chave: h.dia,
    valor: h.nota,
    rotulo: dataCurta(h.dia),
    rotuloLongo: `Dados até ${dataCurta(h.dia)}`,
    detalhe: h.situacao === 'PROVISORIA' ? 'Nota em formação' : h.situacao === 'COLETANDO' ? 'Coletando dados' : null,
  })) : []), [n]);

  if (error?.status === 402 && error.dados?.upgradeNecessario) {
    return <RecursoBloqueado planoNecessario={error.dados.planoNecessario} mensagem={error.dados.mensagem} />;
  }
  if (isLoading) return <p>Calculando o Nexus Score...</p>;
  if (error) return <div className="erro" role="alert">{error.message}</div>;

  const comNota = evolucao.filter((p) => p.valor != null).length;

  return (
    <div className="nexus">
      <div className="page-header">
        <div>
          <h2>Nexus</h2>
          <p>
            A saúde do seu restaurante em uma nota de 0 a 100, com os dados das últimas 4 semanas
            ({dataCurta(n.inicioJanela)} a {dataCurta(n.dia)}). Atualiza todo dia com o dia que fechou.
          </p>
        </div>
      </div>

      <div className="nexus-topo">
        <CartaoNota n={n} />

        <section className="panel nexus-insights" aria-label="O que fazer agora">
          <h3>O que merece sua atenção</h3>
          {n.insights.length === 0 ? (
            <p className="suave">Nenhum destaque por enquanto. Os avisos aparecem aqui conforme os dados chegam.</p>
          ) : (
            <ul>
              {n.insights.map((i) => (
                <li key={i.id} className={`insight insight-${i.severidade.toLowerCase()}`}>
                  <span className="insight-selo"><Icone nome={SEVERIDADE[i.severidade].icone} tamanho={14} /> {SEVERIDADE[i.severidade].rotulo}</span>
                  <p>{i.texto}</p>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>

      <div className="nexus-areas">
        {n.areas.map((a) => <Area key={a.codigo} area={a} />)}
      </div>

      {comNota > 1 && (
        <section className="panel cartao-grafico">
          <div className="cartao-grafico-topo">
            <h3>Evolução da nota</h3>
            <span className="suave">Cada ponto usa as 4 semanas até aquele dia</span>
          </div>
          <GraficoLinha
            titulo="Evolução do Nexus Score"
            dados={evolucao}
            referencias={[{ valor: 0, rotulo: '0' }, { valor: 40, rotulo: '40' }, { valor: 60, rotulo: '60' }, { valor: 75, rotulo: '75' }, { valor: 90, rotulo: '90' }, { valor: 100, rotulo: '100' }]}
            formatarValor={(v) => `Nota ${v}`}
          />
        </section>
      )}

      <details className="panel nexus-metodo">
        <summary>Como a nota é calculada</summary>
        <div className="nexus-metodo-corpo">
          <p>
            Cada indicador vira de 0 a 100 pontos: o valor de referência vale 60 (um restaurante estável), e o
            ideal vale 100. A nota da área é a média dos seus indicadores e a nota final é a média das áreas,
            pelos pesos abaixo. Indicador sem dados suficientes não conta como zero: o peso dele passa para os outros.
            A nota aparece a partir de 28 dias de histórico e fica completa com 56. Regra {n.regraVersao}.
          </p>
          <table>
            <thead>
              <tr><th>Indicador</th><th className="num">Peso na nota</th><th className="num">0 pts</th><th className="num">60 pts</th><th className="num">100 pts</th></tr>
            </thead>
            <tbody>
              {n.areas.flatMap((a) => a.indicadores.map((ind) => (
                <tr key={ind.codigo}>
                  <td><strong>{ind.nome}</strong><div className="suave">{ind.descricao}</div></td>
                  <td className="num">{numero(a.peso * ind.pesoNaArea * 100, 1)}%</td>
                  <td className="num">{formatarAncora(ind, ind.ancoras.zero)}</td>
                  <td className="num">{formatarAncora(ind, ind.ancoras.referencia)}</td>
                  <td className="num">{formatarAncora(ind, ind.ancoras.cem)}</td>
                </tr>
              )))}
            </tbody>
          </table>
        </div>
      </details>
    </div>
  );
}
