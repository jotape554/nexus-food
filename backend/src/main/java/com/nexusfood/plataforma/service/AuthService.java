package com.nexusfood.plataforma.service;

import com.nexusfood.plataforma.dto.AuthResponse;
import com.nexusfood.plataforma.dto.LoginRequest;
import com.nexusfood.plataforma.dto.RegistroRequest;
import com.nexusfood.plataforma.enums.Papel;
import com.nexusfood.plataforma.model.Restaurante;
import com.nexusfood.plataforma.model.Usuario;
import com.nexusfood.plataforma.repository.RestauranteRepository;
import com.nexusfood.plataforma.repository.UsuarioRepository;
import com.nexusfood.plataforma.security.JwtService;
import com.nexusfood.plataforma.security.UsuarioPrincipal;
import com.nexusfood.plataforma.exception.RegraDeNegocioException;
import com.nexusfood.plataforma.util.CpfValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final long VALIDADE_TOKEN_RESET_MINUTOS = 60;

    private final RestauranteRepository restauranteRepository;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final EmailService emailService;
    private final Clock clock;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Transactional
    public AuthResponse registrar(RegistroRequest req) {
        if (usuarioRepository.existsByEmail(req.getEmail())) {
            throw new RegraDeNegocioException("Já existe um usuário com este e-mail.");
        }

        String cpf = CpfValidator.somenteDigitos(req.getCpf());
        if (!CpfValidator.isValido(cpf)) {
            throw new RegraDeNegocioException("Informe um CPF válido.");
        }
        if (usuarioRepository.existsByCpf(cpf)) {
            throw new RegraDeNegocioException("Já existe uma conta cadastrada com este CPF.");
        }

        String slug = gerarSlugUnico(req.getNomeRestaurante());

        Restaurante restaurante = Restaurante.builder()
                .nome(req.getNomeRestaurante())
                .slug(slug)
                .dataFimTrial(LocalDate.now(clock).plusDays(AssinaturaService.DIAS_TRIAL))
                .build();
        restaurante = restauranteRepository.save(restaurante);

        Usuario admin = Usuario.builder()
                .nome(req.getNomeAdmin())
                .email(req.getEmail())
                .cpf(cpf)
                .senhaHash(passwordEncoder.encode(req.getSenha()))
                .papel(Papel.ADMINISTRADOR)
                .restaurante(restaurante)
                .build();
        usuarioRepository.save(admin);

        UsuarioPrincipal principal = new UsuarioPrincipal(admin);
        String token = jwtService.gerarToken(principal);

        return new AuthResponse(token, admin.getNome(), admin.getPapel().name(), restaurante.getId(), restaurante.getSlug());
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.getEmail(), req.getSenha()));

        Usuario usuario = usuarioRepository.findByEmail(req.getEmail())
                .orElseThrow(() -> new RegraDeNegocioException("E-mail ou senha inválidos"));

        UsuarioPrincipal principal = new UsuarioPrincipal(usuario);
        String token = jwtService.gerarToken(principal);

        return new AuthResponse(token, usuario.getNome(), usuario.getPapel().name(),
                usuario.getRestaurante().getId(), usuario.getRestaurante().getSlug());
    }

    /**
     * Sempre "funciona" do ponto de vista do cliente, exista ou não o e-mail — nunca revela
     * se um endereço tem conta cadastrada (evita que alguém descubra e-mails de clientes).
     */
    @Transactional
    public void esqueciSenha(String email) {
        usuarioRepository.findByEmail(email).ifPresent(usuario -> {
            String token = UUID.randomUUID().toString();
            usuario.setResetSenhaToken(token);
            usuario.setResetSenhaExpiraEm(Instant.now(clock).plus(VALIDADE_TOKEN_RESET_MINUTOS, ChronoUnit.MINUTES));
            usuarioRepository.save(usuario);

            String link = frontendUrl + "/redefinir-senha?token=" + token;
            emailService.enviarRedefinicaoSenha(usuario.getEmail(), link);
        });
    }

    @Transactional
    public void redefinirSenha(String token, String novaSenha) {
        Usuario usuario = usuarioRepository.findByResetSenhaToken(token)
                .orElseThrow(() -> new RegraDeNegocioException("Link inválido ou expirado."));

        if (usuario.getResetSenhaExpiraEm() == null || Instant.now(clock).isAfter(usuario.getResetSenhaExpiraEm())) {
            throw new RegraDeNegocioException("Link inválido ou expirado.");
        }

        usuario.setSenhaHash(passwordEncoder.encode(novaSenha));
        usuario.setResetSenhaToken(null);
        usuario.setResetSenhaExpiraEm(null);
        usuarioRepository.save(usuario);
    }

    private String gerarSlugUnico(String nome) {
        String base = Normalizer.normalize(nome, Normalizer.Form.NFD)
                .replaceAll("[^\\p{ASCII}]", "")
                .toLowerCase()
                .trim()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");

        String slug = base;
        int contador = 1;
        while (restauranteRepository.existsBySlug(slug)) {
            slug = base + "-" + contador++;
        }
        return slug;
    }
}
