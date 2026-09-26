package it.abc.musical.controllers.api;

import it.abc.musical.dto.AdminShowDtos.AdminShowListDto;
import it.abc.musical.dto.AdminShowDtos.GalleryImageAttachRequest;
import it.abc.musical.dto.AdminShowDtos.ShowUpsertRequest;
import it.abc.musical.dto.ShowDtos.ShowDetailDto;
import it.abc.musical.entities.Show;
import it.abc.musical.entities.ShowImage;
import it.abc.musical.services.ShowService;
import it.abc.musical.util.AuthUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/shows")
@RequiredArgsConstructor
public class AdminShowsController {

    private final ShowService showService;

    @GetMapping
    public Page<AdminShowListDto> list(@RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        return showService.listAdmin(PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "productionYear", "id")));
    }

    @GetMapping("/{id}")
    public ShowDetailDto detail(@PathVariable Long id) {
        return showService.getAdminDetail(id);
    }

    @PostMapping
    public ResponseEntity<Map<String, Long>> create(
            @Valid @RequestBody ShowUpsertRequest request,
            Authentication authentication) {
        Show show = showService.create(request, AuthUtil.userId(authentication));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", show.getId()));
    }

    @PutMapping("/{id}")
    public Map<String, Long> update(
            @PathVariable Long id,
            @Valid @RequestBody ShowUpsertRequest request,
            Authentication authentication) {
        Show show = showService.update(id, request, AuthUtil.userId(authentication));
        return Map.of("id", show.getId());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        showService.softDelete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/cast-roles")
    public List<String> castRoles(@PathVariable Long id) {
        return showService.castRoles(id);
    }

    @PostMapping("/{id}/images")
    public ResponseEntity<Map<String, Long>> addImage(
            @PathVariable Long id,
            @Valid @RequestBody GalleryImageAttachRequest request) {
        ShowImage showImage = showService.addGalleryImage(id, request.imagePath(), request.caption());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("imageId", showImage.getId()));
    }
}
