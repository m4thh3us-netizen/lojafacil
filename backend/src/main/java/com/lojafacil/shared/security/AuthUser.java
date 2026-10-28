package com.lojafacil.shared.security;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.lojafacil.auth.Role;

/**
 * Usuário autenticado guardado na sessão.
 *
 * O storeId vem SEMPRE daqui (do servidor), nunca de um parâmetro enviado pelo navegador.
 * É assim que garantimos o isolamento entre lojas.
 */
public final class AuthUser implements UserDetails, CredentialsContainer, Serializable {

    private final Long id;
    private final Long storeId;
    private final String email;
    private final String fullName;
    private final Role role;
    private final boolean active;
    private String passwordHash;

    public AuthUser(Long id, Long storeId, String email, String fullName, Role role, boolean active, String passwordHash) {
        this.id = id;
        this.storeId = storeId;
        this.email = email;
        this.fullName = fullName;
        this.role = role;
        this.active = active;
        this.passwordHash = passwordHash;
    }

    public AuthUser withRole(Role newRole) {
        return new AuthUser(id, storeId, email, fullName, newRole, active, null);
    }

    public Long id() { return id; }
    public Long storeId() { return storeId; }
    public String email() { return email; }
    public String fullName() { return fullName; }
    public Role role() { return role; }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override public String getPassword() { return passwordHash; }
    @Override public String getUsername() { return email; }
    @Override public boolean isEnabled() { return active; }
    @Override public void eraseCredentials() { this.passwordHash = null; }
}
