package com.smartfactory.vision.detection.service;

import com.jpyrust.JPyRustBridge;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class JPyRustService {

    private final JPyRustBridge jPyRustBridge = new JPyRustBridge();

    @PostConstruct
    public void init() {
        try {
            String workDir = System.getProperty("user.home") + "/.jpyrust";
            log.info("[JPyRustService] Initializing Native Bridge (v1.1.1)...");

            jPyRustBridge.initialize(workDir, null);

        } catch (Exception e) {
            log.error("[JPyRustService] Initialization failed", e);
        }
    }

    @PreDestroy
    public void cleanup() {
        log.info("[JPyRustService] Stopping service...");
    }

    public CompletableFuture<String> detectAsync(byte[] imageBytes) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String workDir = System.getProperty("user.home") + "/.jpyrust";

                ByteBuffer buffer = ByteBuffer.allocateDirect(imageBytes.length);
                buffer.put(imageBytes);
                buffer.flip();

                byte[] resultBytes = jPyRustBridge.processImage(
                        workDir,
                        buffer,
                        imageBytes.length,
                        640, 480, 3);
                if (resultBytes == null) {
                    return "{\"error\": \"Native bridge returned null\"}";
                }

                return new String(resultBytes, StandardCharsets.UTF_8);
            } catch (Exception e) {
                log.error("AI Inference Error", e);
                return "{\"error\": \"" + e.getMessage() + "\"}";
            }
        });
    }
}
