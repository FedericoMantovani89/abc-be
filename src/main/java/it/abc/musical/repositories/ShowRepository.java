package it.abc.musical.repositories;

import it.abc.musical.entities.Show;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ShowRepository extends JpaRepository<Show, Long> {

    List<Show> findByDeletedAtIsNullOrderByProductionYearDesc();

    Page<Show> findByDeletedAtIsNull(Pageable pageable);

    Optional<Show> findByIdAndDeletedAtIsNull(Long id);

    @Query("select distinct c.roleName from ShowCast c where c.show.id = :showId order by c.roleName")
    List<String> findDistinctCastRoleNames(@Param("showId") Long showId);
}
