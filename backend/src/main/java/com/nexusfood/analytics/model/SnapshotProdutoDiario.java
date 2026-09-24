package com.nexusfood.analytics.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Vendas de um produto num dia operacional fechado (só pedidos concluídos). */
@Entity
@Table(name = "snapshots_produtos_diarios")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SnapshotProdutoDiario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "restaurante_id", nullable = false)
    private Long restauranteId;

    @Column(nullable = false)
    private LocalDate dia;

    @Column(name = "produto_id", nullable = false)
    private Long produtoId;

    private int quantidade;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal faturamento;
}
