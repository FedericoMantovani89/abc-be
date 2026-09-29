package it.abc.musical.services;

import it.abc.musical.dto.UserDtos.UserAdminDto;
import it.abc.musical.dto.UserDtos.UserAdminFilter;
import it.abc.musical.dto.UserDtos.UserAdminListResponse;
import it.abc.musical.entities.Role;
import it.abc.musical.entities.User;
import it.abc.musical.exceptions.BadRequestException;
import it.abc.musical.exceptions.ConflictException;
import it.abc.musical.exceptions.NotFoundException;
import it.abc.musical.repositories.RoleRepository;
import it.abc.musical.repositories.UserRepository;
import it.abc.musical.security.Roles;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AuditLogService auditLogService;

    public static final int DEFAULT_PAGE_SIZE = 25;
    public static final int MAX_PAGE_SIZE = 100;

    /**
     * Lista paginata per /admin/users. Parametri vuoti = nessun filtro. Le date sono giorni
     * inclusivi nel fuso di Roma: last_login_at e' un TIMESTAMP senza fuso scritto con
     * LocalDateTime.now() da una JVM con TZ=Europe/Rome, quindi e' gia' ora di Roma e il
     * giorno "da" / "a" diventa [da 00:00, giorno dopo "a" 00:00) senza conversioni.
     */
    @Transactional(readOnly = true)
    public UserAdminListResponse list(int page, Integer size, String name, String email, String role,
                                      Boolean active, String lastLoginFrom, String lastLoginTo,
                                      Boolean neverLoggedIn) {
        int pageSize = size == null ? DEFAULT_PAGE_SIZE : size;
        if (page < 0) {
            throw new BadRequestException("utenti.pagina.negativa");
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new BadRequestException("utenti.dimensione.pagina", String.valueOf(MAX_PAGE_SIZE));
        }
        Long roleId = null;
        if (!isBlank(role)) {
            roleId = roleRepository.findByName(role)
                    .orElseThrow(() -> new BadRequestException("ruolo.non.valido", role))
                    .getId();
        }
        LocalDate from = parseDate("lastLoginFrom", lastLoginFrom);
        LocalDate to = parseDate("lastLoginTo", lastLoginTo);
        boolean never = Boolean.TRUE.equals(neverLoggedIn);
        if (never && (from != null || to != null)) {
            throw new BadRequestException("utenti.mai.entrato.con.intervallo");
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new BadRequestException("utenti.intervallo.date.invertito");
        }
        UserAdminFilter filter = new UserAdminFilter(
                blankToNull(name), blankToNull(email), roleId, active,
                from == null ? null : from.atStartOfDay(),
                to == null ? null : to.plusDays(1).atStartOfDay(),
                never);
        return UserAdminListResponse.of(
                userRepository.searchForAdmin(filter, PageRequest.of(page, pageSize)).map(UserAdminDto::from),
                userRepository.countForAdmin());
    }

    @Transactional
    public UserAdminDto setStatus(Long id, boolean active) {
        User user = requireTouchable(id);
        user.setActive(active);
        userRepository.save(user);
        auditLogService.record(active ? "ACTIVATE" : "DEACTIVATE", "User", id);
        return UserAdminDto.from(user);
    }

    @Transactional
    public UserAdminDto setRole(Long id, String roleName) {
        User user = requireTouchable(id);
        if (Roles.GOD.equalsIgnoreCase(roleName)) {
            throw new BadRequestException("utenti.ruolo.god.non.assegnabile");
        }
        Role role = roleRepository.findByName(roleName.toUpperCase())
                .orElseThrow(() -> new NotFoundException("ruolo.non.trovato", roleName));
        user.setRole(role);
        userRepository.save(user);
        auditLogService.record("SET_ROLE_" + role.getName(), "User", id);
        return UserAdminDto.from(user);
    }

    /** L'account tecnico GOD non è modificabile. */
    private User requireTouchable(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("utente.non.trovato"));
        if (Roles.GOD.equals(user.getRole().getName())) {
            throw new ConflictException("utenti.account.tecnico");
        }
        return user;
    }

    private static LocalDate parseDate(String param, String value) {
        if (isBlank(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value.strip());
        } catch (DateTimeParseException e) {
            throw new BadRequestException("utenti.data.non.valida", param);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String blankToNull(String value) {
        return isBlank(value) ? null : value.strip();
    }
}
