package com.smartfactory.vision.system.controller;

import com.smartfactory.vision.control.OpcUaClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/system")
@RequiredArgsConstructor
@Slf4j
public class SystemApiController {

    private final OpcUaClientService opcUaClientService;

    @PostMapping("/plc/command")
    public ResponseEntity<String> executePlcCommand(@RequestBody Map<String, String> payload) {
        String device = payload.get("device");
        String command = payload.get("command");
        String source = payload.get("source");

        log.info("[HMI -> PLC] Received command '{}' for device '{}' from source '{}'", command, device, source);

        if ("EMERGENCY_STOP".equals(command)) {
            log.warn("🚨 EMERGENCY STOP ACTIVATED FOR {}! 🚨", device);
            opcUaClientService.triggerEStop();
            return ResponseEntity.ok("Command executed successfully");
        } else if ("RESTART".equals(command)) {
            log.info("Restart command sequence initiated for {}", device);
            return ResponseEntity.ok("Command executed successfully");
        }

        return ResponseEntity.badRequest().body("Unknown command or device");
    }

    @PostMapping("/plc/reset")
    public ResponseEntity<String> resetPlcCommand() {
        log.info("[HMI -> PLC] Received RESET Command from UI");
        opcUaClientService.resetPlc();
        return ResponseEntity.ok("PLC state reset successfully");
    }
}
