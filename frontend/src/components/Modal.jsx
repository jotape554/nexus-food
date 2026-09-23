import { forwardRef } from 'react';

const Modal = forwardRef(function Modal({ titulo, onFechar, children }, ref) {
  return (
    <div className="modal-fundo" onClick={onFechar}>
      <div className="modal" ref={ref} onClick={(e) => e.stopPropagation()}>
        <h3>{titulo}</h3>
        {children}
      </div>
    </div>
  );
});

export default Modal;
