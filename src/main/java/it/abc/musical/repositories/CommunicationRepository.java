package it.abc.musical.repositories;

import it.abc.musical.entities.Communication;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CommunicationRepository extends JpaRepository<Communication, Long> {

    List<Communication> findByDeletedAtIsNullOrderByPinnedDescPublishedAtDesc();

    Optional<Communication> findByIdAndDeletedAtIsNull(Long id);
}
