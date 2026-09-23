package com.nexusfood.plataforma.controller;

import com.nexusfood.plataforma.dto.AuthResponse;
import com.nexusfood.plataforma.dto.EsqueciSenhaRequest;
import com.nexusfood.plataforma.dto.LoginRequest;
import com.nexusfood.plataforma.dto.RedefinirSenhaRequest;
import com.nexusfood.plataforma.dto.RegistroRequest;
import com.nexusfood.plataforma.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** Cria um NOVO restaurante (conta) já com o usuário administrador. */
    @PostMapping("/registro")
    public ResponseEntity<AuthResponse> registrar(@Valid @RequestBody RegistroRequest req) {
        return ResponseEntity.ok(authService.registrar(req));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
        return ResponseEntity.ok(authService.login(req));
    }

    @PostMapping("/esqueci-senha")
    public ResponseEntity<Void> esqueciSenha(@Valid @RequestBody EsqueciSenhaRequest req) {
        authService.esqueciSenha(req.getEmail());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/redefinir-senha")
    public ResponseEntity<Void> redefinirSenha(@Valid @RequestBody RedefinirSenhaRequest req) {
        authService.redefinirSenha(req.getToken(), req.getNovaSenha());
        return ResponseEntity.noContent().build();
    }
}
