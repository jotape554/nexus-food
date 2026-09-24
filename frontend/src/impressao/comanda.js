import { agruparOpcoes } from '../api/opcoes';
import { moeda, numeroPedido, telefone, MODALIDADE_LABEL, PAGAMENTO_LABEL } from '../api/formato';

/**
 * Comanda para impressora térmica (58 mm / 80 mm) ou folha comum, montada como uma página HTML
 * própria: o visual do painel não entra na impressão.
 *
 * Via da cozinha: o que preparar, grande, sem preços. Via de entrega/balcão: completa, com
 * cliente, endereço, valores e pagamento. Tudo que veio do cliente passa por `esc` (é texto
 * digitado por quem fez o pedido).
 */

const esc = (v) => String(v ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

const dataHora = (iso) => new Date(iso).toLocaleString('pt-BR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' });
const hora = (iso) => new Date(iso).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });

function itens(pedido, comPreco) {
  return pedido.itens.map((item) => `
    <div class="item">
      <div class="linha"><span class="item-nome">${item.quantidade}x ${esc(item.nomeProduto)}</span>${comPreco ? `<span>${moeda(item.subtotal)}</span>` : ''}</div>
      ${agruparOpcoes(item.opcoes || []).map((g) => `<div class="opcao">${esc(g.grupo)}: ${esc(g.nomes.join(', '))}</div>`).join('')}
      ${item.observacao ? `<div class="obs">&gt;&gt; ${esc(item.observacao)}</div>` : ''}
    </div>`).join('');
}

function cabecalho(pedido, restaurante, titulo) {
  return `
    <div class="c restaurante">${esc(restaurante?.nome || '')}</div>
    <div class="c via">${titulo}</div>
    <hr>
    <div class="c numero">PEDIDO ${numeroPedido(pedido.numeroDia)}</div>
    <div class="c modalidade">${esc(MODALIDADE_LABEL[pedido.modalidade] || pedido.modalidade).toUpperCase()}</div>
    <div class="c">${dataHora(pedido.criadoEm)}${pedido.prontoPrevistoPara ? ` · pronto ~${hora(pedido.prontoPrevistoPara)}` : ''}</div>
    <hr>`;
}

function viaCozinha(pedido, restaurante) {
  return `
  <section class="via-cozinha">
    ${cabecalho(pedido, restaurante, 'VIA DA COZINHA')}
    <div class="cliente-curto">${esc(pedido.cliente?.nome || '')}</div>
    <hr class="fino">
    ${itens(pedido, false)}
    ${pedido.observacao ? `<hr><div class="obs-geral">OBS: ${esc(pedido.observacao)}</div>` : ''}
    <hr>
    <div class="c rodape">Nexus Food</div>
  </section>`;
}

function viaEntrega(pedido, restaurante) {
  const troco = pedido.trocoPara ? Number(pedido.trocoPara) - Number(pedido.total) : null;
  return `
  <section class="via-entrega">
    ${cabecalho(pedido, restaurante, pedido.modalidade === 'ENTREGA' ? 'VIA DE ENTREGA' : 'VIA DO BALCÃO')}
    <div class="bloco">
      <div><b>${esc(pedido.cliente?.nome || '')}</b></div>
      ${pedido.cliente?.telefone ? `<div>${esc(telefone(pedido.cliente.telefone))}</div>` : ''}
      ${pedido.enderecoEntrega ? `<div class="endereco">${esc(pedido.enderecoEntrega)}</div>` : ''}
      ${pedido.bairroEntrega ? `<div class="endereco">${esc(pedido.bairroEntrega)}</div>` : ''}
    </div>
    <hr>
    ${itens(pedido, true)}
    <hr>
    <div class="linha"><span>Subtotal</span><span>${moeda(pedido.subtotal)}</span></div>
    ${Number(pedido.taxaEntrega) > 0 ? `<div class="linha"><span>Taxa de entrega</span><span>${moeda(pedido.taxaEntrega)}</span></div>` : ''}
    <div class="linha total"><span>TOTAL</span><span>${moeda(pedido.total)}</span></div>
    <hr class="fino">
    <div class="linha"><span>Pagamento</span><span>${esc(PAGAMENTO_LABEL[pedido.formaPagamento] || pedido.formaPagamento)}</span></div>
    ${pedido.trocoPara ? `<div class="linha"><span>Paga com</span><span>${moeda(pedido.trocoPara)}</span></div>
      <div class="linha destaque"><span>Levar de troco</span><span>${moeda(troco)}</span></div>` : ''}
    <div class="c pago">Pagamento na ${pedido.modalidade === 'ENTREGA' ? 'entrega' : 'retirada'} · não cobrado online</div>
    ${pedido.observacao ? `<hr><div class="obs-geral">OBS: ${esc(pedido.observacao)}</div>` : ''}
    <hr>
    <div class="c rodape">Pedido feito pelo cardápio digital<br>Nexus Food</div>
  </section>`;
}

