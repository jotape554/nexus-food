import { useEffect, useLayoutEffect, useRef, useState } from 'react';

/**
 * Linha de uma série só numa escala fixa (ex.: nota 0–100), com linhas de referência discretas.
 * Pontos sem valor (null) quebram a linha. A linha vertical de mira acompanha o mouse e mostra
 * o ponto mais próximo; pelo teclado, as setas andam entre os pontos.
 *
 * dados: [{ chave, rotulo, rotuloLongo, valor|null, detalhe }]
 * referencias: [{ valor, rotulo }]
 */
const MARGEM = { topo: 12, direita: 12, baixo: 26, esquerda: 36 };

export default function GraficoLinha({ dados, minimo = 0, maximo = 100, referencias = [], altura = 220, titulo, formatarValor = String }) {
  const caixaRef = useRef(null);
  const [largura, setLargura] = useState(600);
  const [ativo, setAtivo] = useState(null);

  useLayoutEffect(() => {
    if (caixaRef.current) setLargura(caixaRef.current.clientWidth);
  }, []);
  useEffect(() => {
    const el = caixaRef.current;
    if (!el || typeof ResizeObserver === 'undefined') return undefined;
    const obs = new ResizeObserver(([e]) => setLargura(e.contentRect.width));
    obs.observe(el);
    return () => obs.disconnect();
  }, []);

  const areaL = Math.max(10, largura - MARGEM.esquerda - MARGEM.direita);
  const areaA = altura - MARGEM.topo - MARGEM.baixo;
  const passo = dados.length > 1 ? areaL / (dados.length - 1) : 0;
  const x = (i) => MARGEM.esquerda + (dados.length > 1 ? passo * i : areaL / 2);
  const y = (v) => MARGEM.topo + areaA - ((v - minimo) / (maximo - minimo)) * areaA;

  // Segmentos contínuos (null quebra a linha).
  const segmentos = [];
  let atual = [];
  dados.forEach((d, i) => {
    if (d.valor == null) {
      if (atual.length) segmentos.push(atual);
      atual = [];
    } else {
      atual.push([x(i), y(d.valor)]);
    }
  });
  if (atual.length) segmentos.push(atual);

  const cadaQuantos = Math.max(1, Math.ceil(56 / Math.max(1, passo)));
  const ultimoComValor = [...dados].map((d, i) => [d, i]).reverse().find(([d]) => d.valor != null);

  function aoMover(e) {
    const r = caixaRef.current.getBoundingClientRect();
    const px = e.clientX - r.left;
    const i = dados.length > 1 ? Math.round((px - MARGEM.esquerda) / passo) : 0;
    setAtivo(Math.max(0, Math.min(dados.length - 1, i)));
  }

  function aoTeclar(e) {
    if (e.key === 'ArrowRight') setAtivo((a) => Math.min(dados.length - 1, (a ?? dados.length - 1) + 1));
    if (e.key === 'ArrowLeft') setAtivo((a) => Math.max(0, (a ?? dados.length - 1) - 1));
  }

  const item = ativo != null ? dados[ativo] : null;

  return (
    <div
      className="grafico"
      ref={caixaRef}
      tabIndex={0}
      role="img"
      aria-label={titulo}
      onMouseMove={aoMover}
      onMouseLeave={() => setAtivo(null)}
      onFocus={() => setAtivo(dados.length - 1)}
      onBlur={() => setAtivo(null)}
      onKeyDown={aoTeclar}
    >
      <svg width={largura} height={altura}>
        {referencias.map((ref) => (
          <g key={ref.valor}>
            <line className="grafico-grade" x1={MARGEM.esquerda} x2={largura - MARGEM.direita} y1={y(ref.valor)} y2={y(ref.valor)} />
            <text className="grafico-eixo" x={MARGEM.esquerda - 6} y={y(ref.valor)} dy="0.32em" textAnchor="end">{ref.rotulo}</text>
          </g>
        ))}
        {segmentos.map((pts, i) => (
          <g key={i}>
            <path className="grafico-area" d={`M${pts[0][0]},${y(minimo)} ${pts.map((p) => `L${p[0]},${p[1]}`).join(' ')} L${pts[pts.length - 1][0]},${y(minimo)}Z`} />
            <path className="grafico-linha" d={pts.map((p, j) => `${j ? 'L' : 'M'}${p[0]},${p[1]}`).join(' ')} />
          </g>
        ))}
        {dados.map((d, i) => ((i % cadaQuantos === 0 && dados.length - 1 - i >= cadaQuantos) || i === dados.length - 1) && (
          <text key={d.chave} className="grafico-eixo" x={x(i)} y={altura - 8} textAnchor={i === dados.length - 1 ? 'end' : 'middle'}>{d.rotulo}</text>
        ))}
        {ultimoComValor && (
          <circle className="grafico-ponto" cx={x(ultimoComValor[1])} cy={y(ultimoComValor[0].valor)} r="5" />
        )}
        {item && (
          <g>
            <line className="grafico-mira" x1={x(ativo)} x2={x(ativo)} y1={MARGEM.topo} y2={MARGEM.topo + areaA} />
            {item.valor != null && <circle className="grafico-ponto" cx={x(ativo)} cy={y(item.valor)} r="5" />}
          </g>
        )}
      </svg>
      {item && (
        <div className="grafico-dica" style={{ left: Math.min(Math.max(x(ativo), 90), largura - 90), top: item.valor != null ? Math.max(0, y(item.valor) - 12) : MARGEM.topo + 20 }}>
          <strong>{item.valor != null ? formatarValor(item.valor) : 'Sem nota'}</strong>
          <span className="grafico-dica-titulo">{item.rotuloLongo || item.rotulo}</span>
          {item.detalhe && <span className="grafico-dica-linha"><span>{item.detalhe}</span></span>}
        </div>
      )}
    </div>
  );
}
