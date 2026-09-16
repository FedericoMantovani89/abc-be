package it.abc.musical.repositories;

import it.abc.musical.entities.Token;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface TokenRepository extends JpaRepository<Token, Long> {

    Optional<Token> findByTokenValueAndTokenType(String tokenValue, String tokenType);

    @Modifying
    @Query("delete from Token t where t.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") LocalDateTime cutoff);

    @Modifying
    @Query("delete from Token t where t.user.id = :userId and t.tokenType = :tokenType")
    int deleteAllForUserAndType(@Param("userId") Long userId, @Param("tokenType") String tokenType);
}
