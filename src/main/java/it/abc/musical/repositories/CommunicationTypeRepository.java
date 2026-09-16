package it.abc.musical.repositories;

import it.abc.musical.entities.CommunicationType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommunicationTypeRepository extends JpaRepository<CommunicationType, Long> {

    List<CommunicationType> findByActiveTrueOrderByNameAsc();
}
