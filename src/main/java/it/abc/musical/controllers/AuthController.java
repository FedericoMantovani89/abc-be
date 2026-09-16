package it.abc.musical.controllers;

import it.abc.musical.dto.AuthDtos.ForgotPasswordRequest;
import it.abc.musical.dto.AuthDtos.RegisterRequest;
import it.abc.musical.dto.AuthDtos.ResendVerificationRequest;
import it.abc.musical.dto.AuthDtos.ResetPasswordRequest;
import it.abc.musical.dto.AuthDtos.TokenResponse;
import it.abc.musical.exceptions.BadRequestException;
import it.abc.musical.security.JwtTokenService;
import it.abc.musical.services.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final JwtTokenService jwtTokenService;

    @Value("${jwt.expiration}")
    private long jwtExpiration;

    @Value("${jwt.rememberme-expiration}")
    private long jwtRememberMeExpiration;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    /** Scambia la sessione autenticata (JSESSIONID) con un JWT. */
    @PostMapping("/token")
    public TokenResponse token(Authentication authentication,
                               @RequestParam(defaultValue = "false") boolean rememberMe) {
        long expiration = rememberMe ? jwtRememberMeExpiration : jwtExpiration;
        String token = jwtTokenService.generateToken(authentication, expiration);
        return new TokenResponse(token, "Bearer", expiration);
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, Boolean>> register(@Valid @RequestBody RegisterRequest request) {
        userService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("success", true));
    }

    @GetMapping("/verify")
    public RedirectView verify(@RequestParam String token) {
        try {
            userService.verifyEmail(token);
            return new RedirectView(frontendUrl + "/login?verified=true");
        } catch (BadRequestException e) {
            return new RedirectView(frontendUrl + "/login?verified=false");
        }
    }

    @PostMapping("/resend-verification")
    public Map<String, Boolean> resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        userService.resendVerification(request.email());
        return Map.of("success", true);
    }

    @PostMapping("/forgot-password")
    public Map<String, Boolean> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        userService.forgotPassword(request.email());
        return Map.of("success", true);
    }

    @PostMapping("/reset-password")
    public Map<String, Boolean> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        userService.resetPassword(request.token(), request.newPassword());
        return Map.of("success", true);
    }
}
