package it.abc.musical.repositories;

import it.abc.musical.entities.Folder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FolderRepository extends JpaRepository<Folder, Long> {

    List<Folder> findByDeletedAtIsNullOrderByNameAsc();

    Optional<Folder> findByIdAndDeletedAtIsNull(Long id);

    List<Folder> findByParentFolderIdAndDeletedAtIsNull(Long parentFolderId);

    /**
     * Cartella attiva con quel nome (senza maiuscole) nello stesso padre: la stessa chiave
     * dell'indice unico ux_folders_name_in_parent, radice = 0.
     */
    @Query("""
            select f from Folder f left join f.parentFolder p
            where f.deletedAt is null
              and coalesce(p.id, 0) = :parentKey
              and lower(f.name) = lower(:name)
            """)
    Optional<Folder> findActiveByNameInParent(@Param("parentKey") long parentKey, @Param("name") String name);
}
