import { useCallback, useState } from 'react';
import Modal from './Modal';

/**
 * Confirmação dentro da página, no lugar do confirm() do navegador — que é feio, não segue o
 * visual do sistema e é bloqueado em alguns ambientes (ele devolve "não" sem mostrar nada).
 *
 *   const [confirmar, confirmacao] = useConfirmacao();
 *   if (!(await confirmar({ titulo, mensagem, acao: 'Remover' }))) return;
 *   ...
 *   return <>{...}{confirmacao}</>;
 */
export function useConfirmacao() {
  const [pendente, setPendente] = useState(null);

  const confirmar = useCallback(
    ({ titulo, mensagem, acao = 'Confirmar', perigo = true }) =>
      new Promise((resolve) => setPendente({ titulo, mensagem, acao, perigo, resolve })),
    [],
  );

  function responder(valor) {
    pendente?.resolve(valor);
    setPendente(null);
  }

  const elemento = pendente && (
    <Modal titulo={pendente.titulo} onFechar={() => responder(false)}>
      <p style={{ margin: '0 0 8px', lineHeight: 1.5 }}>{pendente.mensagem}</p>
      <div className="modal-acoes">
        <button type="button" className="btn btn-secundario" onClick={() => responder(false)}>Voltar</button>
        <button type="button" className={`btn ${pendente.perigo ? 'btn-perigo' : 'btn-latao'}`} autoFocus onClick={() => responder(true)}>
          {pendente.acao}
        </button>
      </div>
    </Modal>
  );

  return [confirmar, elemento];
}
