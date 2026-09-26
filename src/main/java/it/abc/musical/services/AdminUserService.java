package it.abc.musical.services;

import it.abc.musical.dto.UserDtos.UserAdminDto;
import it.abc.musical.entities.Role;
import it.abc.musical.entities.User;
import it.abc.musical.exceptions.BadRequestException;
import it.abc.musical.exceptions.ConflictException;
import it.abc.musical.exceptions.NotFoundException;
import it.abc.musical.repositories.RoleRepository;
import it.abc.musical.repositories.UserRepository;
import it.abc.musical.security.Roles;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<UserAdminDto> list() {
        return userRepository.findByDeletedAtIsNullOrderByCreatedAtDesc().stream()
                .map(UserAdminDto::from)
                .toList();
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
            throw new BadRequestException("Il ruolo GOD non è assegnabile");
        }
        Role role = roleRepository.findByName(roleName.toUpperCase())
                .orElseThrow(() -> new NotFoundException("Ruolo non trovato: " + roleName));
        user.setRole(role);
        userRepository.save(user);
        auditLogService.record("SET_ROLE_" + role.getName(), "User", id);
        return UserAdminDto.from(user);
    }

    /** L'account tecnico GOD non è modificabile. */
    private User requireTouchable(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Utente non trovato"));
        if (Roles.GOD.equals(user.getRole().getName())) {
            throw new ConflictException("L'account tecnico non è modificabile");
        }
        return user;
    }
}