const LARGURA = { 80: '72mm', 58: '48mm', A4: '100mm' };

function estilos(papel) {
  const pequeno = papel === '58';
  return `
  @page { size: ${papel === 'A4' ? 'A4' : `${papel}mm auto`}; margin: ${papel === 'A4' ? '12mm' : '0'}; }
  * { box-sizing: border-box; }
  html, body { margin: 0; padding: 0; background: #fff; color: #000; }
  body { font-family: 'Courier New', ui-monospace, monospace; font-size: ${pequeno ? '11px' : '12.5px'}; line-height: 1.3;
         -webkit-print-color-adjust: exact; print-color-adjust: exact; }
  section { width: ${LARGURA[papel] || '72mm'}; padding: 3mm ${papel === 'A4' ? '0' : '2mm'} 6mm; }
  section + section { page-break-before: always; break-before: page; }
  hr { border: none; border-top: 1px dashed #000; margin: 5px 0; }
  hr.fino { border-top-style: dotted; }
  .c { text-align: center; }
  .restaurante { font-size: 1.15em; font-weight: 700; }
  .via { font-size: 0.85em; letter-spacing: 0.08em; }
  .numero { font-size: ${pequeno ? '1.6em' : '1.9em'}; font-weight: 800; letter-spacing: 0.02em; }
  .modalidade { font-size: 1.2em; font-weight: 800; border: 2px solid #000; margin: 3px auto; padding: 1px 4px; display: table; }
  .linha { display: flex; justify-content: space-between; gap: 6px; }
  .linha span:last-child { white-space: nowrap; }
  .item { margin: 4px 0 6px; }
  .item-nome { font-weight: 700; }
  .via-cozinha .item-nome { font-size: 1.25em; }
  .via-cozinha .opcao { font-size: 1.1em; }
  .opcao { padding-left: 2.2em; }
  .obs { padding-left: 2.2em; font-weight: 700; }
  .obs-geral { font-weight: 700; }
  .cliente-curto { font-weight: 700; }
  .endereco { font-weight: 700; }
  .total { font-size: 1.25em; font-weight: 800; margin-top: 2px; }
  .destaque { font-weight: 800; }
  .pago { font-size: 0.85em; margin-top: 4px; }
  .rodape { font-size: 0.8em; }
  `;
}

/** Página completa da comanda (uma ou duas vias). */
export function montarComanda(pedido, restaurante, config) {
  const vias = [];
  if (config.vias !== 'entrega') vias.push(viaCozinha(pedido, restaurante));
  if (config.vias !== 'cozinha') vias.push(viaEntrega(pedido, restaurante));
  return `<!doctype html><html lang="pt-BR"><head><meta charset="utf-8"><title>Pedido ${numeroPedido(pedido.numeroDia)}</title>
    <style>${estilos(config.papel)}</style></head><body>${vias.join('')}</body></html>`;
}

// Impressões em fila: vários pedidos chegando juntos saem um depois do outro.
let fila = Promise.resolve();

/**
 * Imprime numa moldura escondida com a página da comanda. O navegador abre a janela de
 * impressão (ou imprime direto, no Chrome aberto com --kiosk-printing).
 */
export function imprimirPedido(pedido, restaurante, config) {
  const html = montarComanda(pedido, restaurante, config);
  fila = fila.then(() => new Promise((terminou) => {
    const moldura = document.createElement('iframe');
    moldura.className = 'nexus-impressao';
    moldura.setAttribute('aria-hidden', 'true');
    moldura.style.cssText = 'position:fixed;right:0;bottom:0;width:0;height:0;border:0;visibility:hidden;';
    document.body.appendChild(moldura);
    const doc = moldura.contentDocument;
    doc.open();
    doc.write(html);
    doc.close();

    let fim = false;
    const encerrar = () => {
      if (fim) return;
      fim = true;
      setTimeout(() => { moldura.remove(); terminou(); }, 1500);
    };
    moldura.contentWindow.addEventListener('afterprint', encerrar);
    setTimeout(() => {
      try {
        moldura.contentWindow.focus();
        moldura.contentWindow.print();
      } catch { /* navegador sem impressão (ex.: pré-visualização embutida): só encerra */ }
      encerrar();
    }, 80);
  }));
  return fila;
}
