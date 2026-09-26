package it.abc.musical.util;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RoleCsvTest {

    @Test
    void parseTrimsUppercasesAndDropsEmptiesAndDuplicates() {
        assertThat(RoleCsv.parse(" staff, ,MEMBER,,Staff ")).containsExactly("STAFF", "MEMBER");
        assertThat(RoleCsv.parse(null)).isEmpty();
        assertThat(RoleCsv.parse(" , ")).isEmpty();
    }

    @Test
    void formatGivesNullForNoRoles() {
        assertThat(RoleCsv.format(List.of("director", " STAFF "))).isEqualTo("DIRECTOR,STAFF");
        assertThat(RoleCsv.format(List.of())).isNull();
        assertThat(RoleCsv.format(Arrays.asList(" ", null))).isNull();
        assertThat(RoleCsv.format(null)).isNull();
    }

    @Test
    void normalizeMatchesTheDatabaseCheckFormat() {
        assertThat(RoleCsv.normalize(" staff ,member")).isEqualTo("STAFF,MEMBER")
                .matches("^[A-Z_]+(,[A-Z_]+)*$");
        assertThat(RoleCsv.normalize("")).isNull();
    }

    @Test
    void matchesTargetRolesUsesTheSameParsing() {
        assertThat(AuthUtil.matchesTargetRoles(" staff ", Set.of("STAFF"))).isTrue();
        assertThat(AuthUtil.matchesTargetRoles("STAFF", Set.of("MEMBER"))).isFalse();
        assertThat(AuthUtil.matchesTargetRoles(" , ", Set.of("MEMBER"))).isTrue();
        assertThat(AuthUtil.matchesTargetRoles("STAFF", Set.of("GOD"))).isTrue();
    }
}
