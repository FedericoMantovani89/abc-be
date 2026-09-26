package it.abc.musical.dto;

import it.abc.musical.entities.User;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public final class UserDtos {

    private UserDtos() {
    }

    public record UserAdminDto(
            Long id, String email, String firstName, String lastName,
            String role, boolean active, boolean verified,
            String oauthProvider, LocalDateTime lastLoginAt, LocalDateTime createdAt) {

        public static UserAdminDto from(User u) {
            return new UserAdminDto(u.getId(), u.getEmail(), u.getFirstName(), u.getLastName(),
                    u.getRole().getName(), u.isActive(), u.isVerified(),
                    u.getOauthProvider(), u.getLastLoginAt(), u.getCreatedAt());
        }
    }

    public record UserStatusRequest(@NotNull Boolean active) {
    }

    public record UserRoleRequest(@NotBlank @Size(max = 50) String roleName) {
    }
}
