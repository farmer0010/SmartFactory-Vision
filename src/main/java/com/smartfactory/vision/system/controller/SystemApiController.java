package com.smartfactory.vision.system.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/system")
@Slf4j
public class SystemApiController {

    @PostMapping("/plc/command")
    public ResponseEntity<String> executePlcCommand(@RequestBody Map<String, String> payload) {
        String device = payload.get("device");
        String command = payload.get("command");
        String source = payload.get("source");

        log.info("[HMI -> PLC] Received command '{}' for device '{}' from source '{}'", command, device, source);

        // Dummy processing representing a network call to the real PLC
        if ("EMERGENCY_STOP".equals(command) && "ROBOT_ARM_1".equals(device)) {
            log.warn("🚨 EMERGENCY STOP ACTIVATED FOR ROBOT_ARM_1! 🚨");
            return ResponseEntity.ok("Command executed successfully");
        } else if ("RESTART".equals(command)) {
            log.info("Restart command sequence initiated for {}", device);
            return ResponseEntity.ok("Command executed successfully");
        }

        return ResponseEntity.badRequest().body("Unknown command or device");
    }
}
