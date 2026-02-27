package com.smartfactory.vision.auth.service;

import com.smartfactory.vision.auth.entity.AppUser;
import com.smartfactory.vision.auth.entity.Role;
import com.smartfactory.vision.auth.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }

    @PostConstruct
    public void initDefaultUsers() {
        createIfNotExists("admin", "admin123", Role.ADMIN, "관리자");
        createIfNotExists("worker1", "worker123", Role.WORKER, "작업자 1");
        log.info("[Security] Default user accounts have been initialized.");
    }

    private void createIfNotExists(String username, String rawPassword, Role role, String displayName) {
        if (!userRepository.existsByUsername(username)) {
            AppUser user = AppUser.builder()
                    .username(username)
                    .password(passwordEncoder.encode(rawPassword))
                    .role(role)
                    .displayName(displayName)
                    .build();
            userRepository.save(user);
            log.info("[Security] Created default user '{}' with role {}", username, role);
        }
    }
}
