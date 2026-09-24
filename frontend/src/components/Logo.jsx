/**
 * Logo do Nexus Food: símbolo (cloche de serviço sobre o prato, num quadrado laranja) +
 * nome em Space Grotesk, a mesma família tipográfica da Nexus Sistemas.
 * tom: "escuro" sobre fundo escuro, "claro" sobre fundo claro. soSimbolo: só o quadrado.
 */
export function SimboloNexusFood({ tamanho = 32 }) {
  return (
    <svg className="logo-simbolo" width={tamanho} height={tamanho} viewBox="0 0 32 32" aria-hidden="true">
      <rect width="32" height="32" rx="8" fill="#D9622B" />
      <circle cx="16" cy="9.6" r="1.7" fill="#FFF8F1" />
      <path d="M6.8 21.2a9.2 9.2 0 0 1 18.4 0z" fill="#FFF8F1" />
      <path d="M11.2 18.6a5.4 5.4 0 0 1 3.1-3.9" fill="none" stroke="#D9622B" strokeWidth="1.5" strokeLinecap="round" />
      <rect x="5" y="22.6" width="22" height="2.4" rx="1.2" fill="#FFF8F1" />
    </svg>
  );
}

export default function Logo({ tom = 'escuro', tamanho = 30, soSimbolo = false, className = '' }) {
  return (
    <span className={`logo logo-${tom} ${className}`} role="img" aria-label="Nexus Food">
      <SimboloNexusFood tamanho={tamanho} />
      {!soSimbolo && (
        <span className="logo-nome" aria-hidden="true">Nexus<span>Food</span></span>
      )}
    </span>
  );
}
