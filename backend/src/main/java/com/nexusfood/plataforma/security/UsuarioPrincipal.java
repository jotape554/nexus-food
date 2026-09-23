package com.nexusfood.plataforma.security;

import com.nexusfood.plataforma.model.Usuario;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Wrapper de Usuario para o Spring Security. Carrega o restauranteId junto — é a partir
 * daqui que TODOS os services descobrem de qual restaurante o usuário autenticado faz parte.
 */
@Getter
public class UsuarioPrincipal implements UserDetails {

    private final Long id;
    private final String email;
    private final String senhaHash;
    private final String papel;
    private final Long restauranteId;
    private final boolean ativo;

    public UsuarioPrincipal(Usuario usuario) {
        this.id = usuario.getId();
        this.email = usuario.getEmail();
        this.senhaHash = usuario.getSenhaHash();
        this.papel = usuario.getPapel().name();
        this.restauranteId = usuario.getRestaurante().getId();
        this.ativo = usuario.isAtivo();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + papel));
    }

    @Override
    public String getPassword() {
        return senhaHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() { return true; }
    @Override
    public boolean isAccountNonLocked() { return true; }
    @Override
    public boolean isCredentialsNonExpired() { return true; }
    @Override
    public boolean isEnabled() { return ativo; }
}
