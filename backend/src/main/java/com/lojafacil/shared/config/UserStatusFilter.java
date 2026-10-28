package com.lojafacil.shared.config;

import java.io.IOException;
import java.util.Optional;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

import com.lojafacil.auth.User;
import com.lojafacil.auth.UserRepository;
import com.lojafacil.shared.security.AuthUser;

/**
 * A sessão guarda uma "foto" do usuário no momento do login. Este filtro confere, a cada requisição
 * administrativa, se o usuário continua ativo e se o perfil mudou. Assim, um colaborador desativado
 * perde o acesso imediatamente e uma mudança de perfil passa a valer sem novo login.
 * Custo: uma consulta por chave primária por requisição autenticada.
 */
class UserStatusFilter extends OncePerRequestFilter {

    private final UserRepository users;
    private final SecurityContextRepository contextRepository;

    UserStatusFilter(UserRepository users, SecurityContextRepository contextRepository) {
        this.users = users;
        this.contextRepository = contextRepository;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthUser principal) {
            Optional<User> current = users.findById(principal.id());
            if (current.isEmpty() || !current.get().isActive()) {
                SecurityContextHolder.clearContext();
                HttpSession session = request.getSession(false);
                if (session != null) {
                    session.invalidate();
                }
            } else if (current.get().getRole() != principal.role()) {
                AuthUser updated = principal.withRole(current.get().getRole());
                SecurityContext ctx = SecurityContextHolder.createEmptyContext();
                ctx.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(updated, null, updated.getAuthorities()));
                SecurityContextHolder.setContext(ctx);
                contextRepository.saveContext(ctx, request, response);
            }
        }
        chain.doFilter(request, response);
    }
}
