package com.lojafacil.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lojafacil.auth.AuthDtos.AcceptInvitationRequest;
import com.lojafacil.auth.AuthDtos.InvitationPreview;
import com.lojafacil.auth.AuthDtos.MeResponse;
import com.lojafacil.auth.AuthDtos.RegisterRequest;
import com.lojafacil.auth.AuthDtos.ResetPasswordRequest;
import com.lojafacil.auth.AuthDtos.SlugAvailability;
import com.lojafacil.auth.AuthDtos.StoreSummary;
import com.lojafacil.auth.AuthDtos.UserView;
import com.lojafacil.shared.audit.AuditService;
import com.lojafacil.shared.config.AppProperties;
import com.lojafacil.shared.error.Errors;
import com.lojafacil.shared.security.AuthUser;
import com.lojafacil.shared.util.Slugs;
import com.lojafacil.shared.util.Tokens;
import com.lojafacil.store.Store;
import com.lojafacil.store.StoreRepository;

/** Regras de cadastro, recuperação de senha e convites. */
@Service
public class AuthService implements UserDetailsService {

    static final Duration RESET_TOKEN_TTL = Duration.ofMinutes(30);
    static final Duration INVITE_TTL = Duration.ofDays(7);

    private final UserRepository users;
    private final StoreRepository stores;
    private final PasswordResetToken.Repo resetTokens;
    private final Invitation.Repo invitations;
    private final PasswordEncoder passwordEncoder;
    private final MailService mail;
    private final AuditService audit;
    private final AppProperties props;
    private final ApplicationEventPublisher events;

    public AuthService(UserRepository users, StoreRepository stores, PasswordResetToken.Repo resetTokens,
                       Invitation.Repo invitations, PasswordEncoder passwordEncoder, MailService mail,
                       AuditService audit, AppProperties props, ApplicationEventPublisher events) {
        this.users = users;
        this.stores = stores;
        this.resetTokens = resetTokens;
        this.invitations = invitations;
        this.passwordEncoder = passwordEncoder;
        this.mail = mail;
        this.audit = audit;
        this.props = props;
        this.events = events;
    }

    /** Usado pelo Spring Security durante o login. */
    @Override
    @Transactional(readOnly = true)
    public AuthUser loadUserByUsername(String email) {
        return users.findByEmailIgnoreCase(email.trim())
                .map(User::toAuthUser)
                .orElseThrow(() -> new UsernameNotFoundException("not found"));
    }

    @Transactional(readOnly = true)
    public SlugAvailability checkSlug(String rawSlug) {
        String slug = rawSlug == null ? "" : rawSlug.trim().toLowerCase(Locale.ROOT);
        if (!Slugs.isValidStoreSlug(slug)) {
            return new SlugAvailability(slug, false,
                    "Use de 3 a 60 caracteres: letras minúsculas, números e hífen (sem começar ou terminar com hífen).");
        }
        if (stores.existsBySlug(slug)) {
            return new SlugAvailability(slug, false, "Este identificador já está em uso.");
        }
        return new SlugAvailability(slug, true, null);
    }

    /** Cadastro inicial: cria a loja e o usuário proprietário na mesma transação. */
    @Transactional
    public User register(RegisterRequest req) {
        if (!req.owner().password().equals(req.owner().passwordConfirmation())) {
            throw Errors.badRequest("PASSWORD_MISMATCH", "A confirmação de senha não confere.");
        }
        SlugAvailability slug = checkSlug(req.store().slug());
        if (!slug.available()) {
            throw Errors.conflict("SLUG_UNAVAILABLE", slug.reason());
        }
        String email = normalizeEmail(req.owner().email());
        if (users.existsByEmailIgnoreCase(email)) {
            throw Errors.conflict("EMAIL_IN_USE", "Já existe uma conta com este e-mail.");
        }

        Store store = new Store();
        store.setName(req.store().name().trim());
        store.setSlug(slug.slug());
        store.setDescription(blankToNull(req.store().description()));
        store.setContactEmail(email);
        stores.save(store);

        User owner = new User();
        owner.setStoreId(store.getId());
        owner.setFullName(req.owner().fullName().trim());
        owner.setEmail(email);
        owner.setPhone(blankToNull(req.owner().phone()));
        owner.setPasswordHash(passwordEncoder.encode(req.owner().password()));
        owner.setRole(Role.OWNER);
        users.save(owner);

        events.publishEvent(new StoreCreatedEvent(store.getId()));
        audit.record(store.getId(), "STORE", store.getId(), "STORE_CREATED", "Loja criada por " + email);
        return owner;
    }

