package it.abc.musical.repositories;

import it.abc.musical.dto.UserDtos.UserAdminCounts;
import it.abc.musical.dto.UserDtos.UserAdminFilter;
import it.abc.musical.entities.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class UserRepositoryImpl implements UserRepositoryCustom {

    // Espressioni IDENTICHE a quelle degli indici di V012 (coalesce compreso), e sempre
    // deleted_at IS NULL (indici parziali): con un'espressione diversa Postgres fa Seq Scan.
    private static final String NAME_FOLD =
            "public.abc_fold(coalesce(u.first_name, '') || ' ' || coalesce(u.last_name, ''))";
    private static final String EMAIL_FOLD = "public.abc_fold(u.email)";

    private static final String ORDER = " ORDER BY r.sort_order, public.abc_fold(u.first_name),"
            + " public.abc_fold(u.last_name), public.abc_fold(u.email), u.id";

    @PersistenceContext
    private EntityManager em;

    @Override
    public Page<User> searchForAdmin(UserAdminFilter filter, Pageable pageable) {
        Map<String, Object> params = new LinkedHashMap<>();
        String where = where(filter, params);

        Query select = em.createNativeQuery("SELECT u.* FROM users u JOIN roles r ON r.id = u.role_id"
                + where + ORDER + " LIMIT :limit OFFSET :offset", User.class);
        params.forEach(select::setParameter);
        select.setParameter("limit", pageable.getPageSize());
        select.setParameter("offset", pageable.getOffset());
        @SuppressWarnings("unchecked")
        List<User> users = select.getResultList();

        Query count = em.createNativeQuery("SELECT count(*) FROM users u" + where);
        params.forEach(count::setParameter);
        long total = ((Number) count.getSingleResult()).longValue();

        return new PageImpl<>(users, pageable, total);
    }

    @Override
    public UserAdminCounts countForAdmin() {
        Object[] row = (Object[]) em.createNativeQuery("SELECT count(*), count(*) FILTER (WHERE active)"
                + " FROM users WHERE deleted_at IS NULL").getSingleResult();
        long total = ((Number) row[0]).longValue();
        long active = ((Number) row[1]).longValue();
        return new UserAdminCounts(total, active, total - active);
    }

    private static String where(UserAdminFilter f, Map<String, Object> params) {
        List<String> conditions = new ArrayList<>();
        conditions.add("u.deleted_at IS NULL");
        if (f.name() != null) {
            conditions.add(NAME_FOLD + " LIKE '%' || public.abc_fold(:name) || '%'");
            params.put("name", escapeLike(f.name()));
        }
        if (f.email() != null) {
            conditions.add(EMAIL_FOLD + " LIKE '%' || public.abc_fold(:email) || '%'");
            params.put("email", escapeLike(f.email()));
        }
        if (f.roleId() != null) {
            conditions.add("u.role_id = :roleId");
            params.put("roleId", f.roleId());
        }
        if (f.active() != null) {
            conditions.add("u.active = :active");
            params.put("active", f.active());
        }
        if (f.neverLoggedIn()) {
            conditions.add("u.last_login_at IS NULL");
        }
        if (f.lastLoginFrom() != null) {
            conditions.add("u.last_login_at >= :lastLoginFrom");
            params.put("lastLoginFrom", f.lastLoginFrom());
        }
        if (f.lastLoginBefore() != null) {
            conditions.add("u.last_login_at < :lastLoginBefore");
            params.put("lastLoginBefore", f.lastLoginBefore());
        }
        return " WHERE " + String.join(" AND ", conditions);
    }

    /** % e _ del testo dell'utente valgono come caratteri, non come jolly (escape di LIKE: \). */
    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
