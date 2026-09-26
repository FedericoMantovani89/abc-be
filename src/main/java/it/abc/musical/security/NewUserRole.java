package it.abc.musical.security;

import it.abc.musical.entities.Role;
import it.abc.musical.repositories.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Ruolo di chi si iscrive (form o Google/Facebook): REGISTER, finche' un admin
 * non lo promuove a MEMBER per sbloccare l'area riservata ai soci.
 * Componente a se': il provisioning OAuth non puo' dipendere da UserService
 * (UserService usa il PasswordEncoder di SecurityConfig, che usa il provisioning).
 */
@Component
@RequiredArgsConstructor
public class NewUserRole {

    private final RoleRepository roleRepository;

    public Role get() {
        return roleRepository.findByName(Roles.REGISTER)
                .orElseThrow(() -> new IllegalStateException("Ruolo REGISTER mancante"));
    }
}
