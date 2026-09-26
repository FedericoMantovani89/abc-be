package it.abc.musical;

import it.abc.musical.dto.AdminEventDtos.EventUpsertRequest;
import it.abc.musical.dto.AdminShowDtos.ShowUpsertRequest;
import it.abc.musical.dto.AdminShowDtos.CastMemberRequest;
import it.abc.musical.entities.ContentWarning;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Fabbriche per {@link EventUpsertRequest}/{@link ShowUpsertRequest}, al posto dei costruttori
 * posizionali a 21-26 argomenti (quasi tutti null nei test): ogni fabbrica parte da valori validi
 * di default e i metodi fluenti cambiano solo il campo che il test vuole far variare.
 */
public final class TestRequests {

    private TestRequests() {
    }

    public static EventRequestBuilder event() {
        return new EventRequestBuilder();
    }

    public static ShowRequestBuilder show() {
        return new ShowRequestBuilder();
    }

    public static final class EventRequestBuilder {
        private LocalDateTime eventDate = LocalDateTime.of(2027, 1, 9, 20, 0);
        private LocalDateTime bookingOpenAt;
        private LocalDateTime bookingCloseAt;
        private Long posterSourceEventId;
        private Integer heroFocusX;
        private Integer heroFocusY;
        private Integer heroZoomDesktop;
        private Integer heroZoomMobile;

        public EventRequestBuilder eventDate(LocalDateTime eventDate) {
            this.eventDate = eventDate;
            return this;
        }

        public EventRequestBuilder bookingWindow(LocalDateTime openAt, LocalDateTime closeAt) {
            this.bookingOpenAt = openAt;
            this.bookingCloseAt = closeAt;
            return this;
        }

        public EventRequestBuilder posterSource(Long posterSourceEventId) {
            this.posterSourceEventId = posterSourceEventId;
            return this;
        }

        public EventRequestBuilder heroFocus(Integer x, Integer y) {
            this.heroFocusX = x;
            this.heroFocusY = y;
            return this;
        }

        public EventRequestBuilder heroZoom(Integer desktop, Integer mobile) {
            this.heroZoomDesktop = desktop;
            this.heroZoomMobile = mobile;
            return this;
        }

        public EventUpsertRequest build() {
            return new EventUpsertRequest(
                    "Saggio di fine anno", null, eventDate, "Teatro Comunale",
                    null, null, null, bookingOpenAt, bookingCloseAt, null, null, null,
                    null, null, posterSourceEventId,
                    heroFocusX, heroFocusY, heroZoomDesktop, heroZoomMobile, null, null);
        }
    }

    public static final class ShowRequestBuilder {
        private List<CastMemberRequest> cast;
        private List<ContentWarning> contentWarnings;
        private List<Long> retainImageIds;
        private String posterPath;
        private Integer heroFocusX;
        private Integer heroFocusY;
        private Integer heroZoomDesktop;
        private Integer heroZoomMobile;

        public ShowRequestBuilder cast(List<CastMemberRequest> cast) {
            this.cast = cast;
            return this;
        }

        public ShowRequestBuilder retainImages(List<Long> retainImageIds, String posterPath) {
            this.retainImageIds = retainImageIds;
            this.posterPath = posterPath;
            return this;
        }

        public ShowRequestBuilder heroFocus(Integer x, Integer y) {
            this.heroFocusX = x;
            this.heroFocusY = y;
            return this;
        }

        public ShowRequestBuilder heroZoom(Integer desktop, Integer mobile) {
            this.heroZoomDesktop = desktop;
            this.heroZoomMobile = mobile;
            return this;
        }

        public ShowUpsertRequest build() {
            return new ShowUpsertRequest(
                    "Il Piccolo Principe", null, null, null, null, null, null, null, null, null,
                    null, null, null, null, null,
                    cast, contentWarnings, retainImageIds, posterPath, null,
                    heroFocusX, heroFocusY, heroZoomDesktop, heroZoomMobile, null, null);
        }
    }
}
