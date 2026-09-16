package it.abc.musical.repositories;

import it.abc.musical.entities.ShowScene;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ShowSceneRepository extends JpaRepository<ShowScene, Long> {

    List<ShowScene> findByShowIdOrderBySortOrderAscIdAsc(Long showId);

    Optional<ShowScene> findByIdAndShowId(Long id, Long showId);

    long countByShowId(Long showId);
}