    @Transactional
    public void markLogin(Long userId) {
        users.findById(userId).ifPresent(u -> u.setLastLoginAt(Instant.now()));
    }

    @Transactional(readOnly = true)
    public MeResponse me(Long userId) {
        User u = users.findById(userId).orElseThrow(() -> Errors.notFound("Usuário"));
        Store s = stores.findById(u.getStoreId()).orElseThrow(() -> Errors.notFound("Loja"));
        return new MeResponse(UserView.of(u), new StoreSummary(s.getId(), s.getName(), s.getSlug(), s.isDemo()), props.demoMode());
    }

    /**
     * Gera o link de redefinição. Responde da mesma forma exista ou não o e-mail,
     * para não permitir descobrir quais e-mails estão cadastrados.
     */
    @Transactional
    public void requestPasswordReset(String rawEmail) {
        users.findByEmailIgnoreCase(normalizeEmail(rawEmail)).filter(User::isActive).ifPresent(user -> {
            Instant now = Instant.now();
            resetTokens.invalidateAll(user.getId(), now);
            String token = Tokens.newToken();
            resetTokens.save(new PasswordResetToken(user.getId(), Tokens.sha256(token), now.plus(RESET_TOKEN_TTL)));
            String link = props.frontendBaseUrl() + "/redefinir-senha?token=" + token;
            mail.send(user.getEmail(), "Redefinição de senha - LojaFácil",
                    "Olá, " + user.getFullName() + ".\n\nPara criar uma nova senha, acesse o link abaixo (válido por 30 minutos):\n"
                            + link + "\n\nSe você não pediu a redefinição, ignore este e-mail.");
        });
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest req) {
        if (!req.password().equals(req.passwordConfirmation())) {
            throw Errors.badRequest("PASSWORD_MISMATCH", "A confirmação de senha não confere.");
        }
        Instant now = Instant.now();
        PasswordResetToken token = resetTokens.findByTokenHash(Tokens.sha256(req.token()))
                .filter(t -> t.isUsable(now))
                .orElseThrow(() -> Errors.badRequest("INVALID_TOKEN", "Link inválido ou expirado. Solicite um novo."));
        User user = users.findById(token.getUserId()).orElseThrow(() -> Errors.notFound("Usuário"));
        token.setUsedAt(now);
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        audit.record(user.getStoreId(), "USER", user.getId(), "PASSWORD_RESET", null);
    }

    @Transactional(readOnly = true)
    public InvitationPreview previewInvitation(String token) {
        Invitation inv = pendingInvitation(token);
        Store store = stores.findById(inv.getStoreId()).orElseThrow(() -> Errors.notFound("Loja"));
        return new InvitationPreview(store.getName(), inv.getEmail(), inv.getRole());
    }

    @Transactional
    public User acceptInvitation(AcceptInvitationRequest req) {
        if (!req.password().equals(req.passwordConfirmation())) {
            throw Errors.badRequest("PASSWORD_MISMATCH", "A confirmação de senha não confere.");
        }
        Invitation inv = pendingInvitation(req.token());
        if (users.existsByEmailIgnoreCase(inv.getEmail())) {
            throw Errors.conflict("EMAIL_IN_USE", "Já existe uma conta com este e-mail.");
        }
        User user = new User();
        user.setStoreId(inv.getStoreId());
        user.setEmail(inv.getEmail());
        user.setFullName(req.fullName().trim());
        user.setPhone(blankToNull(req.phone()));
        user.setRole(inv.getRole());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        users.save(user);
        inv.setAcceptedAt(Instant.now());
        audit.record(inv.getStoreId(), "USER", user.getId(), "INVITATION_ACCEPTED", inv.getEmail() + " como " + inv.getRole());
        return user;
    }

    private Invitation pendingInvitation(String token) {
        return invitations.findByTokenHash(Tokens.sha256(token))
                .filter(i -> i.isPending(Instant.now()))
                .orElseThrow(() -> Errors.badRequest("INVALID_TOKEN", "Convite inválido, expirado ou já utilizado."));
    }

    static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
