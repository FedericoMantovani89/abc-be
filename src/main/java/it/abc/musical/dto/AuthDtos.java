package it.abc.musical.dto;

import it.abc.musical.validation.ValidPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Email String email,
            @ValidPassword String password,
            @NotBlank String firstName,
            @NotBlank String lastName) {
    }

    public record TokenResponse(String accessToken, String tokenType, long expiresIn) {
    }

    public record ForgotPasswordRequest(@NotBlank @Email String email) {
    }

    public record ResetPasswordRequest(@NotBlank String token, @ValidPassword String newPassword) {
    }

    public record ResendVerificationRequest(@NotBlank @Email String email) {
    }
}
