package com.nexusfood.equipe.service;

import com.nexusfood.equipe.dto.AtualizarUsuarioRequest;
import com.nexusfood.equipe.dto.ConviteResponse;
import com.nexusfood.equipe.dto.EquipeResponse;
import com.nexusfood.equipe.dto.UsuarioEquipeRequest;
import com.nexusfood.equipe.dto.UsuarioEquipeResponse;
import com.nexusfood.plataforma.acesso.AcessoPlanoService;
import com.nexusfood.plataforma.acesso.PlanoInsuficienteException;
import com.nexusfood.plataforma.enums.Papel;
import com.nexusfood.plataforma.enums.PlanoSaas;
import com.nexusfood.plataforma.enums.Recurso;
import com.nexusfood.plataforma.exception.RecursoNaoEncontradoException;
import com.nexusfood.plataforma.exception.RegraDeNegocioException;
import com.nexusfood.plataforma.model.Restaurante;
import com.nexusfood.plataforma.model.Usuario;
import com.nexusfood.plataforma.repository.RestauranteRepository;
import com.nexusfood.plataforma.repository.UsuarioRepository;
import com.nexusfood.plataforma.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Usuários da equipe de um restaurante. Regras:
 * - quem convida define nome, e-mail e perfil; a senha a pessoa cria pelo link (72 h);
 * - o número de usuários ATIVOS respeita o limite do plano (desativados não contam);
 * - o restaurante sempre tem ao menos um administrador ativo;
 * - ninguém desativa nem muda o próprio perfil (evita se trancar para fora);
 * - desativar corta o acesso na hora (o JwtAuthFilter recusa o token de quem está inativo).
 */
@Service
@RequiredArgsConstructor
public class EquipeService {

    static final long VALIDADE_CONVITE_HORAS = 72;
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final UsuarioRepository usuarioRepository;
    private final RestauranteRepository restauranteRepository;
    private final AcessoPlanoService acessoPlano;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final Clock clock;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Transactional(readOnly = true)
    public EquipeResponse listar(Long restauranteId, Long usuarioAtualId) {
        Restaurante restaurante = restaurante(restauranteId);
        Set<Long> fora = foraDoLimite(restaurante);
        List<UsuarioEquipeResponse> usuarios = usuarioRepository.findAllByRestauranteIdOrderByAtivoDescNomeAsc(restauranteId).stream()
                .map(u -> resposta(u, usuarioAtualId, fora))
                .toList();
        long ativos = usuarios.stream().filter(UsuarioEquipeResponse::ativo).count();
        Integer limite = acessoPlano.limiteUsuarios(restaurante);
        PlanoSaas proximo = limite != null && ativos >= limite ? PlanoSaas.menorPlanoParaUsuarios(ativos + 1) : null;
        return new EquipeResponse(usuarios, ativos, limite, acessoPlano.planoEfetivo(restaurante), proximo);
    }

    @Transactional
    public ConviteResponse convidar(Long restauranteId, Long usuarioAtualId, UsuarioEquipeRequest req) {
        Restaurante restaurante = restauranteComTrava(restauranteId);
        String email = req.email().trim().toLowerCase(Locale.ROOT);
        if (usuarioRepository.existsByEmailIgnoreCase(email)) {
            throw new RegraDeNegocioException("Já existe um usuário com este e-mail no Nexus Food.");
        }
        exigirVaga(restaurante);

        Usuario usuario = Usuario.builder()
                .nome(req.nome().trim())
                .email(email)
                .senhaHash(passwordEncoder.encode(segredoAleatorio()))
                .papel(req.papel())
                .restaurante(restaurante)
                .convitePendente(true)
                .build();
        String link = gerarLink(usuario);
        usuarioRepository.save(usuario);

        emailService.enviarConvite(email, usuario.getNome(), restaurante.getNome(), link, VALIDADE_CONVITE_HORAS);
        return new ConviteResponse(resposta(usuario, usuarioAtualId, Set.of()), link, usuario.getResetSenhaExpiraEm());
    }

    @Transactional
    public UsuarioEquipeResponse atualizar(Long restauranteId, Long usuarioAtualId, Long id, AtualizarUsuarioRequest req) {
        restauranteComTrava(restauranteId);
        Usuario usuario = buscar(restauranteId, id);
        if (usuario.getId().equals(usuarioAtualId) && req.papel() != usuario.getPapel()) {
            throw new RegraDeNegocioException("Você não pode mudar o seu próprio perfil. Peça a outro administrador.");
        }
        if (deixariaSemAdministrador(usuario, req.papel() == Papel.ADMINISTRADOR && usuario.isAtivo())) {
            throw new RegraDeNegocioException("O restaurante precisa de pelo menos um administrador ativo.");
        }
        usuario.setNome(req.nome().trim());
        usuario.setPapel(req.papel());
        return resposta(usuario, usuarioAtualId, foraDoLimite(usuario.getRestaurante()));
    }

