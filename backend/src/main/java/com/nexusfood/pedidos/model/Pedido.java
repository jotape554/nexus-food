package com.nexusfood.pedidos.model;

import com.nexusfood.clientes.model.Cliente;
import com.nexusfood.pedidos.enums.CanceladoPor;
import com.nexusfood.pedidos.enums.FormaPagamento;
import com.nexusfood.pedidos.enums.ModalidadePedido;
import com.nexusfood.pedidos.enums.MotivoCancelamento;
import com.nexusfood.pedidos.enums.StatusPedido;
import com.nexusfood.plataforma.exception.RegraDeNegocioException;
import com.nexusfood.plataforma.model.Restaurante;
import com.nexusfood.plataforma.model.Usuario;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Pedido do cliente final. O status só muda por {@link #transicionarPara}, que valida a
 * transição, grava o horário da etapa na coluna correspondente e registra um PedidoEvento —
 * as colunas *Em alimentam as métricas de tempo; os eventos são a auditoria completa.
 */
@Entity
@Table(name = "pedidos")
@Getter
@Setter(AccessLevel.PACKAGE)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "restaurante_id", nullable = false)
    private Restaurante restaurante;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    /** Número curto do dia (o "#27" chamado no balcão); recomeça a cada dia operacional. */
    @Column(nullable = false)
    private Integer numeroDia;

    /** Identificador impossível de adivinhar usado pelo cliente para acompanhar o pedido. */
    @Column(nullable = false, length = 36, unique = true)
    private String codigoPublico;

    /** Gerada pelo navegador do cliente: reenviar o mesmo pedido (clique duplo, rede ruim) não duplica. */
    @Column(nullable = false, length = 64)
    private String chaveIdempotencia;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ModalidadePedido modalidade;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private StatusPedido status = StatusPedido.RECEBIDO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FormaPagamento formaPagamento;

    @Column(precision = 12, scale = 2)
    private BigDecimal trocoPara;

    @Column(length = 500)
    private String enderecoEntrega;

    /** Nome do bairro no momento do pedido (cópia — o cadastro de bairros pode mudar). */
    @Column(length = 120)
    private String bairroEntrega;

    @Column(length = 500)
    private String observacao;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal taxaEntrega;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    /** Dia a que o pedido pertence nos relatórios (fuso + hora de virada do restaurante). */
    @Column(nullable = false)
    private LocalDate diaOperacional;

    /** Criação + tempo estimado de preparo do restaurante — base do indicador de pontualidade. */
    @Column(nullable = false)
    private Instant prontoPrevistoPara;

    @Column(nullable = false)
    private Instant criadoEm;
    private Instant confirmadoEm;
    private Instant emPreparoEm;
    private Instant prontoEm;
    private Instant saiuParaEntregaEm;
    private Instant concluidoEm;
    private Instant canceladoEm;

    @Enumerated(EnumType.STRING)
    private MotivoCancelamento motivoCancelamento;

    @Enumerated(EnumType.STRING)
    private CanceladoPor canceladoPor;

    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    @Builder.Default
    private List<ItemPedido> itens = new ArrayList<>();

    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("ocorridoEm ASC, id ASC")
    @Builder.Default
    private List<PedidoEvento> eventos = new ArrayList<>();

    public void adicionarItem(ItemPedido item) {
        item.setPedido(this);
        itens.add(item);
    }

    /** Registra o evento de criação. Chamado uma única vez, quando o pedido é criado. */
    public void registrarRecebimento() {
        eventos.add(PedidoEvento.builder()
                .pedido(this)
                .statusAnterior(null)
                .statusNovo(StatusPedido.RECEBIDO)
                .ocorridoEm(criadoEm)
                .build());
    }

    /**
     * Única porta de mudança de status.
     *
     * Sair de RECEBIDO para qualquer etapa seguinte conta como "aceitar" o pedido: se o
     * restaurante pular direto para EM_PREPARO, confirmadoEm recebe o mesmo horário (o tempo
     * de aceite continua mensurável). As demais etapas puladas ficam nulas — os indicadores
     * de tempo medem só os pedidos que têm o horário registrado.
     */
    public void transicionarPara(StatusPedido novo, Instant agora, Usuario usuario, MotivoCancelamento motivo) {
        if (!status.podeIrPara(novo, modalidade)) {
            throw new RegraDeNegocioException("Não é possível mudar o pedido de " + status + " para " + novo + ".");
        }
        if (novo == StatusPedido.CANCELADO && motivo == null) {
            throw new RegraDeNegocioException("Informe o motivo do cancelamento.");
        }

        StatusPedido anterior = status;
        switch (novo) {
            case CONFIRMADO -> confirmadoEm = agora;
            case EM_PREPARO -> emPreparoEm = agora;
            case PRONTO -> prontoEm = agora;
            case SAIU_PARA_ENTREGA -> saiuParaEntregaEm = agora;
            case CONCLUIDO -> concluidoEm = agora;
            case CANCELADO -> {
                canceladoEm = agora;
                motivoCancelamento = motivo;
                canceladoPor = motivo.getResponsavel();
            }
            default -> { }
        }
        if (anterior == StatusPedido.RECEBIDO && novo != StatusPedido.CANCELADO && confirmadoEm == null) {
            confirmadoEm = agora;
        }
        status = novo;

        eventos.add(PedidoEvento.builder()
                .pedido(this)
                .statusAnterior(anterior)
                .statusNovo(novo)
                .ocorridoEm(agora)
                .usuario(usuario)
                .build());
    }
}
