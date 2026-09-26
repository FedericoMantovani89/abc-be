package it.abc.musical.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/** Limiti di lunghezza: quelli delle colonne VARCHAR di events (V001). */
public final class AdminEventDtos {

    private AdminEventDtos() {
    }

    public record EventUpsertRequest(
            @NotBlank @Size(max = 255) String title,
            String description,
            @NotNull LocalDateTime eventDate,
            @NotBlank @Size(max = 255) String locationVenue,
            @Size(max = 500) String locationAddress,
            @Size(max = 100) String locationCity,
            @Size(max = 2) String locationProvince,
            LocalDateTime bookingOpenAt,
            LocalDateTime bookingCloseAt,
            @Size(max = 512) String bookingLink,
            @Size(max = 100) String contactEmail,
            @Size(max = 20) String contactPhone,
            Long eventTypeId,
            Long showId,
            Long posterSourceEventId,
            Integer heroFocusX,
            Integer heroFocusY,
            Integer heroZoomDesktop,
            Integer heroZoomMobile,
            Integer heroFocusMobileX,
            Integer heroFocusMobileY) {
    }
}
