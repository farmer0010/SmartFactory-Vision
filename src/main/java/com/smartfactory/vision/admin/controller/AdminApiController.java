package com.smartfactory.vision.admin.controller;

import com.smartfactory.vision.audit.entity.AuditLog;
import com.smartfactory.vision.audit.service.AuditService;
import com.smartfactory.vision.auth.entity.AppUser;
import com.smartfactory.vision.auth.entity.Role;
import com.smartfactory.vision.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminApiController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    @GetMapping("/users")
    public ResponseEntity<List<AppUser>> getAllUsers() {
        return ResponseEntity.ok(userRepository.findAll());
    }

    @PostMapping("/users")
    public ResponseEntity<?> createUser(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        String displayName = body.get("displayName");
        String roleName = body.get("role");

        if (username == null || password == null || username.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "username과 password는 필수입니다."));
        }
        if (userRepository.existsByUsername(username)) {
            return ResponseEntity.badRequest().body(Map.of("error", "이미 존재하는 사용자 ID입니다."));
        }

        AppUser newUser = AppUser.builder()
                .username(username)
                .password(passwordEncoder.encode(password))
                .displayName(displayName != null ? displayName : username)
                .role("ADMIN".equalsIgnoreCase(roleName) ? Role.ADMIN : Role.WORKER)
                .build();

        userRepository.save(newUser);
        auditService.log(getUsername(null), "USER_MGMT",
                String.format("사용자 생성: %s (역할: %s)", username, roleName != null ? roleName : "WORKER"));
        return ResponseEntity.ok(Map.of("message", "사용자 생성 완료", "username", username));
    }

    @PutMapping("/users/{id}/role")
    public ResponseEntity<?> updateRole(@PathVariable Long id, @RequestBody Map<String, String> body) {
        Optional<AppUser> opt = userRepository.findById(id);
        if (opt.isEmpty())
            return ResponseEntity.notFound().build();
        AppUser user = opt.get();
        String newRole = body.get("role");
        if ("ADMIN".equalsIgnoreCase(newRole))
            user.setRole(Role.ADMIN);
        else if ("WORKER".equalsIgnoreCase(newRole))
            user.setRole(Role.WORKER);
        else
            return ResponseEntity.badRequest().body(Map.of("error", "유효하지 않은 역할입니다."));
        userRepository.save(user);
        auditService.log(getUsername(null), "USER_MGMT",
                String.format("역할 변경: 사용자 ID=%d → %s", id, newRole));
        return ResponseEntity.ok(Map.of("message", "역할 변경 완료"));
    }

    @PutMapping("/users/{id}/reset-password")
    public ResponseEntity<?> resetPassword(@PathVariable Long id) {
        Optional<AppUser> opt = userRepository.findById(id);
        if (opt.isEmpty())
            return ResponseEntity.notFound().build();
        AppUser user = opt.get();
        user.setPassword(passwordEncoder.encode("reset123"));
        userRepository.save(user);
        auditService.log(getUsername(null), "USER_MGMT",
                String.format("비밀번호 초기화: 사용자 ID=%d (%s)", id, user.getUsername()));
        return ResponseEntity.ok(Map.of("message", "비밀번호가 'reset123'으로 초기화되었습니다."));
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        if (!userRepository.existsById(id))
            return ResponseEntity.notFound().build();
        String username = userRepository.findById(id).map(AppUser::getUsername).orElse("ID:" + id);
        userRepository.deleteById(id);
        auditService.log(getUsername(null), "USER_MGMT",
                String.format("사용자 삭제: %s (ID=%d)", username, id));
        return ResponseEntity.ok(Map.of("message", "사용자 삭제 완료"));
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        long userCount = userRepository.count();
        return ResponseEntity.ok(Map.of(
                "userCount", userCount,
                "cameraCount", 4,
                "systemStatus", "ONLINE"));
    }

    @GetMapping("/audit")
    public ResponseEntity<List<AuditLog>> getAuditLogs() {
        return ResponseEntity.ok(auditService.getRecentLogs(100));
    }

    private String getUsername(Authentication auth) {
        return auth != null ? auth.getName() : "ADMIN";
    }
}
