package it.abc.musical.controllers.api;

import it.abc.musical.dto.SceneDtos.SceneDto;
import it.abc.musical.dto.SceneDtos.SceneRolesRequest;
import it.abc.musical.dto.SceneDtos.SceneUpsertRequest;
import it.abc.musical.services.SceneService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/shows/{showId}/scenes")
@RequiredArgsConstructor
public class AdminShowScenesController {

    private final SceneService sceneService;

    @GetMapping
    public List<SceneDto> list(@PathVariable Long showId) {
        return sceneService.list(showId);
    }

    @PostMapping
    public ResponseEntity<SceneDto> create(@PathVariable Long showId,
                                           @Valid @RequestBody SceneUpsertRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(sceneService.create(showId, request));
    }

    @PutMapping("/{sceneId}")
    public SceneDto update(@PathVariable Long showId, @PathVariable Long sceneId,
                           @Valid @RequestBody SceneUpsertRequest request) {
        return sceneService.update(showId, sceneId, request);
    }

    @DeleteMapping("/{sceneId}")
    public ResponseEntity<Void> delete(@PathVariable Long showId, @PathVariable Long sceneId) {
        sceneService.delete(showId, sceneId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{sceneId}/roles")
    public SceneDto setRoles(@PathVariable Long showId, @PathVariable Long sceneId,
                             @Valid @RequestBody SceneRolesRequest request) {
        return sceneService.setRoles(showId, sceneId, request.roles());
    }
}
