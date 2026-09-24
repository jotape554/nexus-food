import { useEffect, useLayoutEffect, useRef, useState } from 'react';

/**
 * Gráfico de colunas de uma série só (magnitude). Regras de desenho:
 * colunas de no máximo 24px com topo arredondado (4px) e base reta, uma cor, grade de linha
 * fina e discreta, um eixo, valor exato na dica ao passar o mouse ou focar pelo teclado.
 * A coluna inteira (altura toda da faixa) é a área de toque, não só a barra pintada.
 *
 * dados: [{ chave, rotulo, rotuloLongo, valor, detalhes: [[nome, texto], ...], destaque }]
 */
const ALTURA_PADRAO = 220;
const MARGEM = { topo: 12, direita: 8, baixo: 26, esquerda: 56 };
const LARGURA_MAX_COLUNA = 24;
const RAIO = 4;

function passoBonito(bruto) {
  const potencia = 10 ** Math.floor(Math.log10(bruto));
  const f = bruto / potencia;
  return (f <= 1 ? 1 : f <= 2 ? 2 : f <= 2.5 ? 2.5 : f <= 5 ? 5 : 10) * potencia;
}

function escala(maximo, marcas = 4) {
  if (!maximo || maximo <= 0) return { topo: 1, ticks: [0] };
  const passo = passoBonito(maximo / marcas);
  const topo = Math.ceil(maximo / passo) * passo;
  const ticks = [];
  for (let v = 0; v <= topo + passo / 2; v += passo) ticks.push(Number(v.toFixed(6)));
  return { topo, ticks };
}

/** Coluna com o topo arredondado e a base reta, crescendo a partir da linha de base. */
function caminhoColuna(x, y, largura, altura) {
  if (altura <= 0) return '';
  const r = Math.min(RAIO, largura / 2, altura);
  return `M${x},${y + altura}V${y + r}Q${x},${y} ${x + r},${y}H${x + largura - r}Q${x + largura},${y} ${x + largura},${y + r}V${y + altura}Z`;
}

export default function GraficoColunas({ dados, formatarValor, formatarEixo = formatarValor, altura = ALTURA_PADRAO, titulo }) {
  const caixaRef = useRef(null);
  const [largura, setLargura] = useState(600);
  const [ativo, setAtivo] = useState(null);

  useLayoutEffect(() => {
    if (caixaRef.current) setLargura(caixaRef.current.clientWidth);
  }, []);

  useEffect(() => {
    const el = caixaRef.current;
    if (!el || typeof ResizeObserver === 'undefined') return undefined;
    const obs = new ResizeObserver(([entrada]) => setLargura(entrada.contentRect.width));
    obs.observe(el);
    return () => obs.disconnect();
  }, []);

  const areaLargura = Math.max(10, largura - MARGEM.esquerda - MARGEM.direita);
  const areaAltura = altura - MARGEM.topo - MARGEM.baixo;
  const maximo = Math.max(0, ...dados.map((d) => d.valor));
  const { topo, ticks } = escala(maximo);
  const faixa = areaLargura / Math.max(1, dados.length);
  const colunaLargura = Math.max(2, Math.min(LARGURA_MAX_COLUNA, faixa - 2));
  // Rótulos do eixo X sem encavalar: mostra 1 a cada N conforme o espaço.
  const cadaQuantos = Math.max(1, Math.ceil(44 / faixa));
  const y = (v) => MARGEM.topo + areaAltura - (v / topo) * areaAltura;

  const itemAtivo = ativo != null ? dados[ativo] : null;
  const xAtivo = ativo != null ? MARGEM.esquerda + faixa * ativo + faixa / 2 : 0;

  return (
    <div className="grafico" ref={caixaRef} onMouseLeave={() => setAtivo(null)}>
      <svg width={largura} height={altura} role="img" aria-label={titulo}>
        {ticks.map((t) => (
          <g key={t}>
            <line className="grafico-grade" x1={MARGEM.esquerda} x2={largura - MARGEM.direita} y1={y(t)} y2={y(t)} />
            <text className="grafico-eixo" x={MARGEM.esquerda - 8} y={y(t)} dy="0.32em" textAnchor="end">{formatarEixo(t)}</text>
          </g>
        ))}

        {dados.map((d, i) => {
          const x = MARGEM.esquerda + faixa * i;
          const alturaColuna = (d.valor / topo) * areaAltura;
          const selecionado = ativo === i;
          return (
            <g
              key={d.chave}
              tabIndex={0}
              role="img"
              aria-label={`${d.rotuloLongo || d.rotulo}: ${formatarValor(d.valor)}`}
              onMouseEnter={() => setAtivo(i)}
              onFocus={() => setAtivo(i)}
              onBlur={() => setAtivo(null)}
              className={`grafico-coluna ${selecionado ? 'ativa' : ''} ${d.destaque ? 'destaque' : ''}`}
            >
              <rect className="grafico-alvo" x={x} y={MARGEM.topo} width={faixa} height={areaAltura} />
              <path d={caminhoColuna(x + (faixa - colunaLargura) / 2, y(d.valor), colunaLargura, alturaColuna)} />
              {i % cadaQuantos === 0 && (
                <text className="grafico-eixo" x={x + faixa / 2} y={altura - 8} textAnchor="middle">{d.rotulo}</text>
              )}
            </g>
          );
        })}

        <line className="grafico-base" x1={MARGEM.esquerda} x2={largura - MARGEM.direita} y1={y(0)} y2={y(0)} />
      </svg>

      {itemAtivo && (
        <div
          className="grafico-dica"
          style={{
            left: Math.min(Math.max(xAtivo, 90), largura - 90),
            top: Math.max(0, y(itemAtivo.valor) - 8),
          }}
          role="status"
        >
          <strong>{formatarValor(itemAtivo.valor)}</strong>
          <span className="grafico-dica-titulo">{itemAtivo.rotuloLongo || itemAtivo.rotulo}</span>
          {itemAtivo.detalhes?.map(([nome, texto]) => (
            <span key={nome} className="grafico-dica-linha"><span>{nome}</span><span>{texto}</span></span>
          ))}
        </div>
      )}
    </div>
  );
}
