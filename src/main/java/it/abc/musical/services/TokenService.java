package it.abc.musical.services;

import it.abc.musical.entities.Token;
import it.abc.musical.entities.User;
import it.abc.musical.exceptions.BadRequestException;
import it.abc.musical.repositories.TokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Duration VERIFICATION_VALIDITY = Duration.ofHours(24);
    private static final Duration RESET_VALIDITY = Duration.ofHours(1);

    private final TokenRepository tokenRepository;

    @Transactional
    public Token createToken(User user, String tokenType) {
        // Un solo token attivo per tipo: i precedenti vengono eliminati.
        tokenRepository.deleteAllForUserAndType(user.getId(), tokenType);

        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);

        Token token = new Token();
        token.setUser(user);
        token.setTokenType(tokenType);
        token.setTokenValue(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
        Duration validity = Token.TYPE_EMAIL_VERIFICATION.equals(tokenType)
                ? VERIFICATION_VALIDITY : RESET_VALIDITY;
        token.setExpiresAt(LocalDateTime.now().plus(validity));
        return tokenRepository.save(token);
    }

    @Transactional
    public Token consumeToken(String tokenValue, String tokenType) {
        Token token = tokenRepository.findByTokenValueAndTokenType(tokenValue, tokenType)
                .orElseThrow(() -> new BadRequestException("Token non valido"));
        if (token.isUsed()) {
            throw new BadRequestException("Token già utilizzato");
        }
        if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Token scaduto");
        }
        token.setUsed(true);
        return tokenRepository.save(token);
    }

    /** Pulizia notturna dei token scaduti da più di un giorno. */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void purgeExpiredTokens() {
        int deleted = tokenRepository.deleteExpiredBefore(LocalDateTime.now().minusDays(1));
        log.info("Purged {} expired tokens", deleted);
    }
}
