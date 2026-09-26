package it.abc.musical.security;

import java.util.Set;

/**
 * Nomi dei ruoli e gruppi di ruoli usati nelle regole di accesso.
 * I ruoli esistenti restano quelli della tabella {@code roles}: qui ci sono solo i nomi
 * che il codice cita, cosi' ogni gruppo e' scritto in un posto solo.
 */
public final class Roles {

    public static final String GOD = "GOD";
    public static final String ADMIN = "ADMIN";
    public static final String STAFF = "STAFF";
    public static final String DIRECTOR = "DIRECTOR";
    public static final String TECHNICIAN = "TECHNICIAN";
    public static final String MEMBER = "MEMBER";
    /** Ruolo dei nuovi iscritti, in attesa che un admin li promuova. */
    public static final String REGISTER = "REGISTER";

    /** Gestiscono gli utenti e vedono tutto. */
    public static final Set<String> ADMINS = Set.of(ADMIN, GOD);

    /** Gestiscono i contenuti (area admin). */
    public static final Set<String> STAFF_AND_ABOVE = Set.of(STAFF, ADMIN, GOD);

    /** Entrano nell'area soci. */
    public static final Set<String> MEMBERS_AND_ABOVE = Set.of(MEMBER, TECHNICIAN, DIRECTOR, STAFF, ADMIN, GOD);

    private Roles() {
    }

    /** Nome dell'authority Spring per il ruolo: hasRole()/hasAnyRole() vogliono il prefisso ROLE_. */
    public static String authority(String role) {
        return "ROLE_" + role;
    }

    /** Il gruppo come array, per {@code hasAnyRole(...)}. */
    public static String[] array(Set<String> group) {
        return group.toArray(String[]::new);
    }
}
