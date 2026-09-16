package it.abc.musical.repositories;

import it.abc.musical.entities.Document;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, Long> {

    List<Document> findByDeletedAtIsNullOrderByTitleAsc();

    Optional<Document> findByIdAndDeletedAtIsNull(Long id);

    Optional<Document> findByUuidAndDeletedAtIsNull(UUID uuid);

    List<Document> findByFolderIdAndDeletedAtIsNull(Long folderId);
}
