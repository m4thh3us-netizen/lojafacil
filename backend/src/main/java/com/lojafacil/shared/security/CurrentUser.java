package com.lojafacil.shared.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.lojafacil.shared.error.UnauthorizedException;

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

    public Long idOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof AuthUser user ? user.id() : null;
    }
}
