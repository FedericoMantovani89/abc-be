package it.abc.musical.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public final class AdminEventDtos {

    private AdminEventDtos() {
    }

    public record EventUpsertRequest(
            @NotBlank String title,
            String description,
            @NotNull LocalDateTime eventDate,
            @NotBlank String locationVenue,
            String locationAddress,
            String locationCity,
            String locationProvince,
            LocalDateTime bookingOpenAt,
            LocalDateTime bookingCloseAt,
            String bookingLink,
            String contactEmail,
            String contactPhone,
            Long eventTypeId,
            Long showId) {
    }
}
