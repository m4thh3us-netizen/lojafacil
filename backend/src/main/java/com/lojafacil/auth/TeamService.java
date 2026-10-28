package com.lojafacil.auth;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lojafacil.auth.AuthDtos.InvitationView;
import com.lojafacil.auth.AuthDtos.InviteRequest;
import com.lojafacil.auth.AuthDtos.UpdateMemberRequest;
import com.lojafacil.auth.AuthDtos.UserView;
import com.lojafacil.shared.audit.AuditService;
import com.lojafacil.shared.config.AppProperties;
import com.lojafacil.shared.error.Errors;
import com.lojafacil.shared.security.CurrentUser;
import com.lojafacil.shared.util.Tokens;
import com.lojafacil.store.StoreRepository;

@Service
public class TeamService {
    public record TeamView(List<UserView> members, List<InvitationView> invitations) {
    }

    private final UserRepository users;
    private final Invitation.Repo invitations;
    private final StoreRepository stores;
    private final CurrentUser currentUser;
    private final MailService mail;
    private final AuditService audit;
    private final AppProperties props;

    public TeamService(UserRepository users, Invitation.Repo invitations, StoreRepository stores,
                          CurrentUser currentUser, MailService mail, AuditService audit, AppProperties props) {
        this.users = users;
        this.invitations = invitations;
        this.stores = stores;
        this.currentUser = currentUser;
        this.mail = mail;
        this.audit = audit;
        this.props = props;
    }

    @Transactional(readOnly = true)
    public TeamView list() {
        Long storeId = currentUser.storeId();
        Instant now = Instant.now();
        return new TeamView(
                users.findByStoreIdOrderByFullName(storeId).stream().map(UserView::of).toList(),
                invitations.findByStoreIdAndAcceptedAtIsNullAndRevokedAtIsNullOrderByCreatedAtDesc(storeId).stream()
                        .filter(i -> i.isPending(now))
                        .map(i -> new InvitationView(i.getId(), i.getEmail(), i.getRole(), i.getExpiresAt(), null))
                        .toList());
    }

    @Transactional
    public InvitationView invite(InviteRequest req) {
        if (req.role() == Role.OWNER) {
            throw Errors.badRequest("INVALID_ROLE", "Convites são apenas para Gerente ou Operador.");
        }
        String email = AuthService.normalizeEmail(req.email());
        if (users.existsByEmailIgnoreCase(email)) {
            throw Errors.conflict("EMAIL_IN_USE", "Já existe uma conta com este e-mail.");
        }
        Long storeId = currentUser.storeId();
        String token = Tokens.newToken();
        Invitation inv = new Invitation();
        inv.setStoreId(storeId);
        inv.setEmail(email);
        inv.setRole(req.role());
        inv.setTokenHash(Tokens.sha256(token));
        inv.setInvitedBy(currentUser.id());
        inv.setExpiresAt(Instant.now().plus(AuthService.INVITE_TTL));
        invitations.save(inv);

        String storeName = stores.findById(storeId).map(s -> s.getName()).orElse("");
        String link = props.frontendBaseUrl() + "/convite?token=" + token;
        mail.send(email, "Convite para a equipe de " + storeName,
                "Você foi convidado(a) para a equipe da loja " + storeName + ".\n\nAceite pelo link (válido por 7 dias):\n" + link);
        audit.record(storeId, "INVITATION", inv.getId(), "INVITATION_CREATED", email + " como " + req.role());
        return new InvitationView(inv.getId(), email, inv.getRole(), inv.getExpiresAt(), link);
    }

    @Transactional
    public void revoke(Long id) {
        Invitation inv = invitations.findByIdAndStoreId(id, currentUser.storeId()).orElseThrow(() -> Errors.notFound("Convite"));
        inv.setRevokedAt(Instant.now());
        audit.record(inv.getStoreId(), "INVITATION", id, "INVITATION_REVOKED", inv.getEmail());
    }

    @Transactional
    public UserView updateMember(Long id, UpdateMemberRequest req) {
        User user = users.findByIdAndStoreId(id, currentUser.storeId()).orElseThrow(() -> Errors.notFound("Usuário"));
        if (user.getId().equals(currentUser.id()) || user.getRole() == Role.OWNER) {
            throw Errors.rule("OWNER_PROTECTED", "O perfil e o acesso do proprietário não podem ser alterados por aqui.");
        }
        if (req.role() == Role.OWNER) {
            throw Errors.rule("OWNER_TRANSFER", "A transferência de propriedade não está disponível nesta versão.");
        }
        String before = user.getRole() + "/" + (user.isActive() ? "ativo" : "inativo");
        user.setRole(req.role());
        user.setActive(req.active());
        audit.record(user.getStoreId(), "USER", id, "MEMBER_UPDATED",
                before + " -> " + req.role() + "/" + (req.active() ? "ativo" : "inativo"));
        return UserView.of(user);
    }
}
