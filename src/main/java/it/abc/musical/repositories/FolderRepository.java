package it.abc.musical.repositories;

import it.abc.musical.entities.Folder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FolderRepository extends JpaRepository<Folder, Long> {

    List<Folder> findByDeletedAtIsNullOrderByNameAsc();

    Optional<Folder> findByIdAndDeletedAtIsNull(Long id);

    List<Folder> findByParentFolderIdAndDeletedAtIsNull(Long parentFolderId);
}
