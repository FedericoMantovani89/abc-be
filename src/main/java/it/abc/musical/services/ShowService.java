package it.abc.musical.services;

import it.abc.musical.dto.AdminShowDtos.AdminShowListDto;
import it.abc.musical.dto.AdminShowDtos.ShowUpsertRequest;
import it.abc.musical.dto.ShowDtos.ShowDetailDto;
import it.abc.musical.dto.ShowDtos.ShowSummaryDto;
import it.abc.musical.entities.Show;
import it.abc.musical.entities.ShowCast;
import it.abc.musical.entities.ShowImage;
import it.abc.musical.enums.UploadTargetType;
import it.abc.musical.exceptions.BadRequestException;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    /**
     * Grafie diverse dello stesso ruolo a meno di maiuscole/minuscole (es. "corpo di ballo" e
     * "Corpo di ballo") sono lo stesso ruolo agli occhi dell'admin: una voce sola, con la
     * grafia più frequente tra quelle presenti sullo spettacolo.
     */
    @Transactional(readOnly = true)
    public List<String> castRoles(Long showId) {
        activeShow(showId);
        return mostFrequentSpellingByLowerCase(showRepository.findCastRoleNames(showId)).values().stream()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    @Transactional
    public Show create(ShowUpsertRequest request, Long userId) {
        Show show = new Show();
        show.setCreatedBy(userId);
        applyRequest(show, request, userId);
        if (request.posterPath() != null && !request.posterPath().isBlank()) {
            storageService.validateManagedPath(request.posterPath(), UploadTargetType.SHOW_POSTER);
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
                    storageService.deleteAfterCommit(image.getImageUrl());
                }
                return remove;
            });
        }
        if (request.posterPath() != null && !request.posterPath().isBlank()
                && !request.posterPath().equals(show.getPosterImageUrl())) {
            storageService.validateManagedPath(request.posterPath(), UploadTargetType.SHOW_POSTER);
            storageService.deleteAfterCommit(show.getPosterImageUrl());
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
        storageService.validateManagedPath(imagePath, UploadTargetType.SHOW_GALLERY_IMAGE);
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

    // ------------------------------------------------------------------ internals

    private Show activeShow(Long id) {
        return showRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new NotFoundException("Spettacolo non trovato"));
    }

    /**
     * Punto focale della locandina: entrambi assenti (centro) oppure entrambi in 0..100.
     * Un solo valore presente, o fuori range, è un input incoerente e va rifiutato qui invece
     * che lasciarlo diventare un crop CSS silenziosamente sbagliato lato frontend.
     */
    private void validateHeroFocus(Integer heroFocusX, Integer heroFocusY) {
        boolean bothNull = heroFocusX == null && heroFocusY == null;
        boolean bothInRange = heroFocusX != null && heroFocusY != null
                && heroFocusX >= 0 && heroFocusX <= 100
                && heroFocusY >= 0 && heroFocusY <= 100;
        if (!bothNull && !bothInRange) {
            throw new BadRequestException("Punto focale non valido");
        }
    }

    /**
     * Punto focale mobile: stessa regola del punto focale desktop, ma indipendente da esso
     * (ritaglio separato per il telefono).
     */
    private void validateHeroFocusMobile(Integer heroFocusMobileX, Integer heroFocusMobileY) {
        boolean bothNull = heroFocusMobileX == null && heroFocusMobileY == null;
        boolean bothInRange = heroFocusMobileX != null && heroFocusMobileY != null
                && heroFocusMobileX >= 0 && heroFocusMobileX <= 100
                && heroFocusMobileY >= 0 && heroFocusMobileY <= 100;
        if (!bothNull && !bothInRange) {
            throw new BadRequestException("Punto focale non valido");
        }
    }

    /**
     * Fattore di zoom della locandina in hero: desktop e mobile sono indipendenti fra loro e
     * dal punto focale, ciascuno o assente (100, cioè invariato) o in 10..300.
     */
    private void validateHeroZoom(Integer heroZoomDesktop, Integer heroZoomMobile) {
        if (!isValidZoom(heroZoomDesktop) || !isValidZoom(heroZoomMobile)) {
            throw new BadRequestException("Zoom non valido");
        }
    }

    private static boolean isValidZoom(Integer zoom) {
        return zoom == null || (zoom >= 10 && zoom <= 300);
    }

    private void applyRequest(Show show, ShowUpsertRequest request, Long userId) {
        validateHeroFocus(request.heroFocusX(), request.heroFocusY());
        validateHeroFocusMobile(request.heroFocusMobileX(), request.heroFocusMobileY());
        validateHeroZoom(request.heroZoomDesktop(), request.heroZoomMobile());
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
        if (request.showInHome() != null) {
            show.setShowInHome(request.showInHome());
        }
        show.setHeroFocusX(request.heroFocusX());
        show.setHeroFocusY(request.heroFocusY());
        show.setHeroFocusMobileX(request.heroFocusMobileX());
        show.setHeroFocusMobileY(request.heroFocusMobileY());
        show.setHeroZoomDesktop(request.heroZoomDesktop());
        show.setHeroZoomMobile(request.heroZoomMobile());
        show.setUpdatedBy(userId);

        // Il cast viene sostituito integralmente (orphanRemoval elimina i rimossi).
        show.getCast().clear();
        if (request.cast() != null) {
            // Grafie già in uso altrove (per spettacolo diverso da questo): se il ruolo che
            // sta per essere scritto esiste già a meno di maiuscole/minuscole, riusa quella
            // invece di crearne una seconda. Calcolata una volta sola per salvataggio; questo
            // spettacolo non compare perché il suo cast è stato appena svuotato sopra.
            Map<String, String> canonicalByLowerCase = mostFrequentSpellingByLowerCase(
                    showRepository.findCastRoleNamesExcludingShow(show.getId() != null ? show.getId() : -1L));
            int order = 0;
            for (var member : request.cast()) {
                ShowCast cast = new ShowCast();
                cast.setShow(show);
                cast.setFirstName(member.firstName() != null ? member.firstName() : "");
                cast.setLastName(member.lastName() != null ? member.lastName() : "");
                cast.setRoleName(normalizeRoleName(member.roleName(), canonicalByLowerCase));
                cast.setSortOrder(member.sortOrder() != null ? member.sortOrder() : order);
                show.getCast().add(cast);
                order++;
            }
        }
    }

    /**
     * Spazi normalizzati (bordi e doppi interni tolti) e grafia riusata se il ruolo esiste
     * già a meno di maiuscole/minuscole: prima tra i ruoli già incontrati in questo stesso
     * salvataggio (registrati in {@code canonicalByLowerCase} mano a mano), poi tra quelli
     * di altri spettacoli. Se non esiste ancora, la grafia data diventa quella canonica per
     * il resto di questo salvataggio.
     */
    private String normalizeRoleName(String rawRoleName, Map<String, String> canonicalByLowerCase) {
        String normalized = rawRoleName.trim().replaceAll("\\s+", " ");
        String key = normalized.toLowerCase(Locale.ITALIAN);
        return canonicalByLowerCase.computeIfAbsent(key, k -> normalized);
    }

    /** Per ogni ruolo a meno di maiuscole/minuscole, la grafia esatta più frequente tra quelle date. */
    private static Map<String, String> mostFrequentSpellingByLowerCase(List<String> roleNames) {
        Map<String, Map<String, Integer>> countsByLowerCase = new LinkedHashMap<>();
        for (String roleName : roleNames) {
            countsByLowerCase.computeIfAbsent(roleName.toLowerCase(Locale.ITALIAN), k -> new LinkedHashMap<>())
                    .merge(roleName, 1, Integer::sum);
        }
        Map<String, String> mostFrequent = new HashMap<>();
        countsByLowerCase.forEach((key, counts) -> mostFrequent.put(key,
                counts.entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow().getKey()));
        return mostFrequent;
    }
}
