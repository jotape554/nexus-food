/**
 * Ícones de traço simples, desenhados em SVG para ficarem iguais em qualquer aparelho
 * (emoji muda de cara em cada sistema). Herdam a cor do texto.
 */
const CAMINHOS = {
  pessoa: <><circle cx="12" cy="8" r="4" /><path d="M4 21c0-4 4-6 8-6s8 2 8 6" /></>,
  entrega: <><path d="M3 6h11v10H3z" /><path d="M14 10h4l3 3v3h-7" /><circle cx="7" cy="18" r="2" /><circle cx="17" cy="18" r="2" /></>,
  sacola: <><path d="M5 8h14l-1 13H6z" /><path d="M9 8V6a3 3 0 0 1 6 0v2" /></>,
  mesa: <><path d="M3 9h18" /><path d="M6 9v11M18 9v11" /><path d="M8 5h8" /></>,
  dinheiro: <><rect x="3" y="6" width="18" height="12" rx="2" /><circle cx="12" cy="12" r="2.5" /></>,
  relogio: <><circle cx="12" cy="12" r="9" /><path d="M12 7v5l3 2" /></>,
  local: <><path d="M12 21s-7-6.2-7-11a7 7 0 0 1 14 0c0 4.8-7 11-7 11z" /><circle cx="12" cy="10" r="2.5" /></>,
  cartao: <><rect x="3" y="5" width="18" height="14" rx="2" /><path d="M3 10h18" /></>,
  pix: <><path d="M12 3l9 9-9 9-9-9z" /><path d="M8 12h8" /></>,
  nota: <><path d="M6 3h9l4 4v14H6z" /><path d="M9 12h7M9 16h5" /></>,
  som: <><path d="M4 9h4l5-4v14l-5-4H4z" /><path d="M17 9a4 4 0 0 1 0 6" /></>,
  semSom: <><path d="M4 9h4l5-4v14l-5-4H4z" /><path d="M17 10l4 4M21 10l-4 4" /></>,
  check: <path d="M5 12l5 5 9-10" />,
};

export default function Icone({ nome, tamanho = 16, className = '' }) {
  return (
    <svg
      className={`icone ${className}`}
      width={tamanho}
      height={tamanho}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      {CAMINHOS[nome]}
    </svg>
  );
}

export const ICONE_MODALIDADE = { RETIRADA: 'sacola', ENTREGA: 'entrega', CONSUMO_LOCAL: 'mesa' };
export const ICONE_PAGAMENTO = { PIX: 'pix', DINHEIRO: 'dinheiro', CARTAO_CREDITO: 'cartao', CARTAO_DEBITO: 'cartao' };
