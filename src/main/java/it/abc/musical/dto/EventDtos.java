package it.abc.musical.dto;

import it.abc.musical.entities.Event;

import java.time.LocalDateTime;

public final class EventDtos {

    private EventDtos() {
    }

    public record EventSummaryDto(
            Long id, String title, LocalDateTime eventDate,
            String locationVenue, String locationCity, String locationProvince,
            String posterImageUrl, String eventTypeName, Long showId) {

        public static EventSummaryDto from(Event e) {
            return new EventSummaryDto(e.getId(), e.getTitle(), e.getEventDate(),
                    e.getLocationVenue(), e.getLocationCity(), e.getLocationProvince(),
                    e.getPosterImageUrl(),
                    e.getEventType() != null ? e.getEventType().getName() : null,
                    e.getShow() != null ? e.getShow().getId() : null);
        }
    }

    public record EventDetailDto(
            Long id, String title, String description, LocalDateTime eventDate,
            String locationVenue, String locationAddress, String locationCity, String locationProvince,
            String posterImageUrl, LocalDateTime bookingOpenAt, LocalDateTime bookingCloseAt,
            String bookingLink, String contactEmail, String contactPhone,
            Long eventTypeId, String eventTypeName,
            Long showId, String showTitle, String showPosterImageUrl) {

        public static EventDetailDto from(Event e) {
            return new EventDetailDto(e.getId(), e.getTitle(), e.getDescription(), e.getEventDate(),
                    e.getLocationVenue(), e.getLocationAddress(), e.getLocationCity(), e.getLocationProvince(),
                    e.getPosterImageUrl(), e.getBookingOpenAt(), e.getBookingCloseAt(),
                    e.getBookingLink(), e.getContactEmail(), e.getContactPhone(),
                    e.getEventType() != null ? e.getEventType().getId() : null,
                    e.getEventType() != null ? e.getEventType().getName() : null,
                    e.getShow() != null ? e.getShow().getId() : null,
                    e.getShow() != null ? e.getShow().getTitle() : null,
                    e.getShow() != null ? e.getShow().getPosterImageUrl() : null);
        }
    }
}
