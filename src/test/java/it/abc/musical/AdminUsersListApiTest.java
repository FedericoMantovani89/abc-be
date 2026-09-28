package it.abc.musical;

import com.jayway.jsonpath.JsonPath;
import it.abc.musical.entities.User;
import it.abc.musical.repositories.RoleRepository;
import it.abc.musical.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/admin/users: paginazione, filtri e ordine lato server. Il database e' condiviso con
 * le altre classi di test, quindi ogni test crea utenti con un marcatore proprio nell'email e
 * filtra su quello.
 */
class AdminUsersListApiTest extends IntegrationTestBase {

    @Autowired
    UserRepository userRepository;

    @Autowired
    RoleRepository roleRepository;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void responseHasPagedModelShapeWithCountsAndDefaultSize25() throws Exception {
        list(get("/api/admin/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page.size").value(25))
                .andExpect(jsonPath("$.page.number").value(0))
                .andExpect(jsonPath("$.page.totalElements").isNumber())
                .andExpect(jsonPath("$.page.totalPages").isNumber())
                .andExpect(jsonPath("$.counts.total").isNumber())
                .andExpect(jsonPath("$.counts.active").isNumber())
                .andExpect(jsonPath("$.counts.inactive").isNumber())
                .andExpect(jsonPath("$.totalElements").doesNotExist())
                .andExpect(jsonPath("$.pageable").doesNotExist());
    }

    @Test
    void sixtyUsersMakeThreePages() throws Exception {
        String marker = marker();
        for (int i = 0; i < 60; i++) {
            user(marker, "u%02d".formatted(i), "Nome", "Cognome%02d".formatted(i), "MEMBER", true, null);
        }
        list(get("/api/admin/users").param("email", marker))
                .andExpect(jsonPath("$.content.length()").value(25))
                .andExpect(jsonPath("$.page.totalElements").value(60))
                .andExpect(jsonPath("$.page.totalPages").value(3))
                .andExpect(jsonPath("$.content[0].lastName").value("Cognome00"));
        list(get("/api/admin/users").param("email", marker).param("page", "2"))
                .andExpect(jsonPath("$.content.length()").value(10))
                .andExpect(jsonPath("$.page.number").value(2))
                .andExpect(jsonPath("$.content[0].lastName").value("Cognome50"))
                .andExpect(jsonPath("$.content[9].lastName").value("Cognome59"));
        list(get("/api/admin/users").param("email", marker).param("size", "100"))
                .andExpect(jsonPath("$.content.length()").value(60))
                .andExpect(jsonPath("$.page.totalPages").value(1));
    }

    @Test
    void orderIsRoleThenFirstNameThenLastNameThenEmailIgnoringAccentsAndCase() throws Exception {
        String marker = marker();
        user(marker, "e", "zoe", "Bianchi", "MEMBER", true, null);
        user(marker, "d", "Élia", "Verdi", "MEMBER", true, null);
        user(marker, "c", "Elia", "Rossi", "MEMBER", true, null);
        user(marker, "b", "Zeno", "Neri", "ADMIN", true, null);
        user(marker, "a", "Carla", "Bruni", "REGISTER", true, null);
        user(marker, "f", "Anna", "Gialli", "STAFF", true, null);
        user(marker, "h", "elia", "rossi", "MEMBER", true, null);
        user(marker, "g", "Elia", "Rossi", "MEMBER", true, null);

        assertThat(emails(list(get("/api/admin/users").param("email", marker))))
                .containsExactly(
                        email(marker, "b"),  // ADMIN
                        email(marker, "f"),  // STAFF
                        email(marker, "c"),  // MEMBER elia rossi, email c < g < h
                        email(marker, "g"),
                        email(marker, "h"),
                        email(marker, "d"),  // elia verdi
                        email(marker, "e"),  // zoe
                        email(marker, "a")); // REGISTER
    }

    @Test
    void nameFilterIgnoresAccentsAndCaseAndSpansFirstAndLastName() throws Exception {
        String marker = marker();
        user(marker, "rossi", "Mario", "Rossi", "MEMBER", true, null);
        user(marker, "rossi2", "Lucia", "ROSSÌ", "MEMBER", true, null);
        user(marker, "verdi", "Mario", "Verdi", "MEMBER", true, null);
        user(marker, "solo", null, "Rossini", "MEMBER", true, null);

        assertThat(emails(list(get("/api/admin/users").param("email", marker).param("name", "rossì"))))
                .containsExactlyInAnyOrder(email(marker, "rossi"), email(marker, "rossi2"), email(marker, "solo"));
        assertThat(emails(list(get("/api/admin/users").param("email", marker).param("name", "MARIO ROSS"))))
                .containsExactly(email(marker, "rossi"));
        assertThat(emails(list(get("/api/admin/users").param("email", marker).param("name", "   "))))
                .hasSize(4);
    }

