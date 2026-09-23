package com.nexusfood.pedidos.service;

import com.nexusfood.catalogo.model.Produto;
import com.nexusfood.catalogo.repository.ProdutoRepository;
import com.nexusfood.clientes.model.Cliente;
import com.nexusfood.clientes.service.ClienteService;
import com.nexusfood.pedidos.dto.AcompanhamentoPedidoResponse;
import com.nexusfood.pedidos.dto.CriarPedidoRequest;
import com.nexusfood.pedidos.dto.MudarStatusRequest;
import com.nexusfood.pedidos.dto.PedidoResponse;
import com.nexusfood.pedidos.enums.FormaPagamento;
import com.nexusfood.pedidos.enums.ModalidadePedido;
import com.nexusfood.pedidos.enums.StatusPedido;
import com.nexusfood.pedidos.model.ItemPedido;
import com.nexusfood.pedidos.model.Pedido;
import com.nexusfood.pedidos.repository.PedidoRepository;
import com.nexusfood.plataforma.enums.TipoTaxaEntrega;
import com.nexusfood.plataforma.exception.RecursoNaoEncontradoException;
import com.nexusfood.plataforma.exception.RegraDeNegocioException;
import com.nexusfood.plataforma.model.BairroEntrega;
import com.nexusfood.plataforma.model.Restaurante;
import com.nexusfood.plataforma.model.Usuario;
import com.nexusfood.plataforma.repository.BairroEntregaRepository;
import com.nexusfood.plataforma.repository.RestauranteRepository;
import com.nexusfood.plataforma.repository.UsuarioRepository;
import com.nexusfood.plataforma.security.SecurityUtils;
import com.nexusfood.plataforma.service.RelogioRestaurante;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PedidoService {

    private static final Set<StatusPedido> STATUS_EM_ANDAMENTO = EnumSet.of(
            StatusPedido.RECEBIDO, StatusPedido.CONFIRMADO, StatusPedido.EM_PREPARO,
            StatusPedido.PRONTO, StatusPedido.SAIU_PARA_ENTREGA);

    private final PedidoRepository pedidoRepository;
    private final RestauranteRepository restauranteRepository;
    private final ProdutoRepository produtoRepository;
    private final BairroEntregaRepository bairroEntregaRepository;
    private final UsuarioRepository usuarioRepository;
    private final ClienteService clienteService;
    private final RelogioRestaurante relogio;

    /**
     * Cria o pedido vindo do cardápio público. Tudo numa transação, com a linha do restaurante
     * travada: a numeração do dia e a checagem de idempotência não sofrem corrida.
     */
    @Transactional
    public AcompanhamentoPedidoResponse criarPublico(String slug, CriarPedidoRequest req) {
        Long restauranteId = restauranteRepository.findBySlug(slug)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Restaurante não encontrado"))
                .getId();
        Restaurante restaurante = restauranteRepository.travarPorId(restauranteId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Restaurante não encontrado"));

        String chave = req.getChaveIdempotencia().trim();
        var existente = pedidoRepository.findByRestauranteIdAndChaveIdempotencia(restauranteId, chave);
        if (existente.isPresent()) {
            return AcompanhamentoPedidoResponse.de(existente.get());
        }

        if (!restaurante.isAceitandoPedidos()) {
            throw new RegraDeNegocioException("O restaurante não está aceitando pedidos agora.");
        }
        validarModalidade(restaurante, req.getModalidade());

        Instant agora = relogio.agora();
        List<ItemPedido> itens = montarItens(restauranteId, req.getItens());
        BigDecimal subtotal = itens.stream().map(ItemPedido::getSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add);

        if (subtotal.compareTo(restaurante.getPedidoMinimo()) < 0) {
            throw new RegraDeNegocioException("O pedido mínimo deste restaurante é R$ "
                    + restaurante.getPedidoMinimo().setScale(2) + " (sem a taxa de entrega).");
        }

        BigDecimal taxaEntrega = BigDecimal.ZERO;
        String bairroNome = null;
        String endereco = null;
        if (req.getModalidade() == ModalidadePedido.ENTREGA) {
            if (req.getEnderecoEntrega() == null || req.getEnderecoEntrega().isBlank()) {
                throw new RegraDeNegocioException("Informe o endereço de entrega.");
            }
            endereco = req.getEnderecoEntrega().trim();
            if (restaurante.getTipoTaxaEntrega() == TipoTaxaEntrega.POR_BAIRRO) {
                BairroEntrega bairro = bairroAtendido(restauranteId, req.getBairroId());
                taxaEntrega = bairro.getTaxa();
                bairroNome = bairro.getNome();
            } else {
                taxaEntrega = restaurante.getTaxaEntregaFixa();
            }
        }

        BigDecimal total = subtotal.add(taxaEntrega);
        BigDecimal trocoPara = validarTroco(req.getFormaPagamento(), req.getTrocoPara(), total);

        Cliente cliente = clienteService.identificar(restauranteId, req.getTelefoneCliente(), req.getNomeCliente(), agora);
        LocalDate dia = relogio.diaOperacional(restaurante, agora);

        Pedido pedido = Pedido.builder()
                .restaurante(restaurante)
                .cliente(cliente)
                .numeroDia(pedidoRepository.maiorNumeroDoDia(restauranteId, dia) + 1)
                .codigoPublico(UUID.randomUUID().toString())
                .chaveIdempotencia(chave)
                .modalidade(req.getModalidade())
                .formaPagamento(req.getFormaPagamento())
                .trocoPara(trocoPara)
                .enderecoEntrega(endereco)
                .bairroEntrega(bairroNome)
                .observacao(textoOuNulo(req.getObservacao()))
                .subtotal(subtotal)
                .taxaEntrega(taxaEntrega)
                .total(total)
                .diaOperacional(dia)
                .prontoPrevistoPara(agora.plus(Duration.ofMinutes(restaurante.getTempoPreparoEstimadoMin())))
                .criadoEm(agora)
                .build();
        itens.forEach(pedido::adicionarItem);
        pedido.registrarRecebimento();

        return AcompanhamentoPedidoResponse.de(pedidoRepository.save(pedido));
    }

    @Transactional(readOnly = true)
    public AcompanhamentoPedidoResponse acompanhar(String codigoPublico) {
        return pedidoRepository.findByCodigoPublico(codigoPublico)
                .map(AcompanhamentoPedidoResponse::de)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pedido não encontrado"));
    }

    /** Pedidos que ainda precisam de ação — as colunas do painel. */
    @Transactional(readOnly = true)
    public List<PedidoResponse> emAndamento() {
        return pedidoRepository.findAllComStatus(SecurityUtils.restauranteAtualId(), STATUS_EM_ANDAMENTO).stream()
                .map(p -> PedidoResponse.de(p, false))
                .toList();
    }

    /** Todos os pedidos de um dia operacional (padrão: hoje), inclusive concluídos e cancelados. */
    @Transactional(readOnly = true)
    public List<PedidoResponse> doDia(LocalDate dia) {
        Long restauranteId = SecurityUtils.restauranteAtualId();
        LocalDate alvo = dia != null ? dia : relogio.diaOperacionalAtual(restauranteAtual(restauranteId));
        return pedidoRepository.findAllDoDia(restauranteId, alvo).stream()
                .map(p -> PedidoResponse.de(p, false))
                .toList();
    }

    @Transactional(readOnly = true)
    public PedidoResponse detalhe(Long id) {
        return PedidoResponse.de(buscar(id), true);
    }

    @Transactional
    public PedidoResponse mudarStatus(Long id, MudarStatusRequest req) {
        Pedido pedido = buscar(id);
        Usuario usuario = usuarioRepository.getReferenceById(SecurityUtils.usuarioAtual().getId());
        pedido.transicionarPara(req.getStatus(), relogio.agora(), usuario, req.getMotivoCancelamento());
        return PedidoResponse.de(pedidoRepository.save(pedido), true);
    }

    private List<ItemPedido> montarItens(Long restauranteId, List<CriarPedidoRequest.Item> itensReq) {
        Set<Long> ids = itensReq.stream().map(CriarPedidoRequest.Item::getProdutoId).collect(Collectors.toSet());
        Map<Long, Produto> produtos = produtoRepository.findAllByIdInAndRestauranteId(ids, restauranteId).stream()
                .collect(Collectors.toMap(Produto::getId, Function.identity()));

        return itensReq.stream().map(itemReq -> {
            Produto produto = produtos.get(itemReq.getProdutoId());
            if (produto == null || !produto.isAtivo() || !produto.getCategoria().isAtiva()) {
                throw new RegraDeNegocioException("Um dos produtos do pedido não está mais no cardápio. Atualize a página.");
            }
            if (!produto.isDisponivel()) {
                throw new RegraDeNegocioException("\"" + produto.getNome() + "\" esgotou. Remova-o do pedido para continuar.");
            }
            BigDecimal subtotal = produto.getPreco().multiply(BigDecimal.valueOf(itemReq.getQuantidade()));
            return ItemPedido.builder()
                    .produto(produto)
                    .nomeProduto(produto.getNome())
                    .precoUnitario(produto.getPreco())
                    .quantidade(itemReq.getQuantidade())
                    .subtotal(subtotal)
                    .observacao(textoOuNulo(itemReq.getObservacao()))
                    .build();
        }).toList();
    }

    private void validarModalidade(Restaurante r, ModalidadePedido modalidade) {
        boolean aceita = switch (modalidade) {
            case RETIRADA -> r.isAceitaRetirada();
            case ENTREGA -> r.isAceitaEntrega();
            case CONSUMO_LOCAL -> r.isAceitaConsumoLocal();
        };
        if (!aceita) {
            throw new RegraDeNegocioException("Este restaurante não atende nessa modalidade.");
        }
    }

    private BairroEntrega bairroAtendido(Long restauranteId, Long bairroId) {
        if (bairroId == null) {
            throw new RegraDeNegocioException("Escolha o bairro de entrega.");
        }
        return bairroEntregaRepository.findByIdAndRestauranteId(bairroId, restauranteId)
                .filter(BairroEntrega::isAtivo)
                .orElseThrow(() -> new RegraDeNegocioException("O restaurante não entrega nesse bairro."));
    }

    private BigDecimal validarTroco(FormaPagamento forma, BigDecimal trocoPara, BigDecimal total) {
        if (trocoPara == null || forma != FormaPagamento.DINHEIRO) {
            return null;
        }
        if (trocoPara.compareTo(total) < 0) {
            throw new RegraDeNegocioException("O valor para troco deve ser maior ou igual ao total do pedido.");
        }
        return trocoPara;
    }

    private Pedido buscar(Long id) {
        return pedidoRepository.findByIdAndRestauranteId(id, SecurityUtils.restauranteAtualId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pedido não encontrado"));
    }

    private Restaurante restauranteAtual(Long restauranteId) {
        return restauranteRepository.findById(restauranteId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Restaurante não encontrado"));
    }

    private static String textoOuNulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }
}
