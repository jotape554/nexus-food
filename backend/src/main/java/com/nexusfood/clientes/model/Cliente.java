package com.nexusfood.clientes.model;

import com.nexusfood.plataforma.model.Restaurante;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Cliente final do restaurante. A identidade é o telefone normalizado (ver TelefoneUtil):
 * o mesmo número sempre cai no mesmo cliente, o que sustenta recompra e retenção no Nexus.
 */
@Entity
@Table(name = "clientes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "restaurante_id", nullable = false)
    private Restaurante restaurante;

    @Column(nullable = false, length = 20)
    private String telefone;

    @Column(nullable = false, length = 160)
    private String nome;

    @Column(nullable = false)
    private Instant criadoEm;
}