    @Test
    void emailFilterIgnoresCaseAndAccents() throws Exception {
        String marker = marker();
        user(marker, "Direzione", "A", "A", "MEMBER", true, null);
        user(marker, "altro", "B", "B", "MEMBER", true, null);

        assertThat(emails(list(get("/api/admin/users").param("email", marker.toUpperCase() + "-DIREZIÒNE"))))
                .containsExactly(email(marker, "Direzione"));
    }

    @Test
    void percentAndUnderscoreAreText() throws Exception {
        String marker = marker();
        user(marker, "a_b", "Sconto", "100%", "MEMBER", true, null);
        user(marker, "axb", "Sconto", "1000", "MEMBER", true, null);

        assertThat(emails(list(get("/api/admin/users").param("email", marker + "-a_b"))))
                .containsExactly(email(marker, "a_b"));
        assertThat(emails(list(get("/api/admin/users").param("email", marker).param("name", "100%"))))
                .containsExactly(email(marker, "a_b"));
        assertThat(emails(list(get("/api/admin/users").param("email", marker).param("name", "%"))))
                .containsExactly(email(marker, "a_b"));
    }

    @Test
    void roleAndActiveFilters() throws Exception {
        String marker = marker();
        user(marker, "staff-on", "A", "A", "STAFF", true, null);
        user(marker, "staff-off", "B", "B", "STAFF", false, null);
        user(marker, "member-off", "C", "C", "MEMBER", false, null);

        assertThat(emails(list(get("/api/admin/users").param("email", marker).param("role", "STAFF"))))
                .containsExactly(email(marker, "staff-on"), email(marker, "staff-off"));
        assertThat(emails(list(get("/api/admin/users").param("email", marker).param("active", "false"))))
                .containsExactly(email(marker, "staff-off"), email(marker, "member-off"));
        assertThat(emails(list(get("/api/admin/users").param("email", marker)
                .param("role", "STAFF").param("active", "true"))))
                .containsExactly(email(marker, "staff-on"));
        assertThat(emails(list(get("/api/admin/users").param("email", marker)
                .param("role", "").param("active", ""))))
                .hasSize(3);
    }

    @Test
    void lastLoginDatesAreInclusiveDaysAndNeverLoggedIn() throws Exception {
        String marker = marker();
        user(marker, "before", "A", "A", "MEMBER", true, LocalDateTime.of(2026, 3, 10, 23, 59, 59));
        user(marker, "first", "B", "B", "MEMBER", true, LocalDateTime.of(2026, 3, 11, 0, 0));
        user(marker, "last", "C", "C", "MEMBER", true, LocalDateTime.of(2026, 3, 12, 23, 59, 59, 999_000_000));
        user(marker, "after", "D", "D", "MEMBER", true, LocalDateTime.of(2026, 3, 13, 0, 0));
        user(marker, "never", "E", "E", "MEMBER", true, null);

        assertThat(emails(list(get("/api/admin/users").param("email", marker)
                .param("lastLoginFrom", "2026-03-11").param("lastLoginTo", "2026-03-12"))))
                .containsExactly(email(marker, "first"), email(marker, "last"));
        assertThat(emails(list(get("/api/admin/users").param("email", marker).param("lastLoginFrom", "2026-03-13"))))
                .containsExactly(email(marker, "after"));
        assertThat(emails(list(get("/api/admin/users").param("email", marker).param("lastLoginTo", "2026-03-10"))))
                .containsExactly(email(marker, "before"));
        assertThat(emails(list(get("/api/admin/users").param("email", marker).param("lastLoginFrom", "2026-03-12")
                .param("lastLoginTo", "2026-03-12"))))
                .containsExactly(email(marker, "last"));
        assertThat(emails(list(get("/api/admin/users").param("email", marker).param("neverLoggedIn", "true"))))
                .containsExactly(email(marker, "never"));
        assertThat(emails(list(get("/api/admin/users").param("email", marker).param("neverLoggedIn", "false"))))
                .hasSize(5);
    }

