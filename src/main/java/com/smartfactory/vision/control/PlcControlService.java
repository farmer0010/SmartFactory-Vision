package com.smartfactory.vision.control;

import com.ghgande.j2mod.modbus.facade.ModbusTCPMaster;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class PlcControlService {

    private ModbusTCPMaster master;
    private boolean isConnected = false;

    @PostConstruct
    public void init() {
        connectToPlc();
    }

    private void connectToPlc() {
        try {
            // j2mod (v3.x) ModbusTCPMaster(host, port)
            master = new ModbusTCPMaster("localhost", 502);
            master.connect();

            isConnected = true;
            log.info("[PLC] Connected to Soft-PLC (localhost:502)");
        } catch (Exception e) {
            log.warn("[PLC] Failed to connect to Soft-PLC: {}", e.getMessage());
            isConnected = false;
        }
    }

    @Async
    public void triggerDefectAction() {
        if (master == null || !isConnected) {
            // Try to reconnect once
            connectToPlc();
            if (!isConnected)
                return;
        }

        try {
            // Write TRUE to Coil 1 (Address 1 in 1-based, 0 in 0-based. j2mod usually uses
            // 0-based index)
            // Let's assume Address 0 corresponds to Coil 1
            master.writeCoil(0, true);
            log.info("[PLC] Triggered Reject Cylinder (Coil 0: ON)");

            // Wait 500ms to simulate cylinder action
            Thread.sleep(500);

            // Write FALSE to Coil 1
            master.writeCoil(0, false);
            log.info("[PLC] Reset Reject Cylinder (Coil 0: OFF)");

        } catch (Exception e) {
            log.error("[PLC] Error simulating defect action: {}", e.getMessage());
            // Attempt strict reconnect on next call
            try {
                master.disconnect();
            } catch (Exception ex) {
            }
            isConnected = false;
        }
    }

    @PreDestroy
    public void cleanup() {
        if (master != null) {
            try {
                master.disconnect();
                log.info("[PLC] Disconnected from Soft-PLC");
            } catch (Exception e) {
                log.error("[PLC] Error disconnecting", e);
            }
        }
    }
}
