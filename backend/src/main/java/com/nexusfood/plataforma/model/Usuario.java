package com.nexusfood.plataforma.model;

import com.nexusfood.plataforma.enums.Papel;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "usuarios")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false, unique = true)
    private String email;

    /** Só preenchido para o administrador que criou a conta (evita reuso de trial com CPFs diferentes). */
    @Column(unique = true)
    private String cpf;

    @Column(nullable = false)
    @JsonIgnore
    private String senhaHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Papel papel;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "restaurante_id", nullable = false)
    private Restaurante restaurante;

    @Builder.Default
    private boolean ativo = true;

    /** Criado pelo administrador e ainda sem senha própria (o link de convite não foi usado). */
    @Builder.Default
    private boolean convitePendente = false;

    /** Preenchidos só durante um pedido de "esqueci minha senha" em andamento. */
    @JsonIgnore
    private String resetSenhaToken;
    @JsonIgnore
    private Instant resetSenhaExpiraEm;
}