    @Transactional
    public UsuarioEquipeResponse alterarAtivo(Long restauranteId, Long usuarioAtualId, Long id, boolean ativo) {
        Restaurante restaurante = restauranteComTrava(restauranteId);
        Usuario usuario = buscar(restauranteId, id);
        if (usuario.isAtivo() == ativo) {
            return resposta(usuario, usuarioAtualId, foraDoLimite(restaurante));
        }
        if (!ativo) {
            if (usuario.getId().equals(usuarioAtualId)) {
                throw new RegraDeNegocioException("Você não pode desativar o seu próprio acesso.");
            }
            if (deixariaSemAdministrador(usuario, false)) {
                throw new RegraDeNegocioException("O restaurante precisa de pelo menos um administrador ativo.");
            }
            usuario.setResetSenhaToken(null);
            usuario.setResetSenhaExpiraEm(null);
        } else {
            exigirVaga(restaurante);
        }
        usuario.setAtivo(ativo);
        usuarioRepository.flush();
        return resposta(usuario, usuarioAtualId, foraDoLimite(restaurante));
    }

    /** Novo link: reenviar um convite vencido ou deixar alguém da equipe criar outra senha. */
    @Transactional
    public ConviteResponse novoLink(Long restauranteId, Long usuarioAtualId, Long id) {
        Usuario usuario = buscar(restauranteId, id);
        if (usuario.getId().equals(usuarioAtualId)) {
            throw new RegraDeNegocioException("Para trocar a sua senha, use a opção Esqueci minha senha na tela de login.");
        }
        if (!usuario.isAtivo()) {
            throw new RegraDeNegocioException("Reative o usuário antes de gerar um novo link.");
        }
        String link = gerarLink(usuario);
        if (usuario.isConvitePendente()) {
            emailService.enviarConvite(usuario.getEmail(), usuario.getNome(), usuario.getRestaurante().getNome(), link, VALIDADE_CONVITE_HORAS);
        }
        return new ConviteResponse(resposta(usuario, usuarioAtualId, foraDoLimite(usuario.getRestaurante())), link, usuario.getResetSenhaExpiraEm());
    }

    // ---------- regras ----------

    private void exigirVaga(Restaurante restaurante) {
        Integer limite = acessoPlano.limiteUsuarios(restaurante);
        if (limite == null) return;
        long ativos = usuarioRepository.countByRestauranteIdAndAtivoTrue(restaurante.getId());
        if (ativos >= limite) {
            PlanoSaas necessario = PlanoSaas.menorPlanoParaUsuarios(ativos + 1);
            throw new PlanoInsuficienteException(Recurso.EQUIPE.name(), necessario,
                    "Seu plano permite %d usuários ativos e todos estão em uso. Desative alguém ou mude para o plano %s."
                            .formatted(limite, AcessoPlanoService.nome(necessario)),
                    Map.of("limiteUsuarios", limite));
        }
    }

    /** Tirar este usuário do grupo de administradores ativos deixaria o restaurante sem nenhum? */
    private boolean deixariaSemAdministrador(Usuario usuario, boolean continuaAdministradorAtivo) {
        if (continuaAdministradorAtivo) return false;
        if (usuario.getPapel() != Papel.ADMINISTRADOR || !usuario.isAtivo()) return false;
        return usuarioRepository.countByRestauranteIdAndAtivoTrueAndPapel(usuario.getRestaurante().getId(), Papel.ADMINISTRADOR) <= 1;
    }

    private Set<Long> foraDoLimite(Restaurante restaurante) {
        Integer limite = acessoPlano.limiteUsuarios(restaurante);
        if (limite == null) return Set.of();
        List<Long> fila = usuarioRepository.idsAtivosPorPrioridade(restaurante.getId());
        return fila.size() <= limite ? Set.of() : new HashSet<>(fila.subList(limite, fila.size()));
    }

    private String gerarLink(Usuario usuario) {
        String token = segredoAleatorio();
        usuario.setResetSenhaToken(token);
        usuario.setResetSenhaExpiraEm(Instant.now(clock).plus(VALIDADE_CONVITE_HORAS, ChronoUnit.HOURS));
        return frontendUrl + "/redefinir-senha?token=" + token;
    }

    private static String segredoAleatorio() {
        byte[] bytes = new byte[32];
        ALEATORIO.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private UsuarioEquipeResponse resposta(Usuario u, Long usuarioAtualId, Set<Long> fora) {
        return new UsuarioEquipeResponse(u.getId(), u.getNome(), u.getEmail(), u.getPapel(), u.isAtivo(),
                u.isConvitePendente(), u.getId() != null && u.getId().equals(usuarioAtualId), fora.contains(u.getId()));
    }

    private Usuario buscar(Long restauranteId, Long id) {
        return usuarioRepository.findByIdAndRestauranteId(id, restauranteId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado."));
    }

    private Restaurante restaurante(Long id) {
        return restauranteRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Restaurante não encontrado"));
    }

    /** Dois administradores mexendo na equipe ao mesmo tempo não furam o limite nem a regra do último admin. */
    private Restaurante restauranteComTrava(Long id) {
        return restauranteRepository.travarPorId(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Restaurante não encontrado"));
    }
}
