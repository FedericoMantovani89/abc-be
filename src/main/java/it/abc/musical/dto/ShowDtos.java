package it.abc.musical.dto;

import it.abc.musical.entities.ContentWarning;
import it.abc.musical.entities.Show;

import java.time.LocalDateTime;
import java.util.List;

public final class ShowDtos {

    private ShowDtos() {
    }

    public record CastMemberDto(Long id, String firstName, String lastName, String roleName, int sortOrder) {
    }

    public record ShowImageDto(Long id, String imageUrl, String caption, Integer displayOrder) {
    }

    public record ShowSummaryDto(
            Long id, String title, Integer productionYear, int durationMinutes,
            Integer ageRecommendation, String posterImageUrl, String director, LocalDateTime createdAt,
            boolean showInHome, Integer heroFocusX, Integer heroFocusY,
            Integer heroZoomDesktop, Integer heroZoomMobile,
            Integer heroFocusMobileX, Integer heroFocusMobileY) {

        public static ShowSummaryDto from(Show s) {
            return new ShowSummaryDto(s.getId(), s.getTitle(), s.getProductionYear(),
                    s.getDurationMinutes(), s.getAgeRecommendation(), s.getPosterImageUrl(),
                    s.getDirector(), s.getCreatedAt(), s.isShowInHome(), s.getHeroFocusX(), s.getHeroFocusY(),
                    s.getHeroZoomDesktop(), s.getHeroZoomMobile(),
                    s.getHeroFocusMobileX(), s.getHeroFocusMobileY());
        }
    }

    public record ShowDetailDto(
            Long id, String title, String plot, int durationMinutes, Integer ageRecommendation,
            String director, String setDesigner, String costumeDesigner, String choreographer,
            String hairAndMakeup, String producer, Integer productionYear,
            String trailerUrl, String officialWebsiteUrl, String reviewsUrl, String socialMediaUrl,
            String posterImageUrl, boolean showInHome, List<ContentWarning> contentWarnings,
            List<CastMemberDto> cast, List<ShowImageDto> images, Integer heroFocusX, Integer heroFocusY,
            Integer heroZoomDesktop, Integer heroZoomMobile,
            Integer heroFocusMobileX, Integer heroFocusMobileY) {

        public static ShowDetailDto from(Show s) {
            return new ShowDetailDto(
                    s.getId(), s.getTitle(), s.getPlot(), s.getDurationMinutes(), s.getAgeRecommendation(),
                    s.getDirector(), s.getSetDesigner(), s.getCostumeDesigner(), s.getChoreographer(),
                    s.getHairAndMakeup(), s.getProducer(), s.getProductionYear(),
                    s.getTrailerUrl(), s.getOfficialWebsiteUrl(), s.getReviewsUrl(), s.getSocialMediaUrl(),
                    s.getPosterImageUrl(), s.isShowInHome(), s.getContentWarnings(),
                    s.getCast().stream()
                            .map(c -> new CastMemberDto(c.getId(), c.getFirstName(), c.getLastName(),
                                    c.getRoleName(), c.getSortOrder()))
                            .toList(),
                    s.getImages().stream()
                            .map(i -> new ShowImageDto(i.getId(), i.getImageUrl(), i.getCaption(),
                                    i.getDisplayOrder()))
                            .toList(),
                    s.getHeroFocusX(), s.getHeroFocusY(),
                    s.getHeroZoomDesktop(), s.getHeroZoomMobile(),
                    s.getHeroFocusMobileX(), s.getHeroFocusMobileY());
        }
    }
}
