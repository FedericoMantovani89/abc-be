package it.abc.musical.config;

import it.abc.musical.enums.UploadTargetType;
import it.abc.musical.security.CustomAuthenticationFailureHandler;
import it.abc.musical.security.CustomAuthenticationSuccessHandler;
import it.abc.musical.security.CustomOAuth2UserService;
import it.abc.musical.security.CustomOidcUserService;
import it.abc.musical.security.CustomUserDetailsService;
import it.abc.musical.security.OAuth2AuthenticationSuccessHandler;
import it.abc.musical.security.RateLimitingFilter;
import it.abc.musical.security.SocialProviderGuardFilter;
import it.abc.musical.security.Roles;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.security.web.util.matcher.RegexRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
// I permessi stanno tutti nei requestMatchers: per usare @PreAuthorize va rimessa @EnableMethodSecurity.
@RequiredArgsConstructor
public class SecurityConfig {

    private final RateLimitingFilter rateLimitingFilter;
    private final CustomAuthenticationSuccessHandler customAuthenticationSuccessHandler;
    private final CustomAuthenticationFailureHandler customAuthenticationFailureHandler;
    private final OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;
    private final CustomOAuth2UserService customOAuth2UserService;
    private final CustomOidcUserService customOidcUserService;
    private final CustomUserDetailsService customUserDetailsService;
    private final JwtAuthenticationConverter jwtAuthenticationConverter;
    private final SocialLoginConfig.SocialProviders socialProviders;

    private final AppProperties props;

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, SessionRegistry sessionRegistry) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .addFilterBefore(rateLimitingFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(new SocialProviderGuardFilter(socialProviders, props.getFrontendUrl()), OAuth2AuthorizationRequestRedirectFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login/oauth2/code/*", "/oauth2/authorization/*").permitAll()
                .requestMatchers(publicUploadPatterns()).permitAll()
                .requestMatchers("/error").permitAll()
                .requestMatchers("/api/public/**").permitAll()
                .requestMatchers("/api/auth/token").authenticated()
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers("/actuator/**").hasAnyRole(Roles.array(Roles.ADMINS))
                .requestMatchers("/api/admin/users/**").hasAnyRole(Roles.array(Roles.ADMINS))
                .requestMatchers("/api/admin/**").hasAnyRole(Roles.array(Roles.STAFF_AND_ABOVE))
                .requestMatchers("/api/account/**").authenticated()
                .requestMatchers("/api/member/**").hasAnyRole(Roles.array(Roles.MEMBERS_AND_ABOVE))
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginProcessingUrl("/api/auth/login")
                .successHandler(customAuthenticationSuccessHandler)
                .failureHandler(customAuthenticationFailureHandler)
                .permitAll()
            )
            .oauth2Login(oauth2 -> oauth2
                .successHandler(oAuth2AuthenticationSuccessHandler)
                .userInfoEndpoint(u -> u
                    .userService(customOAuth2UserService)   // Facebook
                    .oidcUserService(customOidcUserService) // Google (OIDC)
                )
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt ->
                jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
            .logout(logout -> logout
                .logoutUrl("/api/auth/logout")
                .logoutSuccessUrl(props.getFrontendUrl() + "/login")
                .deleteCookies("JSESSIONID", "remember-me")
                .invalidateHttpSession(true)
                .permitAll()
            )
            .rememberMe(rm -> rm
                .tokenValiditySeconds(90 * 24 * 60 * 60) // 90 giorni
                .rememberMeParameter("rememberMe")
                .userDetailsService(customUserDetailsService)
                .key(props.getSecurity().getRememberMeKey())
            )
            .exceptionHandling(ex -> ex
                .defaultAuthenticationEntryPointFor(
                    new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                    new RegexRequestMatcher("^/api/.*", null)
                )
            )
            .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))
            .sessionManagement(session -> session
                .sessionFixation(fixation -> fixation.migrateSession())
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                .sessionConcurrency(concurrency -> concurrency
                    .maximumSessions(-1)
                    .maxSessionsPreventsLogin(false)
                    .sessionRegistry(sessionRegistry)
                )
            );
        return http.build();
    }

    /**
     * Cartelle di upload servite come risorse statiche pubbliche (stessa fonte di WebMvcConfig,
     * {@code UploadTargetType.publiclyServed()}): scritte una volta sola invece che a mano qui e
     * la' quando ne arriva una nuova. {@code media/} (l'archivio documenti) non e' pubblica: non
     * compare, resta raggiungibile solo da MemberFileController che controlla i permessi.
     */
    private static String[] publicUploadPatterns() {
        return Arrays.stream(UploadTargetType.values())
                .filter(UploadTargetType::publiclyServed)
                .map(target -> "/" + target.subdir() + "/**")
                .toArray(String[]::new);
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /**
     * Il backend risponde solo al sito: {@code app.frontend-url} e nient'altro. Altre origini
     * (es. http://localhost:3000 in sviluppo) si aggiungono solo con {@code app.cors.extra-origins},
     * vuota di default.
     */
    private List<String> allowedOrigins() {
        List<String> origins = new ArrayList<>();
        origins.add(stripTrailingSlash(props.getFrontendUrl()));
        props.getCors().getExtraOrigins().stream()
                .map(SecurityConfig::stripTrailingSlash)
                .filter(o -> !o.isBlank() && !origins.contains(o))
                .forEach(origins::add);
        return origins;
    }

    private static String stripTrailingSlash(String url) {
        String trimmed = url.strip();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    @Bean
    HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }
}
