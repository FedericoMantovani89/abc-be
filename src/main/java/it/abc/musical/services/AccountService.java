package it.abc.musical.services;

import it.abc.musical.dto.AccountDtos.AccountDto;
import it.abc.musical.dto.AccountDtos.ChangePasswordRequest;
import it.abc.musical.dto.AccountDtos.DeleteAccountRequest;
import it.abc.musical.dto.AccountDtos.UpdateProfileRequest;
import it.abc.musical.entities.User;
import it.abc.musical.exceptions.BadRequestException;
import it.abc.musical.exceptions.NotFoundException;
import it.abc.musical.repositories.UserRepository;
import it.abc.musical.util.AuthUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** Gestione self-service dell'account dell'utente autenticato (qualsiasi ruolo). */
@Service
@RequiredArgsConstructor
public class AccountService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public AccountDto me(Authentication authentication) {
        return AccountDto.from(currentUser(authentication));
    }

    @Transactional
    public AccountDto updateProfile(Authentication authentication, UpdateProfileRequest request) {
        User user = currentUser(authentication);
        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        userRepository.save(user);
        return AccountDto.from(user);
    }

    @Transactional
    public void changePassword(Authentication authentication, ChangePasswordRequest request) {
        User user = currentUser(authentication);
        // Se ha già una password locale deve dimostrare di conoscerla; gli utenti
        // OAuth-only la impostano per la prima volta senza password attuale.
        if (user.getPassword() != null) {
            if (request.currentPassword() == null
                    || !passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
                throw new BadRequestException("Password attuale non corretta");
            }
        }
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        auditLogService.record("CHANGE_PASSWORD", "User", user.getId());
    }

    /**
     * Cancellazione logica: la riga resta come tombstone per le FK, ma i dati
     * personali vengono anonimizzati e l'email originale liberata per la reiscrizione.
     */
    @Transactional
    public void deleteAccount(Authentication authentication, DeleteAccountRequest request) {
        User user = currentUser(authentication);
        if (user.getPassword() != null) {
            String current = request != null ? request.currentPassword() : null;
            if (current == null || !passwordEncoder.matches(current, user.getPassword())) {
                throw new BadRequestException("Password attuale non corretta");
            }
        }
        Long id = user.getId();
        user.setDeletedAt(LocalDateTime.now());
        user.setActive(false);
        user.setEmail("deleted+" + id + "@deleted.invalid");
        user.setPassword(null);
        user.setFirstName(null);
        user.setLastName(null);
        user.setOauthProvider(null);
        user.setOauthId(null);
        user.setProfilePictureUrl(null);
        userRepository.save(user);
        auditLogService.record("DELETE_ACCOUNT", "User", id);
    }

    private User currentUser(Authentication authentication) {
        Long id = AuthUtil.userId(authentication);
        if (id == null) {
            throw new BadRequestException("Utente non identificabile");
        }
        return userRepository.findById(id)
                .filter(u -> u.getDeletedAt() == null)
                .orElseThrow(() -> new NotFoundException("Utente non trovato"));
    }
}
