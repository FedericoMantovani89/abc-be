package it.abc.musical.dto;

import it.abc.musical.entities.ShowScene;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Set;

public final class SceneDtos {

    private SceneDtos() {
    }

    public record SceneDto(Long id, String sceneNumber, String title, int sortOrder,
                           Set<String> castRoles) {

        public static SceneDto from(ShowScene s) {
            return new SceneDto(s.getId(), s.getSceneNumber(), s.getTitle(), s.getSortOrder(),
                    s.getCastRoles());
        }
    }

    public record SceneUpsertRequest(String sceneNumber, @NotBlank String title, Integer sortOrder) {
    }

    public record SceneRolesRequest(@NotNull List<String> roles) {
    }
}
