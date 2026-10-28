package com.lojafacil.shared.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.lojafacil.shared.error.UnauthorizedException;

/**
 * Acesso ao usuário logado. Serviços administrativos usam {@link #storeId()} para filtrar
 * todas as consultas pela loja do usuário.
 */
@Component
public class CurrentUser {

    public AuthUser get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthUser user)) {
            throw new UnauthorizedException();
        }
        return user;
    }

    public Long id() {
        return get().id();
    }

    public Long storeId() {
        return get().storeId();
    }

    /** Retorna o id do usuário, ou null quando a ação é do sistema (webhook, job). */
    public Long idOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof AuthUser user ? user.id() : null;
    }
}
