package it.abc.musical.repositories;

import it.abc.musical.dto.UserDtos.UserAdminCounts;
import it.abc.musical.dto.UserDtos.UserAdminFilter;
import it.abc.musical.entities.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Ricerca utenti per il pannello admin: SQL nativo, per usare gli indici trigram di V012. */
public interface UserRepositoryCustom {

    /** Utenti non cancellati che passano il filtro, nell'ordine fisso della lista admin. */
    Page<User> searchForAdmin(UserAdminFilter filter, Pageable pageable);

    /** Conteggi su tutti gli utenti non cancellati, senza filtri. */
    UserAdminCounts countForAdmin();
}
