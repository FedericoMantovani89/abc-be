package it.abc.musical.config;

import it.abc.musical.security.CustomAuthenticationFailureHandler;
import it.abc.musical.security.CustomAuthenticationSuccessHandler;
import it.abc.musical.security.CustomOAuth2UserService;
import it.abc.musical.security.CustomOidcUserService;
import it.abc.musical.security.CustomUserDetailsService;
import it.abc.musical.security.OAuth2AuthenticationSuccessHandler;
import it.abc.musical.security.RateLimitingFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.security.web.util.matcher.RegexRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
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

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Value("${security.remember-me-key}")
    private String rememberMeKey;

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, SessionRegistry sessionRegistry) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .addFilterBefore(rateLimitingFilter, UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login/oauth2/code/*", "/oauth2/authorization/*").permitAll()
                .requestMatchers("/images/**", "/posters/**", "/show_gallery/**", "/error").permitAll()
                .requestMatchers("/api/public/**").permitAll()
                .requestMatchers("/api/auth/token").authenticated()
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers("/actuator/**").hasAnyRole("ADMIN", "GOD")
                .requestMatchers("/api/admin/users/**").hasAnyRole("ADMIN", "GOD")
                .requestMatchers("/api/admin/**").hasAnyRole("ADMIN", "STAFF", "GOD")
                .requestMatchers("/api/account/**").authenticated()
                .requestMatchers("/api/member/**").hasAnyRole("MEMBER", "TECHNICIAN", "DIRECTOR", "STAFF", "ADMIN", "GOD")
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
                .logoutSuccessUrl(frontendUrl + "/login")
                .deleteCookies("JSESSIONID", "remember-me")
                .invalidateHttpSession(true)
                .permitAll()
            )
            .rememberMe(rm -> rm
                .tokenValiditySeconds(90 * 24 * 60 * 60) // 90 giorni
                .rememberMeParameter("rememberMe")
                .userDetailsService(customUserDetailsService)
                .key(rememberMeKey)
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

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of(
                "http://localhost:3000",
                frontendUrl
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
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
