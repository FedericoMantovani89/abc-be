package it.abc.musical.dto;

import it.abc.musical.entities.Communication;
import it.abc.musical.entities.CommunicationType;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

public final class CommunicationDtos {

    private CommunicationDtos() {
    }

    public record CommunicationTypeDto(Long id, String name, String description, boolean active) {

        public static CommunicationTypeDto from(CommunicationType t) {
            return new CommunicationTypeDto(t.getId(), t.getName(), t.getDescription(), t.isActive());
        }
    }

    public record CommunicationDto(
            Long id, String title, String content,
            CommunicationTypeDto communicationType,
            String priority, boolean pinned,
            LocalDateTime publishedAt, LocalDateTime expiresAt,
            String targetRoles, LocalDateTime createdAt) {

        public static CommunicationDto from(Communication c) {
            return new CommunicationDto(c.getId(), c.getTitle(), c.getContent(),
                    c.getCommunicationType() != null
                            ? CommunicationTypeDto.from(c.getCommunicationType()) : null,
                    c.getPriority(), Boolean.TRUE.equals(c.getPinned()),
                    c.getPublishedAt(), c.getExpiresAt(),
                    c.getTargetRoles(), c.getCreatedAt());
        }
    }

    public record CommunicationUpsertRequest(
            @NotBlank String title,
            @NotBlank String content,
            Long communicationTypeId,
            String priority,
            Boolean pinned,
            LocalDateTime publishedAt,
            LocalDateTime expiresAt,
            String targetRoles) {
    }
}