    @Test
    void combinedFilters() throws Exception {
        String marker = marker();
        user(marker, "hit", "Giulia", "Bèrti", "DIRECTOR", true, LocalDateTime.of(2026, 5, 2, 12, 0));
        user(marker, "wrong-role", "Giulia", "Berti", "MEMBER", true, LocalDateTime.of(2026, 5, 2, 12, 0));
        user(marker, "inactive", "Giulia", "Berti", "DIRECTOR", false, LocalDateTime.of(2026, 5, 2, 12, 0));
        user(marker, "old", "Giulia", "Berti", "DIRECTOR", true, LocalDateTime.of(2026, 4, 2, 12, 0));
        user(marker, "other", "Paolo", "Berti", "DIRECTOR", true, LocalDateTime.of(2026, 5, 2, 12, 0));

        assertThat(emails(list(get("/api/admin/users").param("email", marker).param("name", "giulia berti")
                .param("role", "DIRECTOR").param("active", "true")
                .param("lastLoginFrom", "2026-05-01").param("lastLoginTo", "2026-05-31"))))
                .containsExactly(email(marker, "hit"));
    }

    @Test
    void countsIgnoreFiltersAndDeletedUsers() throws Exception {
        String marker = marker();
        user(marker, "on", "A", "A", "MEMBER", true, null);
        User deleted = user(marker, "deleted", "B", "B", "MEMBER", true, null);
        deleted.setDeletedAt(LocalDateTime.now());
        userRepository.save(deleted);

        Map<String, Object> expected = jdbc.queryForMap("SELECT count(*) AS total,"
                + " count(*) FILTER (WHERE active) AS active FROM users WHERE deleted_at IS NULL");
        long total = ((Number) expected.get("total")).longValue();
        long active = ((Number) expected.get("active")).longValue();

        list(get("/api/admin/users").param("email", marker).param("active", "false"))
                .andExpect(jsonPath("$.page.totalElements").value(0))
                .andExpect(jsonPath("$.counts.total").value(total))
                .andExpect(jsonPath("$.counts.active").value(active))
                .andExpect(jsonPath("$.counts.inactive").value(total - active));
        assertThat(emails(list(get("/api/admin/users").param("email", marker))))
                .containsExactly(email(marker, "on"));
    }

    @Test
    void invalidParametersAre400() throws Exception {
        bad(get("/api/admin/users").param("size", "101"), "dimensione");
        bad(get("/api/admin/users").param("size", "0"), "dimensione");
        bad(get("/api/admin/users").param("page", "-1"), "pagina");
        bad(get("/api/admin/users").param("role", "NESSUNO"), "Ruolo non valido");
        bad(get("/api/admin/users").param("role", "admin"), "Ruolo non valido");
        bad(get("/api/admin/users").param("lastLoginFrom", "11/03/2026"), "lastLoginFrom");
        bad(get("/api/admin/users").param("lastLoginTo", "2026-02-30"), "lastLoginTo");
        bad(get("/api/admin/users").param("neverLoggedIn", "true").param("lastLoginFrom", "2026-03-11"), "Mai entrato");
        bad(get("/api/admin/users").param("neverLoggedIn", "true").param("lastLoginTo", "2026-03-11"), "Mai entrato");
        bad(get("/api/admin/users").param("lastLoginFrom", "2026-03-12").param("lastLoginTo", "2026-03-11"), "dopo");
        bad(get("/api/admin/users").param("active", "forse"), "active");
        bad(get("/api/admin/users").param("size", "tanti"), "size");
    }

    private ResultActions list(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.with(asRole("ADMIN"))).andExpect(status().isOk());
    }

    private void bad(MockHttpServletRequestBuilder request, String messagePart) throws Exception {
        mockMvc.perform(request.with(asRole("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString(messagePart)));
    }

    private static List<String> emails(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.content[*].email");
    }

    private static String marker() {
        return "lst" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static String email(String marker, String local) {
        return (marker + "-" + local + "@test.abc.it").toLowerCase();
    }

    private User user(String marker, String local, String firstName, String lastName, String role,
                      boolean active, LocalDateTime lastLoginAt) {
        User user = new User();
        user.setEmail(email(marker, local));
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setActive(active);
        user.setLastLoginAt(lastLoginAt);
        user.setRole(roleRepository.findByName(role).orElseThrow());
        return userRepository.save(user);
    }
}
