package it.abc.musical.services;

import it.abc.musical.dto.SceneDtos.SceneDto;
import it.abc.musical.dto.SceneDtos.SceneUpsertRequest;
import it.abc.musical.entities.Show;
import it.abc.musical.entities.ShowScene;
import it.abc.musical.exceptions.NotFoundException;
import it.abc.musical.repositories.ShowRepository;
import it.abc.musical.repositories.ShowSceneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SceneService {

    private final ShowSceneRepository showSceneRepository;
    private final ShowRepository showRepository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<SceneDto> list(Long showId) {
        requireShow(showId);
        return showSceneRepository.findByShowIdOrderBySortOrderAscIdAsc(showId).stream()
                .map(SceneDto::from)
                .toList();
    }

    @Transactional
    public SceneDto create(Long showId, SceneUpsertRequest request) {
        Show show = requireShow(showId);
        ShowScene scene = new ShowScene();
        scene.setShow(show);
        scene.setSceneNumber(request.sceneNumber());
        scene.setTitle(request.title().trim());
        scene.setSortOrder(request.sortOrder() != null ? request.sortOrder()
                : (int) showSceneRepository.countByShowId(showId));
        scene = showSceneRepository.save(scene);
        auditLogService.record("CREATE", "ShowScene", scene.getId());
        return SceneDto.from(scene);
    }

    @Transactional
    public SceneDto update(Long showId, Long sceneId, SceneUpsertRequest request) {
        ShowScene scene = requireScene(showId, sceneId);
        scene.setSceneNumber(request.sceneNumber());
        scene.setTitle(request.title().trim());
        if (request.sortOrder() != null) {
            scene.setSortOrder(request.sortOrder());
        }
        scene = showSceneRepository.save(scene);
        auditLogService.record("UPDATE", "ShowScene", sceneId);
        return SceneDto.from(scene);
    }

    @Transactional
    public void delete(Long showId, Long sceneId) {
        ShowScene scene = requireScene(showId, sceneId);
        showSceneRepository.delete(scene);   // cascade DB su scene_cast_roles e calendar_event_scenes
        auditLogService.record("DELETE", "ShowScene", sceneId);
    }

    @Transactional
    public SceneDto setRoles(Long showId, Long sceneId, List<String> roles) {
        ShowScene scene = requireScene(showId, sceneId);
        scene.setCastRoles(new LinkedHashSet<>(roles));
        scene = showSceneRepository.save(scene);
        auditLogService.record("SET_ROLES", "ShowScene", sceneId);
        return SceneDto.from(scene);
    }

    private Show requireShow(Long showId) {
        return showRepository.findByIdAndDeletedAtIsNull(showId)
                .orElseThrow(() -> new NotFoundException("Spettacolo non trovato"));
    }

    private ShowScene requireScene(Long showId, Long sceneId) {
        return showSceneRepository.findByIdAndShowId(sceneId, showId)
                .orElseThrow(() -> new NotFoundException("Scena non trovata"));
    }
}
