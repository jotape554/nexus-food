import { useEffect, useMemo, useRef, useState } from 'react';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { api } from '../api/http';
import { moeda, MODALIDADE_LABEL, PAGAMENTO_LABEL } from '../api/formato';
import GraficoColunas from '../components/graficos/GraficoColunas';
import RecursoBloqueado from '../components/RecursoBloqueado';
import Icone from '../components/Icone';
import { salvarArquivo } from '../api/arquivo';

// ---------- datas (sempre em AAAA-MM-DD, sem fuso: são dias operacionais) ----------

function paraData(iso) {
  const [a, m, d] = iso.split('-').map(Number);
  return new Date(Date.UTC(a, m - 1, d));
}
function paraIso(data) {
  return data.toISOString().slice(0, 10);
}
function somarDias(iso, dias) {
  const d = paraData(iso);
  d.setUTCDate(d.getUTCDate() + dias);
  return paraIso(d);
}
function inicioDoMes(iso, mesesAtras = 0) {
  const d = paraData(iso);
  return paraIso(new Date(Date.UTC(d.getUTCFullYear(), d.getUTCMonth() - mesesAtras, 1)));
}
function fimDoMes(iso) {
  const d = paraData(iso);
  return paraIso(new Date(Date.UTC(d.getUTCFullYear(), d.getUTCMonth() + 1, 0)));
}

const DIA_SEMANA_CURTO = ['dom', 'seg', 'ter', 'qua', 'qui', 'sex', 'sáb'];
const DIA_SEMANA = ['Segunda', 'Terça', 'Quarta', 'Quinta', 'Sexta', 'Sábado', 'Domingo'];
const MES_CURTO = ['jan', 'fev', 'mar', 'abr', 'mai', 'jun', 'jul', 'ago', 'set', 'out', 'nov', 'dez'];

function diaMes(iso) {
  const [, m, d] = iso.split('-');
  return `${d}/${m}`;
}
function dataCompleta(iso) {
  const [a, m, d] = iso.split('-');
  return `${d}/${m}/${a}`;
}
function periodoTexto(inicio, fim) {
  return inicio === fim ? dataCompleta(inicio) : `${dataCompleta(inicio)} a ${dataCompleta(fim)}`;
}

const PRESETS = [
  { id: 'hoje', rotulo: 'Hoje', periodo: (h) => [h, h], agrupamento: 'DIA' },
  { id: '7d', rotulo: '7 dias', periodo: (h) => [somarDias(h, -6), h], agrupamento: 'DIA' },
  { id: '30d', rotulo: '30 dias', periodo: (h) => [somarDias(h, -29), h], agrupamento: 'DIA' },
  { id: 'mes', rotulo: 'Este mês', periodo: (h) => [inicioDoMes(h), h], agrupamento: 'DIA' },
  { id: 'mesPassado', rotulo: 'Mês passado', periodo: (h) => [inicioDoMes(h, 1), fimDoMes(inicioDoMes(h, 1))], agrupamento: 'DIA' },
  { id: '12m', rotulo: '12 meses', periodo: (h) => [inicioDoMes(h, 11), h], agrupamento: 'MES' },
];

const AGRUPAMENTOS = [['DIA', 'Dia'], ['SEMANA', 'Semana'], ['MES', 'Mês']];

// ---------- formatação ----------

function moedaEixo(v) {
  if (v >= 1000) return `R$ ${(v / 1000).toLocaleString('pt-BR', { maximumFractionDigits: 1 })} mil`;
  return `R$ ${v.toLocaleString('pt-BR', { maximumFractionDigits: 0 })}`;
}
function inteiro(v) {
  return Number(v).toLocaleString('pt-BR');
}
function pct(v) {
  return `${Number(v).toLocaleString('pt-BR', { minimumFractionDigits: 0, maximumFractionDigits: 1 })}%`;
}

function rotulosDoPonto(ponto, agrupamento, variosAnos) {
  if (agrupamento === 'MES') {
    const [a, m] = ponto.inicio.split('-');
    const mes = MES_CURTO[Number(m) - 1];
    return { rotulo: variosAnos ? `${mes}/${a.slice(2)}` : mes, rotuloLongo: `${mes}/${a}` };
  }
  if (agrupamento === 'SEMANA') {
    return { rotulo: diaMes(ponto.inicio), rotuloLongo: `Semana de ${diaMes(ponto.inicio)} a ${diaMes(ponto.fim)}` };
  }
  return { rotulo: diaMes(ponto.inicio), rotuloLongo: `${DIA_SEMANA_CURTO[paraData(ponto.inicio).getUTCDay()]}, ${dataCompleta(ponto.inicio)}` };
}

