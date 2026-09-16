package it.abc.musical.dto;

import it.abc.musical.entities.User;
import it.abc.musical.validation.ValidPassword;
import jakarta.validation.constraints.NotBlank;

public final class AccountDtos {

    private AccountDtos() {
    }

    /** Dati dell'utente autenticato per l'area account. */
    public record AccountDto(
            String email, String firstName, String lastName,
            String role, String oauthProvider, boolean hasPassword) {

        public static AccountDto from(User u) {
            return new AccountDto(u.getEmail(), u.getFirstName(), u.getLastName(),
                    u.getRole().getName(), u.getOauthProvider(), u.getPassword() != null);
        }
    }

    public record UpdateProfileRequest(@NotBlank String firstName, @NotBlank String lastName) {
    }

    /** currentPassword è null per gli utenti OAuth che impostano una password per la prima volta. */
    public record ChangePasswordRequest(String currentPassword, @ValidPassword String newPassword) {
    }

    /** currentPassword richiesta solo se l'utente ha una password locale. */
    public record DeleteAccountRequest(String currentPassword) {
    }
}
