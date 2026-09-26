package it.abc.musical.dto;

import it.abc.musical.validation.ValidPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Limiti di lunghezza: quelli delle colonne di {@code users} e {@code tokens}; la password
 * si ferma a 72 caratteri perche' BCrypt non ne usa di piu' (e oltre rifiuta la codifica).
 */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 255) String email,
            @ValidPassword @Size(max = 72) String password,
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName) {
    }

    public record TokenResponse(String accessToken, String tokenType, long expiresIn) {
    }

    public record ForgotPasswordRequest(@NotBlank @Email @Size(max = 255) String email) {
    }

    public record ResetPasswordRequest(
            @NotBlank @Size(max = 500) String token,
            @ValidPassword @Size(max = 72) String newPassword) {
    }

    public record ResendVerificationRequest(@NotBlank @Email @Size(max = 255) String email) {
    }
}
