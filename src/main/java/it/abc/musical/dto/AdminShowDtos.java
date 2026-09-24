package it.abc.musical.dto;

import it.abc.musical.entities.ContentWarning;
import it.abc.musical.entities.Show;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;
import java.util.List;

public final class AdminShowDtos {

    private AdminShowDtos() {
    }

    public record CastMemberRequest(String firstName, String lastName,
                                    @NotBlank String roleName, Integer sortOrder) {
    }

    public record ShowUpsertRequest(
            @NotBlank String title,
            String plot,
            Integer durationMinutes,
            Integer ageRecommendation,
            String director,
            String setDesigner,
            String costumeDesigner,
            String choreographer,
            String hairAndMakeup,
            String producer,
            Integer productionYear,
            String trailerUrl,
            String officialWebsiteUrl,
            String reviewsUrl,
            String socialMediaUrl,
            List<CastMemberRequest> cast,
            List<ContentWarning> contentWarnings,
            List<Long> retainImageIds,
            String posterPath,
            Boolean showInHome,
            Integer heroFocusX,
            Integer heroFocusY,
            Integer heroZoomDesktop,
            Integer heroZoomMobile,
            Integer heroFocusMobileX,
            Integer heroFocusMobileY) {
    }

    public record GalleryImageAttachRequest(@NotBlank String imagePath, String caption) {
    }

    public record AdminShowListDto(
            Long id, String title, Integer productionYear, String posterImageUrl,
            int castSize, LocalDateTime updatedAt, Integer heroFocusX, Integer heroFocusY,
            Integer heroZoomDesktop, Integer heroZoomMobile,
            Integer heroFocusMobileX, Integer heroFocusMobileY) {

        public static AdminShowListDto from(Show s) {
            return new AdminShowListDto(s.getId(), s.getTitle(), s.getProductionYear(),
                    s.getPosterImageUrl(), s.getCast().size(), s.getUpdatedAt(),
                    s.getHeroFocusX(), s.getHeroFocusY(),
                    s.getHeroZoomDesktop(), s.getHeroZoomMobile(),
                    s.getHeroFocusMobileX(), s.getHeroFocusMobileY());
        }
    }
}
