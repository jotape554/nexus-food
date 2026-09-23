package com.nexusfood.plataforma.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private String nome;
    private String papel;
    private Long restauranteId;
    private String restauranteSlug;
}
