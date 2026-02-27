package com.smartfactory.vision.auth.config;

import com.smartfactory.vision.auth.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

        private final UserService userService;
        private final PasswordEncoder passwordEncoder;

        @Bean
        public DaoAuthenticationProvider authenticationProvider() {
                DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
                provider.setUserDetailsService(userService);
                provider.setPasswordEncoder(passwordEncoder);
                return provider;
        }

        @Bean
        public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
                return config.getAuthenticationManager();
        }

        @Bean
        public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
                http
                                .authenticationProvider(authenticationProvider())
                                .authorizeHttpRequests(auth -> auth

                                                .requestMatchers("/login", "/login?error", "/login?logout").permitAll()
                                                .requestMatchers("/webjars/**", "/css/**", "/js/**", "/images/**")
                                                .permitAll()

                                                .requestMatchers("/ws-stomp/**").authenticated()

                                                .requestMatchers("/h2-console/**").hasRole("ADMIN")

                                                .requestMatchers("/api/history/cleanup").hasRole("ADMIN")
                                                .requestMatchers("/api/system/plc/**").hasRole("ADMIN")

                                                .requestMatchers("/admin/**").hasRole("ADMIN")
                                                .requestMatchers("/api/admin/**").hasRole("ADMIN")

                                                .requestMatchers("/api/copilot/**").authenticated()

                                                .requestMatchers("/api/**").authenticated()
                                                .requestMatchers("/", "/history", "/history/**").authenticated()

                                                .anyRequest().authenticated())
                                .formLogin(form -> form
                                                .loginPage("/login")
                                                .defaultSuccessUrl("/", true)
                                                .failureUrl("/login?error=true")
                                                .usernameParameter("username")
                                                .passwordParameter("password")
                                                .permitAll())
                                .httpBasic(basic -> basic.realmName("SmartFactory API"))
                                .logout(logout -> logout
                                                .logoutRequestMatcher(new AntPathRequestMatcher("/logout"))
                                                .logoutSuccessUrl("/login?logout=true")
                                                .invalidateHttpSession(true)
                                                .deleteCookies("JSESSIONID")
                                                .permitAll())
                                .csrf(csrf -> csrf

                                                .ignoringRequestMatchers("/h2-console/**")

                                                .ignoringRequestMatchers("/ws-stomp/**")

                                                .ignoringRequestMatchers("/api/**"))
                                .headers(headers -> headers

                                                .frameOptions(frame -> frame.sameOrigin()));

                return http.build();
        }
}
