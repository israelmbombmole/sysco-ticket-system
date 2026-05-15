package com.sysco.web.config;

import com.sysco.web.security.FileShareManagementAccessFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfiguration {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Authentication uses {@link com.sysco.web.security.SyscoUserDetailsService} reading {@code users}
     * + {@code user_permissions} (same model as the JavaFX client — bcrypt hashes).
     */
    @Bean
    public FileShareManagementAccessFilter fileShareManagementAccessFilter() {
        return new FileShareManagementAccessFilter();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            FileShareManagementAccessFilter fileShareManagementAccessFilter,
            com.sysco.web.security.SyscoAuthenticationSuccessHandler successHandler,
            com.sysco.web.security.SyscoAuthenticationFailureHandler failureHandler,
            com.sysco.web.security.SyscoLogoutSuccessHandler logoutSuccessHandler)
            throws Exception {
        http.authorizeHttpRequests(auth -> auth
                        .requestMatchers("/css/**", "/js/**", "/images/**", "/ws/**", "/login", "/login/forgot-password", "/error")
                        .permitAll()
                        .anyRequest().authenticated())
                .addFilterAfter(fileShareManagementAccessFilter, AuthorizationFilter.class)
                .formLogin(form -> form
                        .loginPage("/login")
                        .successHandler(successHandler)
                        .failureHandler(failureHandler)
                        .permitAll())
                .logout(logout -> logout
                        .logoutSuccessHandler(logoutSuccessHandler)
                        .permitAll())
                /*
                 * Same-origin JSON assistants use fetch(); CSRF meta can be missing in some deployments.
                 * These URLs remain protected by authentication + method-level @PreAuthorize.
                 */
                .csrf(csrf -> csrf.ignoringRequestMatchers(
                        "/app/missions/api/assistant/**",
                        "/app/create-ticket/api/assistant/**"));
        return http.build();
    }
}
