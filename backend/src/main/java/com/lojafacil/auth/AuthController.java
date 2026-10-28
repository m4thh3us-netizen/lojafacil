package com.lojafacil.auth;

import java.util.Locale;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.lojafacil.auth.AuthDtos.AcceptInvitationRequest;
import com.lojafacil.auth.AuthDtos.ForgotPasswordRequest;
import com.lojafacil.auth.AuthDtos.InvitationPreview;
import com.lojafacil.auth.AuthDtos.LoginRequest;
import com.lojafacil.auth.AuthDtos.MeResponse;
import com.lojafacil.auth.AuthDtos.RegisterRequest;
import com.lojafacil.auth.AuthDtos.ResetPasswordRequest;
import com.lojafacil.auth.AuthDtos.SlugAvailability;
import com.lojafacil.shared.error.ApiException;
import com.lojafacil.shared.security.AuthUser;
import com.lojafacil.shared.security.CurrentUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Autenticação baseada em sessão: após o login, o servidor guarda o usuário na sessão e o navegador
 * recebe apenas um cookie HttpOnly (JSESSIONID), inacessível a JavaScript.
 * O logout é tratado pelo Spring Security em POST /api/auth/logout.
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Autenticação")
public class AuthController {

    private final AuthService authService;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final LoginAttemptService attempts;
    private final CurrentUser currentUser;

    public AuthController(AuthService authService, AuthenticationManager authenticationManager,
                          SecurityContextRepository securityContextRepository, LoginAttemptService attempts,
                          CurrentUser currentUser) {
        this.authService = authService;
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.attempts = attempts;
        this.currentUser = currentUser;
    }

    @Operation(summary = "Obtém o token CSRF (também enviado no cookie XSRF-TOKEN)")
    @GetMapping("/csrf")
    public CsrfToken csrf(CsrfToken token) {
        return token;
    }

    @Operation(summary = "Verifica se um identificador de loja está disponível")
    @GetMapping("/slug-availability")
    public SlugAvailability slugAvailability(@RequestParam String slug) {
        return authService.checkSlug(slug);
    }

    @Operation(summary = "Cadastra responsável e loja; já inicia a sessão")
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public MeResponse register(@Valid @RequestBody RegisterRequest req, HttpServletRequest request, HttpServletResponse response) {
        User owner = authService.register(req);
        startSession(owner.toAuthUser(), request, response);
        return authService.me(owner.getId());
    }

    @Operation(summary = "Login com e-mail e senha")
    @PostMapping("/login")
    public MeResponse login(@Valid @RequestBody LoginRequest req, HttpServletRequest request, HttpServletResponse response) {
        String emailKey = "login:" + req.email().trim().toLowerCase(Locale.ROOT);
        String ipKey = "login-ip:" + request.getRemoteAddr();
        attempts.checkAllowed(emailKey, ipKey);
        Authentication auth;
        try {
            auth = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(req.email().trim(), req.password()));
        } catch (AuthenticationException e) {
            attempts.recordFailure(emailKey, ipKey);
            // Mesma mensagem para usuário inexistente, senha errada ou conta desativada.
            throw new ApiException(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS", "E-mail ou senha inválidos.");
        }
        attempts.reset(emailKey);
        AuthUser user = (AuthUser) auth.getPrincipal();
        startSession(user, request, response);
        authService.markLogin(user.id());
        return authService.me(user.id());
    }

    @Operation(summary = "Dados do usuário logado (401 se não houver sessão)")
    @GetMapping("/me")
    public MeResponse me() {
        return authService.me(currentUser.id());
    }

    @Operation(summary = "Solicita link de redefinição de senha (resposta sempre 204)")
    @PostMapping("/password/forgot")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void forgot(@Valid @RequestBody ForgotPasswordRequest req, HttpServletRequest request) {
        String key = "forgot-ip:" + request.getRemoteAddr();
        attempts.checkAllowed(key);
        attempts.recordFailure(key); // conta cada pedido para limitar abuso de envio de e-mails
        authService.requestPasswordReset(req.email());
    }

    @Operation(summary = "Redefine a senha com o token recebido por e-mail")
    @PostMapping("/password/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reset(@Valid @RequestBody ResetPasswordRequest req) {
        authService.resetPassword(req);
    }

    @Operation(summary = "Consulta um convite pelo token")
    @GetMapping("/invitations/{token}")
    public InvitationPreview invitation(@PathVariable String token) {
        return authService.previewInvitation(token);
    }

    @Operation(summary = "Aceita um convite e cria o acesso do colaborador")
    @PostMapping("/invitations/accept")
    public MeResponse acceptInvitation(@Valid @RequestBody AcceptInvitationRequest req,
                                       HttpServletRequest request, HttpServletResponse response) {
        User user = authService.acceptInvitation(req);
        startSession(user.toAuthUser(), request, response);
        return authService.me(user.getId());
    }

    /** Cria a sessão autenticada. Trocar o id da sessão evita ataques de fixação de sessão. */
    private void startSession(AuthUser user, HttpServletRequest request, HttpServletResponse response) {
        request.getSession(true);
        request.changeSessionId();
        user.eraseCredentials();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities()));
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }
}
