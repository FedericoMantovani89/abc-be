package it.abc.musical.services;

import it.abc.musical.dto.AuthDtos.RegisterRequest;
import it.abc.musical.entities.Token;
import it.abc.musical.entities.User;
import it.abc.musical.exceptions.BadRequestException;
import it.abc.musical.exceptions.ConflictException;
import it.abc.musical.repositories.UserRepository;
import it.abc.musical.security.NewUserRole;
import it.abc.musical.util.EmailAddresses;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final NewUserRole newUserRole;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final EmailService emailService;

    @Transactional
    public User register(RegisterRequest request) {
        String email = EmailAddresses.normalize(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("Email già registrata");
        }
        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        user.setRole(newUserRole.get());
        user.setActive(true);
        user.setVerified(false);
        user = userRepository.save(user);

        Token token = tokenService.createToken(user, Token.TYPE_EMAIL_VERIFICATION);
        emailService.sendVerificationEmail(user.getEmail(), user.getFirstName(), token.getTokenValue());
        return user;
    }

    @Transactional
    public void verifyEmail(String tokenValue) {
        Token token = tokenService.consumeToken(tokenValue, Token.TYPE_EMAIL_VERIFICATION);
        User user = token.getUser();
        user.setVerified(true);
        userRepository.save(user);
    }

    @Transactional
    public void resendVerification(String email) {
        userRepository.findByEmailIgnoreCase(EmailAddresses.normalize(email))
                .filter(u -> !u.isVerified())
                .ifPresent(user -> {
                    Token token = tokenService.createToken(user, Token.TYPE_EMAIL_VERIFICATION);
                    emailService.sendVerificationEmail(user.getEmail(), user.getFirstName(), token.getTokenValue());
                });
        // Risposta identica anche se l'email non esiste: nessun information leak.
    }

    @Transactional
    public void forgotPassword(String email) {
        userRepository.findByEmailIgnoreCase(EmailAddresses.normalize(email))
                .filter(User::isActive)
                .ifPresent(user -> {
                    Token token = tokenService.createToken(user, Token.TYPE_PASSWORD_RESET);
                    emailService.sendPasswordResetEmail(user.getEmail(), user.getFirstName(), token.getTokenValue());
                });
    }

    @Transactional
    public void resetPassword(String tokenValue, String newPassword) {
        Token token = tokenService.consumeToken(tokenValue, Token.TYPE_PASSWORD_RESET);
        User user = token.getUser();
        if (!user.isActive()) {
            throw new BadRequestException("Account disattivato");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        // Un reset riuscito prova il possesso dell'email: l'account risulta verificato.
        user.setVerified(true);
        userRepository.save(user);
    }
}
