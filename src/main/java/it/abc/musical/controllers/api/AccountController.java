package it.abc.musical.controllers.api;

import it.abc.musical.dto.AccountDtos.AccountDto;
import it.abc.musical.dto.AccountDtos.ChangePasswordRequest;
import it.abc.musical.dto.AccountDtos.DeleteAccountRequest;
import it.abc.musical.dto.AccountDtos.UpdateProfileRequest;
import it.abc.musical.services.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Area account self-service: accessibile a qualsiasi utente autenticato (REGISTER incluso). */
@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @GetMapping("/me")
    public AccountDto me(Authentication authentication) {
        return accountService.me(authentication);
    }

    @PutMapping("/profile")
    public AccountDto updateProfile(@Valid @RequestBody UpdateProfileRequest request,
                                    Authentication authentication) {
        return accountService.updateProfile(authentication, request);
    }

    @PutMapping("/password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                               Authentication authentication) {
        accountService.changePassword(authentication, request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteAccount(@RequestBody(required = false) DeleteAccountRequest request,
                                              Authentication authentication) {
        accountService.deleteAccount(authentication, request);
        return ResponseEntity.noContent().build();
    }
}
