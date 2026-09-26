package it.abc.musical.dto;

import it.abc.musical.entities.ContentWarning;
import it.abc.musical.entities.Show;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/** Limiti di lunghezza: quelli delle colonne VARCHAR di shows/show_cast/show_images (V001). */
public final class AdminShowDtos {

    private AdminShowDtos() {
    }

    public record CastMemberRequest(
            @Size(max = 255) String firstName,
            @Size(max = 255) String lastName,
            @NotBlank @Size(max = 255) String roleName,
            Integer sortOrder) {
    }

    public record ShowUpsertRequest(
            @NotBlank @Size(max = 255) String title,
            String plot,
            Integer durationMinutes,
            Integer ageRecommendation,
            @Size(max = 255) String director,
            @Size(max = 255) String setDesigner,
            @Size(max = 255) String costumeDesigner,
            @Size(max = 255) String choreographer,
            @Size(max = 255) String hairAndMakeup,
            @Size(max = 255) String producer,
            Integer productionYear,
            @Size(max = 512) String trailerUrl,
            @Size(max = 512) String officialWebsiteUrl,
            @Size(max = 512) String reviewsUrl,
            @Size(max = 512) String socialMediaUrl,
            List<CastMemberRequest> cast,
            List<ContentWarning> contentWarnings,
            List<Long> retainImageIds,
            @Size(max = 512) String posterPath,
            Boolean showInHome,
            Integer heroFocusX,
            Integer heroFocusY,
            Integer heroZoomDesktop,
            Integer heroZoomMobile,
            Integer heroFocusMobileX,
            Integer heroFocusMobileY) {
    }

    public record GalleryImageAttachRequest(
            @NotBlank @Size(max = 512) String imagePath,
            @Size(max = 255) String caption) {
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
