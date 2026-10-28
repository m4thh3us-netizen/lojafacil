package com.lojafacil.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Objetos de entrada e saída do módulo de autenticação. */
public final class AuthDtos {

    private AuthDtos() {
    }

    static final String PASSWORD_REGEX = "^(?=.*[A-Za-z])(?=.*\\d).+$";
    static final String PASSWORD_MSG = "A senha deve conter letras e números.";

    public record OwnerData(
            @NotBlank(message = "Informe seu nome completo.") @Size(max = 120) String fullName,
            @NotBlank(message = "Informe o e-mail.") @Email(message = "E-mail inválido.") @Size(max = 160) String email,
            @NotBlank(message = "Informe a senha.") @Size(min = 8, max = 72, message = "A senha deve ter entre 8 e 72 caracteres.")
            @Pattern(regexp = PASSWORD_REGEX, message = PASSWORD_MSG) String password,
            @NotBlank(message = "Confirme a senha.") String passwordConfirmation,
            @Size(max = 30) String phone) {
    }

    public record StoreData(
            @NotBlank(message = "Informe o nome da loja.") @Size(max = 120) String name,
            @NotBlank(message = "Informe o identificador da loja.") @Size(min = 3, max = 60) String slug,
            @Size(max = 1000) String description) {
    }

    public record RegisterRequest(@NotNull @Valid OwnerData owner, @NotNull @Valid StoreData store) {
    }

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {
    }

    public record ForgotPasswordRequest(@NotBlank @Email String email) {
    }

    public record ResetPasswordRequest(
            @NotBlank String token,
            @NotBlank @Size(min = 8, max = 72, message = "A senha deve ter entre 8 e 72 caracteres.")
            @Pattern(regexp = PASSWORD_REGEX, message = PASSWORD_MSG) String password,
            @NotBlank String passwordConfirmation) {
    }

    public record AcceptInvitationRequest(
            @NotBlank String token,
            @NotBlank @Size(max = 120) String fullName,
            @NotBlank @Size(min = 8, max = 72, message = "A senha deve ter entre 8 e 72 caracteres.")
            @Pattern(regexp = PASSWORD_REGEX, message = PASSWORD_MSG) String password,
            @NotBlank String passwordConfirmation,
            @Size(max = 30) String phone) {
    }

    public record InvitationPreview(String storeName, String email, Role role) {
    }

    public record UserView(Long id, String fullName, String email, String phone, Role role, boolean active) {
        static UserView of(User u) {
            return new UserView(u.getId(), u.getFullName(), u.getEmail(), u.getPhone(), u.getRole(), u.isActive());
        }
    }

    public record StoreSummary(Long id, String name, String slug, boolean demo) {
    }

    /** Resposta de /me: quem está logado, em qual loja e com qual perfil. */
    public record MeResponse(UserView user, StoreSummary store, boolean demoMode) {
    }

    public record SlugAvailability(String slug, boolean available, String reason) {
    }

    public record InviteRequest(
            @NotBlank @Email @Size(max = 160) String email,
            @NotNull Role role) {
    }

    public record InvitationView(Long id, String email, Role role, java.time.Instant expiresAt, String inviteLink) {
    }

    public record UpdateMemberRequest(@NotNull Role role, boolean active) {
    }
}
