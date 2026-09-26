package it.abc.musical.security;

import it.abc.musical.entities.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Getter
public class CustomUserDetails implements UserDetails {

    private static final long serialVersionUID = 1L;

    // UserDetails estende Serializable, ma User e' un'entita' JPA non Serializable.
    // Qui e' accettabile: la sessione (SessionCreationPolicy.IF_REQUIRED) e' tenuta in
    // memoria dal Tomcat embedded, che di default non la persiste su disco, quindi questo
    // oggetto non viene mai serializzato davvero. Rendere il campo transient lo
    // svuoterebbe dopo una deserializzazione: sarebbe un cambio di comportamento.
    @SuppressWarnings("serial")
    private final User user;

    public CustomUserDetails(User user) {
        this.user = user;
    }

    public Long getId() {
        return user.getId();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(Roles.authority(user.getRole().getName())));
    }

    @Override
    public String getPassword() {
        return user.getPassword();
    }

    @Override
    public String getUsername() {
        return user.getEmail();
    }

    @Override
    public boolean isEnabled() {
        return user.isActive() && user.isVerified();
    }

    @Override
    public boolean isAccountNonLocked() {
        return user.isActive();
    }
}