// ---------- pedaços da tela ----------

function Variacao({ valor, comparacao, invertido = false }) {
  if (valor == null) return <span className="kpi-variacao neutra">sem base para comparar</span>;
  const n = Number(valor);
  const bom = invertido ? n < 0 : n > 0;
  const classe = n === 0 ? 'neutra' : bom ? 'positiva' : 'negativa';
  const seta = n > 0 ? '▲' : n < 0 ? '▼' : '■';
  return (
    <span className={`kpi-variacao ${classe}`} title={`Comparado com ${periodoTexto(comparacao.inicio, comparacao.fim)}`}>
      <span aria-hidden="true">{seta}</span> {pct(Math.abs(n))} <span className="kpi-contra">vs período anterior</span>
    </span>
  );
}

function Kpi({ rotulo, valor, children }) {
  return (
    <div className="kpi">
      <div className="kpi-rotulo">{rotulo}</div>
      <div className="kpi-valor">{valor}</div>
      <div className="kpi-rodape">{children}</div>
    </div>
  );
}

function ListaParticipacao({ titulo, itens, rotulos }) {
  if (itens.length === 0) return null;
  return (
    <div className="participacao">
      <h4>{titulo}</h4>
      {itens.map((f) => (
        <div className="participacao-linha" key={f.chave}>
          <div className="participacao-texto">
            <span>{rotulos[f.chave] || f.chave}</span>
            <span className="participacao-valor">{moeda(f.faturamento)} <span className="suave">· {pct(f.percentualFaturamento)}</span></span>
          </div>
          <div className="medidor" aria-hidden="true"><span style={{ width: `${Math.max(1, Number(f.percentualFaturamento))}%` }} /></div>
        </div>
      ))}
    </div>
  );
}

function montarCsv(relatorio) {
  const linhas = [['Início', 'Fim', 'Faturamento (R$)', 'Pedidos concluídos', 'Ticket médio (R$)', 'Cancelados']];
  const numero = (v) => Number(v).toFixed(2).replace('.', ',');
  relatorio.serie.forEach((p) => linhas.push([dataCompleta(p.inicio), dataCompleta(p.fim), numero(p.faturamento), p.pedidosConcluidos, numero(p.ticketMedio), p.cancelados]));
  const csv = '﻿' + linhas.map((l) => l.join(';')).join('\r\n');
  const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }));
  const link = document.createElement('a');
  link.href = url;
  link.download = `vendas-${relatorio.inicio}-a-${relatorio.fim}.csv`;
  link.click();
  URL.revokeObjectURL(url);
}

// ---------- página ----------

