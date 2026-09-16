package it.abc.musical.repositories;

import it.abc.musical.entities.ShowImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ShowImageRepository extends JpaRepository<ShowImage, Long> {

    Optional<ShowImage> findByIdAndShowId(Long id, Long showId);
}
