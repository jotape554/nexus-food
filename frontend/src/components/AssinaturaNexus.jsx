import { MARCA } from '../marca';

/**
 * "Um produto Nexus Sistemas": a assinatura da empresa por trás do Nexus Food, no mesmo
 * desenho do site da Nexus (ponto em degradê + Space Grotesk). Discreta de propósito: em
 * telas do cliente final o protagonista é o restaurante.
 *
 * tom: "escuro" sobre fundo escuro (menu lateral, barra da demo), "claro" sobre fundo claro.
 */
export default function AssinaturaNexus({ tom = 'claro', prefixo = 'Um produto', className = '' }) {
  return (
    <a
      className={`assinatura-nexus ${tom} ${className}`}
      href={MARCA.instagram}
      target="_blank"
      rel="noreferrer"
      title={`${MARCA.empresa} no Instagram (${MARCA.instagramRotulo})`}
    >
      <span className="assinatura-texto">{prefixo}</span>
      <span className="assinatura-marca"><span className="ponto-nexus" aria-hidden="true" />{MARCA.empresa}</span>
    </a>
  );
}
