package it.abc.musical.services;

import it.abc.musical.dto.AdminShowDtos.AdminShowListDto;
import it.abc.musical.dto.AdminShowDtos.ShowUpsertRequest;
import it.abc.musical.dto.ShowDtos.ShowDetailDto;
import it.abc.musical.dto.ShowDtos.ShowSummaryDto;
import it.abc.musical.entities.Show;
import it.abc.musical.entities.ShowCast;
import it.abc.musical.entities.ShowImage;
import it.abc.musical.exceptions.NotFoundException;
import it.abc.musical.repositories.ShowRepository;
import it.abc.musical.util.HtmlSanitizer;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ShowService {

    private final ShowRepository showRepository;
    private final StorageService storageService;

    // ------------------------------------------------------------------ public

    @Transactional(readOnly = true)
    public List<ShowSummaryDto> listPublic() {
        return showRepository.findByDeletedAtIsNullOrderByProductionYearDesc().stream()
                .map(ShowSummaryDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ShowDetailDto getPublicDetail(Long id) {
        return ShowDetailDto.from(activeShow(id));
    }

    // ------------------------------------------------------------------ admin

    @Transactional(readOnly = true)
    public Page<AdminShowListDto> listAdmin(Pageable pageable) {
        return showRepository.findByDeletedAtIsNull(pageable).map(AdminShowListDto::from);
    }

    @Transactional(readOnly = true)
    public ShowDetailDto getAdminDetail(Long id) {
        return ShowDetailDto.from(activeShow(id));
    }

    @Transactional(readOnly = true)
    public List<String> castRoles(Long showId) {
        activeShow(showId);
        return showRepository.findDistinctCastRoleNames(showId);
    }

    @Transactional
    public Show create(ShowUpsertRequest request, Long userId) {
        Show show = new Show();
        show.setCreatedBy(userId);
        applyRequest(show, request, userId);
        if (request.posterPath() != null && !request.posterPath().isBlank()) {
            storageService.validateManagedPath(request.posterPath(), StorageService.POSTERS_DIR);
            show.setPosterImageUrl(request.posterPath());
        }
        return showRepository.save(show);
    }

    @Transactional
    public Show update(Long id, ShowUpsertRequest request, Long userId) {
        Show show = activeShow(id);
        applyRequest(show, request, userId);

        if (request.retainImageIds() != null) {
            Set<Long> retain = new HashSet<>(request.retainImageIds());
            show.getImages().removeIf(image -> {
                boolean remove = !retain.contains(image.getId());
                if (remove) {
                    storageService.delete(image.getImageUrl());
                }
                return remove;
            });
        }
        if (request.posterPath() != null && !request.posterPath().isBlank()
                && !request.posterPath().equals(show.getPosterImageUrl())) {
            storageService.validateManagedPath(request.posterPath(), StorageService.POSTERS_DIR);
            storageService.delete(show.getPosterImageUrl());
            show.setPosterImageUrl(request.posterPath());
        }
        return showRepository.save(show);
    }

    @Transactional
    public void softDelete(Long id) {
        Show show = activeShow(id);
        show.setDeletedAt(LocalDateTime.now());
        showRepository.save(show);
    }

    @Transactional
    public ShowImage addGalleryImage(Long showId, String imagePath, String caption) {
        storageService.validateManagedPath(imagePath, StorageService.GALLERY_DIR);
        Show show = activeShow(showId);
        ShowImage showImage = new ShowImage();
        showImage.setShow(show);
        showImage.setImageUrl(imagePath);
        showImage.setCaption(caption);
        showImage.setDisplayOrder(show.getImages().size());
        show.getImages().add(showImage);
        // `show` è già un'entità managed in questa transazione: showRepository.save(show)
        // richiamerebbe EntityManager.merge(), che per una collezione con un elemento
        // transient crea una copia interna e assegna l'id generato alla copia, non
        // all'oggetto `showImage` che restituiamo qui. Un flush esplicito lascia invece
        // che il cascade-persist automatico (già attivo perché `show` è managed) inserisca
        // la stessa istanza, popolandone correttamente l'id.
        showRepository.flush();
        return showImage;
    }

    @Transactional
    public void deleteGalleryImage(Long showId, Long imageId) {
        Show show = activeShow(showId);
        ShowImage image = show.getImages().stream()
                .filter(i -> i.getId().equals(imageId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Immagine non trovata"));
        storageService.delete(image.getImageUrl());
        show.getImages().remove(image);
        showRepository.save(show);
    }

    // ------------------------------------------------------------------ internals

    private Show activeShow(Long id) {
        return showRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new NotFoundException("Spettacolo non trovato"));
    }

    private void applyRequest(Show show, ShowUpsertRequest request, Long userId) {
        show.setTitle(request.title().trim());
        show.setPlot(HtmlSanitizer.sanitize(request.plot() != null ? request.plot() : ""));
        show.setDurationMinutes(request.durationMinutes() != null ? request.durationMinutes() : 0);
        show.setAgeRecommendation(request.ageRecommendation());
        show.setDirector(request.director());
        show.setSetDesigner(request.setDesigner());
        show.setCostumeDesigner(request.costumeDesigner());
        show.setChoreographer(request.choreographer());
        show.setHairAndMakeup(request.hairAndMakeup());
        show.setProducer(request.producer());
        show.setProductionYear(request.productionYear());
        show.setTrailerUrl(request.trailerUrl());
        show.setOfficialWebsiteUrl(request.officialWebsiteUrl());
        show.setReviewsUrl(request.reviewsUrl());
        show.setSocialMediaUrl(request.socialMediaUrl());
        show.setContentWarnings(request.contentWarnings() != null
                ? new ArrayList<>(request.contentWarnings()) : new ArrayList<>());
        show.setUpdatedBy(userId);

        // Il cast viene sostituito integralmente (orphanRemoval elimina i rimossi).
        show.getCast().clear();
        if (request.cast() != null) {
            int order = 0;
            for (var member : request.cast()) {
                ShowCast cast = new ShowCast();
                cast.setShow(show);
                cast.setFirstName(member.firstName() != null ? member.firstName() : "");
                cast.setLastName(member.lastName() != null ? member.lastName() : "");
                cast.setRoleName(member.roleName());
                cast.setSortOrder(member.sortOrder() != null ? member.sortOrder() : order);
                show.getCast().add(cast);
                order++;
            }
        }
    }
}