export default function Relatorios() {
  const [filtro, setFiltro] = useState({ preset: '30d', inicio: null, fim: null, agrupamento: 'DIA' });
  const [personalizado, setPersonalizado] = useState({ inicio: '', fim: '' });

  const parametros = new URLSearchParams();
  if (filtro.inicio) parametros.set('inicio', filtro.inicio);
  if (filtro.fim) parametros.set('fim', filtro.fim);
  parametros.set('agrupamento', filtro.agrupamento);

  const [erroPeriodo, setErroPeriodo] = useState('');
  const [erroArquivo, setErroArquivo] = useState('');
  const [hoje, setHoje] = useState(null);
  // Primeiro dia que o plano deixa consultar: undefined até o primeiro relatório, null = sem limite.
  const [primeiroDia, setPrimeiroDia] = useState(undefined);
  const [bloqueio, setBloqueio] = useState(null);
  const ultimoRelatorio = useRef(null);

  const { data, error, isLoading, isFetching } = useQuery({
    queryKey: ['relatorio-vendas', parametros.toString()],
    queryFn: () => api.get(`/api/relatorios/vendas?${parametros}`),
    placeholderData: keepPreviousData,
  });

  // Se um pedido der erro, a tela continua mostrando o último relatório bom (e os filtros seguem usáveis).
  if (data) ultimoRelatorio.current = data;
  const r = data || ultimoRelatorio.current;

  useEffect(() => {
    if (data?.hoje) setHoje(data.hoje);
    if (data) setPrimeiroDia(data.primeiroDiaPermitido ?? null);
  }, [data]);

  const foraDoPlano = (inicio) => !!primeiroDia && inicio < primeiroDia;

  // Mesmo recorte do backend (PlanoSaas): até 1 ano atrás é Profissional; antes disso, Premium.
  function avisarForaDoPlano(inicio) {
    setBloqueio({
      planoNecessario: inicio >= somarDias(hoje, -364) ? 'PROFISSIONAL' : 'PREMIUM',
      mensagem: `Seu plano mostra relatórios a partir de ${dataCompleta(primeiroDia)}.`,
    });
  }

  async function baixarPlanilha() {
    setErroArquivo('');
    try {
      await salvarArquivo(`vendas-${r.inicio}-a-${r.fim}.csv`, montarCsv(r));
    } catch (e) {
      setErroArquivo(e.message);
    }
  }

  function aplicarPreset(p) {
    if (!hoje) return;
    const [inicio, fim] = p.periodo(hoje);
    setErroPeriodo('');
    if (foraDoPlano(inicio)) {
      avisarForaDoPlano(inicio);
      return;
    }
    setBloqueio(null);
    setFiltro({ preset: p.id, inicio, fim, agrupamento: p.agrupamento });
  }

  function aplicarPersonalizado(e) {
    e.preventDefault();
    const { inicio, fim } = personalizado;
    if (!inicio || !fim) return;
    if (fim < inicio) {
      setErroPeriodo('A data final não pode ser anterior à data inicial.');
      return;
    }
    if ((paraData(fim) - paraData(inicio)) / 86400000 + 1 > 366) {
      setErroPeriodo('Escolha um período de até 1 ano.');
      return;
    }
    setErroPeriodo('');
    if (foraDoPlano(inicio)) {
      avisarForaDoPlano(inicio);
      return;
    }
    setBloqueio(null);
    setFiltro({ preset: 'personalizado', inicio, fim, agrupamento: filtro.agrupamento });
  }

  const serie = useMemo(() => {
    if (!r) return [];
    const variosAnos = r.inicio.slice(0, 4) !== r.fim.slice(0, 4);
    return r.serie.map((p) => ({
      chave: p.inicio,
      valor: Number(p.faturamento),
      ...rotulosDoPonto(p, r.agrupamento, variosAnos),
      detalhes: [
        ['Pedidos', inteiro(p.pedidosConcluidos)],
        ['Ticket médio', moeda(p.ticketMedio)],
        ...(p.cancelados ? [['Cancelados', inteiro(p.cancelados)]] : []),
      ],
    }));
  }, [r]);

  const horas = useMemo(() => {
    if (!r) return [];
    const maior = Math.max(...r.porHora.map((h) => h.pedidos));
    return r.porHora.map((h) => ({
      chave: String(h.chave),
      valor: h.pedidos,
      rotulo: `${h.chave}h`,
      rotuloLongo: `${String(h.chave).padStart(2, '0')}:00 às ${String(h.chave).padStart(2, '0')}:59`,
      destaque: maior > 0 && h.pedidos === maior,
      detalhes: [['Vendido', moeda(h.faturamento)]],
    }));
  }, [r]);

  const dias = useMemo(() => (r ? r.porDiaDaSemana.map((d) => ({
    chave: String(d.chave),
    valor: Number(d.faturamento),
    rotulo: DIA_SEMANA[d.chave - 1].slice(0, 3).toLowerCase(),
    rotuloLongo: DIA_SEMANA[d.chave - 1],
    detalhes: [['Pedidos', inteiro(d.pedidos)]],
  })) : []), [r]);

  // O backend é quem decide; se ele recusar um período, o aviso é o mesmo da checagem local.
  const avisoPlano = bloqueio || (error?.status === 402 && error.dados?.upgradeNecessario
    ? { planoNecessario: error.dados.planoNecessario, mensagem: error.dados.mensagem }
    : null);

  const horaPico = horas.find((h) => h.destaque);
  const semVendas = r && r.resumo.pedidosConcluidos === 0;
  const tituloSerie = { DIA: 'por dia', SEMANA: 'por semana', MES: 'por mês' }[r?.agrupamento || 'DIA'];

  return (
    <div className="relatorios">
      <div className="page-header">
        <div>
          <h2>Relatórios</h2>
          <p>{r ? `Vendas de ${periodoTexto(r.inicio, r.fim)}. Só pedidos concluídos contam como venda.` : 'Vendas do seu restaurante.'}</p>
        </div>
        {r && !semVendas && (
          <button type="button" className="btn btn-secundario" onClick={baixarPlanilha}>Baixar planilha (CSV)</button>
        )}
      </div>

      <div className="filtros-relatorio">
        <div className="filtro-grupo" role="group" aria-label="Período">
          {PRESETS.map((p) => {
            const bloqueado = hoje && foraDoPlano(p.periodo(hoje)[0]);
            return (
              <button type="button" key={p.id} className={`chip ${filtro.preset === p.id ? 'ativo' : ''} ${bloqueado ? 'chip-bloqueado' : ''}`}
                onClick={() => aplicarPreset(p)} disabled={!hoje} title={bloqueado ? 'Fora do histórico do seu plano' : undefined}>
                {bloqueado && <Icone nome="cadeado" tamanho={12} />}
                {p.rotulo}
              </button>
            );
          })}
        </div>
        <form className="filtro-grupo periodo-livre" onSubmit={aplicarPersonalizado}>
          <label htmlFor="rel-inicio" className="sr-only">Data inicial</label>
          <input id="rel-inicio" type="date" min={primeiroDia || undefined} value={personalizado.inicio} onChange={(e) => setPersonalizado({ ...personalizado, inicio: e.target.value })} />
          <span className="suave">a</span>
          <label htmlFor="rel-fim" className="sr-only">Data final</label>
          <input id="rel-fim" type="date" value={personalizado.fim} onChange={(e) => setPersonalizado({ ...personalizado, fim: e.target.value })} />
          <button type="submit" className={`chip ${filtro.preset === 'personalizado' ? 'ativo' : ''}`} disabled={!personalizado.inicio || !personalizado.fim}>Aplicar</button>
        </form>
        <div className="filtro-grupo segmentado" role="group" aria-label="Agrupar por">
          {AGRUPAMENTOS.map(([valor, rotulo]) => (
            <button type="button" key={valor} aria-pressed={filtro.agrupamento === valor} onClick={() => setFiltro({ ...filtro, agrupamento: valor })}>
              {rotulo}
            </button>
          ))}
        </div>
      </div>

      {avisoPlano && (
        <RecursoBloqueado
          compacto
          planoNecessario={avisoPlano.planoNecessario}
          titulo="Esse período está fora do seu plano"
          mensagem={`${avisoPlano.mensagem} Nada foi apagado: o histórico completo aparece ao mudar de plano.`}
        />
      )}
      {(erroPeriodo || (error && error.status !== 402)) && (
        <div className="erro" role="alert">{erroPeriodo || error.message}</div>
      )}
      {erroArquivo && (
        <div className="erro" role="alert">{erroArquivo}</div>
      )}
      {isLoading && <p>Carregando...</p>}

      {r && (
        <div className={`relatorio-conteudo ${isFetching ? 'atualizando' : ''}`}>
          <section className="kpis" aria-label="Resumo do período">
            <Kpi rotulo="Faturamento" valor={moeda(r.resumo.faturamento)}>
              <Variacao valor={r.comparacao.variacaoFaturamento} comparacao={r.comparacao} />
            </Kpi>
            <Kpi rotulo="Pedidos concluídos" valor={inteiro(r.resumo.pedidosConcluidos)}>
              <Variacao valor={r.comparacao.variacaoPedidos} comparacao={r.comparacao} />
            </Kpi>
            <Kpi rotulo="Ticket médio" valor={moeda(r.resumo.ticketMedio)}>
              <Variacao valor={r.comparacao.variacaoTicketMedio} comparacao={r.comparacao} />
            </Kpi>
            <Kpi rotulo="Cancelamentos" valor={pct(r.resumo.taxaCancelamento)}>
              <span className="kpi-detalhe">
                {inteiro(r.resumo.cancelados)} de {inteiro(r.resumo.pedidosRecebidos)} pedidos
                {r.resumo.cancelados > 0 && ` · ${inteiro(r.resumo.canceladosPeloRestaurante)} pelo restaurante`}
              </span>
            </Kpi>
            <Kpi rotulo="Clientes" valor={inteiro(r.resumo.clientesUnicos)}>
              <span className="kpi-detalhe">{inteiro(r.resumo.clientesNovos)} novos · {inteiro(r.resumo.clientesRecorrentes)} voltaram</span>
            </Kpi>
          </section>

          {semVendas ? (
            <div className="panel estado-vazio" style={{ padding: 40 }}>
              Nenhuma venda concluída neste período. Escolha outro período acima.
            </div>
          ) : (
            <>
              <section className="panel cartao-grafico">
                <div className="cartao-grafico-topo">
                  <h3>Faturamento {tituloSerie}</h3>
                  <span className="suave">{inteiro(r.resumo.itensVendidos)} itens vendidos</span>
                </div>
                <GraficoColunas titulo={`Faturamento ${tituloSerie}`} dados={serie} formatarValor={moeda} formatarEixo={moedaEixo} altura={260} />
              </section>

              <div className="grade-relatorio">
                <section className="panel cartao-grafico">
                  <div className="cartao-grafico-topo">
                    <h3>Horários de pico</h3>
                    {horaPico && <span className="suave">Mais pedidos às {horaPico.rotuloLongo.slice(0, 5)}</span>}
                  </div>
                  <GraficoColunas
                    titulo="Pedidos por hora do dia"
                    dados={horas}
                    formatarValor={(v) => `${inteiro(v)} ${v === 1 ? 'pedido' : 'pedidos'}`}
                    formatarEixo={inteiro}
                  />
                </section>
                <section className="panel cartao-grafico">
                  <div className="cartao-grafico-topo"><h3>Dias da semana</h3></div>
                  <GraficoColunas titulo="Faturamento por dia da semana" dados={dias} formatarValor={moeda} formatarEixo={moedaEixo} />
                </section>
              </div>

              <div className="grade-relatorio">
                <section className="panel cartao-grafico">
                  <div className="cartao-grafico-topo"><h3>Produtos mais vendidos</h3></div>
                  <table className="tabela-produtos">
                    <thead>
                      <tr><th>Produto</th><th className="num">Qtd.</th><th className="num">Vendido</th><th className="coluna-medidor"><span className="sr-only">Participação</span></th></tr>
                    </thead>
                    <tbody>
                      {r.produtos.map((p, i) => (
                        <tr key={p.produtoId}>
                          <td><span className="posicao">{i + 1}</span> {p.nome}</td>
                          <td className="num">{inteiro(p.quantidade)}</td>
                          <td className="num">{moeda(p.faturamento)}</td>
                          <td className="coluna-medidor">
                            <div className="medidor" title={`${pct(p.percentualFaturamento)} do faturamento`}>
                              <span style={{ width: `${Math.max(1, Number(p.percentualFaturamento))}%` }} />
                            </div>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </section>
                <section className="panel cartao-grafico">
                  <div className="cartao-grafico-topo"><h3>Como seus clientes compram</h3></div>
                  <ListaParticipacao titulo="Modalidade" itens={r.porModalidade} rotulos={MODALIDADE_LABEL} />
                  <ListaParticipacao titulo="Forma de pagamento" itens={r.porFormaPagamento} rotulos={PAGAMENTO_LABEL} />
                </section>
              </div>

              <details className="panel tabela-serie">
                <summary>Ver os números {tituloSerie} em tabela</summary>
                <table>
                  <thead>
                    <tr><th>Período</th><th className="num">Faturamento</th><th className="num">Pedidos</th><th className="num">Ticket médio</th><th className="num">Cancelados</th></tr>
                  </thead>
                  <tbody>
                    {r.serie.map((p, i) => (
                      <tr key={p.inicio}>
                        <td>{serie[i].rotuloLongo}</td>
                        <td className="num">{moeda(p.faturamento)}</td>
                        <td className="num">{inteiro(p.pedidosConcluidos)}</td>
                        <td className="num">{moeda(p.ticketMedio)}</td>
                        <td className="num">{inteiro(p.cancelados)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </details>
            </>
          )}
        </div>
      )}
    </div>
  );
}
