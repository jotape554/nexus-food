import { useEffect, useState } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { AuthProvider } from '../context/AuthContext';
import { Rotas } from '../App';
import { aoMudar, reiniciarDemonstracao } from './apiFalsa';
import { SLUG_DEMO } from './dadosIniciais';
import AssinaturaNexus from '../components/AssinaturaNexus';
import './demo.css';

const LARGURA_LADO_A_LADO = '(min-width: 1280px)';

function usarMidia(consulta) {
  const [bate, setBate] = useState(() => window.matchMedia(consulta).matches);
  useEffect(() => {
    const m = window.matchMedia(consulta);
    const ouvir = () => setBate(m.matches);
    m.addEventListener('change', ouvir);
    return () => m.removeEventListener('change', ouvir);
  }, [consulta]);
  return bate;
}

function limparDadosDoClienteFinal() {
  try {
    localStorage.removeItem(`nexusfood_carrinho_${SLUG_DEMO}`);
    localStorage.removeItem('nexusfood_cliente');
  } catch { /* sem armazenamento, nada a limpar */ }
}

/**
 * Demonstração: o painel do restaurante e o celular do cliente são as MESMAS telas do sistema,
 * cada uma com seu próprio roteador em memória, conversando com a API falsa do navegador.
 * Um pedido feito no celular aparece no painel na hora.
 */
export default function DemoShell() {
  const queryClient = useQueryClient();
  const larga = usarMidia(LARGURA_LADO_A_LADO);
  const [visao, setVisao] = useState(() => (window.matchMedia(LARGURA_LADO_A_LADO).matches ? 'lado' : 'painel'));
  const [geracao, setGeracao] = useState(0);
  const [confirmandoReinicio, setConfirmandoReinicio] = useState(false);

  const visaoEfetiva = visao === 'lado' && !larga ? 'painel' : visao;

  // Qualquer mudança nos dados (ex.: pedido novo no celular) atualiza as telas na hora.
  useEffect(() => aoMudar(() => queryClient.invalidateQueries()), [queryClient]);

  // "Seu cardápio: …" no painel leva para o celular do cliente.
  useEffect(() => {
    const abrirCliente = () => setVisao((v) => (v === 'lado' && larga ? v : 'cliente'));
    window.addEventListener('nexusdemo:abrir-cliente', abrirCliente);
    return () => window.removeEventListener('nexusdemo:abrir-cliente', abrirCliente);
  }, [larga]);

  useEffect(() => {
    if (!confirmandoReinicio) return undefined;
    const t = setTimeout(() => setConfirmandoReinicio(false), 4000);
    return () => clearTimeout(t);
  }, [confirmandoReinicio]);

  function reiniciar() {
    if (!confirmandoReinicio) {
      setConfirmandoReinicio(true);
      return;
    }
    setConfirmandoReinicio(false);
    limparDadosDoClienteFinal();
    reiniciarDemonstracao();
    queryClient.clear();
    setGeracao((g) => g + 1);
  }

  const mostraPainel = visaoEfetiva !== 'cliente';
  const mostraCliente = visaoEfetiva !== 'painel';

  return (
    <AuthProvider>
      <div className={`demo demo-${visaoEfetiva}`}>
        <header className="demo-barra">
          <div className="demo-marca">
            <strong>Nexus<span>Food</span></strong>
            <span className="demo-selo">demonstração</span>
            <AssinaturaNexus tom="escuro" prefixo="por" className="demo-assinatura" />
          </div>

          <nav className="demo-visoes" aria-label="O que mostrar">
            <button type="button" aria-pressed={visaoEfetiva === 'painel'} onClick={() => setVisao('painel')}>Painel do restaurante</button>
            <button type="button" aria-pressed={visaoEfetiva === 'cliente'} onClick={() => setVisao('cliente')}>Celular do cliente</button>
            {larga && <button type="button" aria-pressed={visaoEfetiva === 'lado'} onClick={() => setVisao('lado')}>Lado a lado</button>}
          </nav>

          <button type="button" className={`demo-reiniciar ${confirmandoReinicio ? 'confirmando' : ''}`} onClick={reiniciar}>
            {confirmandoReinicio ? 'Clique de novo para reiniciar' : 'Reiniciar demonstração'}
          </button>
        </header>

        <p className="demo-dica">
          Faça um pedido no celular do cliente e veja ele chegar no painel. Dados de exemplo da Cantina da Nona;
          tudo que você muda fica só neste navegador.
        </p>

        <div className="demo-palco" key={geracao}>
          <section className="demo-painel" hidden={!mostraPainel} aria-label="Painel do restaurante">
            <MemoryRouter initialEntries={['/painel/pedidos']}>
              <Rotas />
            </MemoryRouter>
          </section>

          <section className="demo-lado-cliente" hidden={!mostraCliente} aria-label="Celular do cliente">
            <div className="demo-celular">
              <div className="demo-celular-tela">
                <MemoryRouter initialEntries={[`/r/${SLUG_DEMO}`]}>
                  <Rotas />
                </MemoryRouter>
              </div>
            </div>
          </section>
        </div>
      </div>
    </AuthProvider>
  );
}
