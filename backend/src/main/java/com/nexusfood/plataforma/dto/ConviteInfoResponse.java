package com.nexusfood.plataforma.dto;

/** convite: true se a pessoa ainda não tem senha (primeiro acesso), false se é uma troca de senha. */
public record ConviteInfoResponse(String nome, String email, String restaurante, boolean convite) {}
