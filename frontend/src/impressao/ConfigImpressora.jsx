import { useMemo, useState } from 'react';
import Modal from '../components/Modal';
import { gravarConfig, lerConfig } from './config';
import { imprimirPedido, montarComanda } from './comanda';

/** Pedido de exemplo para a prévia quando ainda não há pedido na tela. */
function pedidoExemplo() {
  const agora = new Date();
  return {
    id: 0, numeroDia: 12, status: 'CONFIRMADO', modalidade: 'ENTREGA', formaPagamento: 'DINHEIRO',
    criadoEm: agora.toISOString(), prontoPrevistoPara: new Date(agora.getTime() + 35 * 60000).toISOString(),
    cliente: { nome: 'Cliente de exemplo', telefone: '5511987650000' },
    enderecoEntrega: 'Rua de Exemplo, 100 — apto 12', bairroEntrega: 'Centro',
    itens: [
      { quantidade: 1, nomeProduto: 'Pizza grande', subtotal: 71.9, observacao: 'Bem assada',
        opcoes: [{ grupo: 'Tamanho', nome: 'Grande' }, { grupo: 'Borda', nome: 'Catupiry' }] },
      { quantidade: 2, nomeProduto: 'Refrigerante lata', subtotal: 13, opcoes: [] },
    ],
    subtotal: 84.9, taxaEntrega: 7, total: 91.9, trocoPara: 100, observacao: 'Interfone quebrado',
  };
}

const OPCOES = {
  papel: [['80', 'Térmica 80 mm'], ['58', 'Térmica 58 mm'], ['A4', 'Folha comum (A4)']],
  vias: [['ambas', 'Cozinha + entrega'], ['cozinha', 'Só a da cozinha'], ['entrega', 'Só a completa']],
  automatico: [['aceitar', 'Ao aceitar o pedido'], ['chegar', 'Assim que o pedido chega'], ['nao', 'Só quando eu clicar']],
};

const LARGURA_PREVIA = { 80: 302, 58: 219, A4: 400 };

function Escolha({ titulo, campo, config, onChange }) {
  return (
    <fieldset className="escolha-impressao">
      <legend>{titulo}</legend>
      <div className="segmentado" role="group" aria-label={titulo}>
        {OPCOES[campo].map(([valor, rotulo]) => (
          <button key={valor} type="button" aria-pressed={config[campo] === valor} onClick={() => onChange({ ...config, [campo]: valor })}>
            {rotulo}
          </button>
        ))}
      </div>
    </fieldset>
  );
}

/**
 * Impressora deste computador: papel, vias e quando imprimir sozinho, com prévia da comanda.
 * Fica guardado só neste aparelho (cada balcão tem a sua impressora).
 */
export default function ConfigImpressora({ pedido, restaurante, onFechar }) {
  const [config, setConfig] = useState(lerConfig);
  const exemplo = pedido || pedidoExemplo();
  const html = useMemo(() => montarComanda(exemplo, restaurante, config), [exemplo, restaurante, config]);

  function mudar(nova) {
    setConfig(nova);
    gravarConfig(nova);
  }

  return (
    <Modal titulo="Impressora deste computador" onFechar={onFechar} largo>
      <div className="config-impressora">
        <div className="config-impressora-opcoes">
          <Escolha titulo="Papel" campo="papel" config={config} onChange={mudar} />
          <Escolha titulo="O que imprimir" campo="vias" config={config} onChange={mudar} />
          <Escolha titulo="Imprimir sozinho" campo="automatico" config={config} onChange={mudar} />
          <p className="dica-campo">
            Funciona com qualquer impressora instalada no computador, inclusive as térmicas de cupom.
            Na janela de impressão, escolha a impressora e deixe as margens em "Nenhuma".
          </p>
          <p className="dica-campo">
            Para sair direto, sem a janela: abra o painel no Google Chrome com a opção
            <code> --kiosk-printing</code> (no atalho do Chrome, em Propriedades → Destino) e deixe a
            impressora térmica como padrão do computador.
          </p>
          <div className="modal-acoes" style={{ justifyContent: 'flex-start' }}>
            <button type="button" className="btn btn-latao" onClick={() => imprimirPedido(exemplo, restaurante, config)}>
              Imprimir {pedido ? 'este pedido' : 'teste'}
            </button>
            <button type="button" className="btn btn-secundario" onClick={onFechar}>Fechar</button>
          </div>
        </div>
        <div className="config-impressora-previa">
          <span className="rotulo-secao">Prévia {pedido ? '' : '(exemplo)'}</span>
          <iframe
            title="Prévia da comanda"
            srcDoc={html}
            style={{ width: LARGURA_PREVIA[config.papel] }}
            sandbox=""
          />
        </div>
      </div>
    </Modal>
  );
}
