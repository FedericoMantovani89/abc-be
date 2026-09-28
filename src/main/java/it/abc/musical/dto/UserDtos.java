package it.abc.musical.dto;

import it.abc.musical.entities.User;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;
import org.springframework.data.web.PagedModel.PageMetadata;

import java.time.LocalDateTime;
import java.util.List;

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

    /**
     * Filtro gia' validato della lista admin (null = nessun filtro). Le date sono gia' limiti di
     * TIMESTAMP: {@code lastLoginBefore} e' esclusivo (mezzanotte del giorno dopo "fino al").
     */
    public record UserAdminFilter(
            String name, String email, Long roleId, Boolean active,
            LocalDateTime lastLoginFrom, LocalDateTime lastLoginBefore, boolean neverLoggedIn) {
    }

    /** Conteggi su tutti gli utenti non cancellati, indipendenti dal filtro. */
    public record UserAdminCounts(long total, long active, long inactive) {
    }

    /**
     * Risposta di GET /api/admin/users: {@code content} e {@code page} con la stessa forma di una
     * Page serializzata VIA_DTO (PagedModel), piu' {@code counts}.
     */
    public record UserAdminListResponse(
            List<UserAdminDto> content, PageMetadata page, UserAdminCounts counts) {

        public static UserAdminListResponse of(Page<UserAdminDto> page, UserAdminCounts counts) {
            return new UserAdminListResponse(page.getContent(),
                    new PageMetadata(page.getSize(), page.getNumber(), page.getTotalElements(), page.getTotalPages()),
                    counts);
        }
    }
}
