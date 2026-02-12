package com.smartfactory.vision.detection.service;

import com.jpyrust.JPyRustBridge;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
public class JPyRustService {

    private final JPyRustBridge jPyRustBridge = new JPyRustBridge();
    private final AtomicBoolean processing = new AtomicBoolean(false);
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @PostConstruct
    public void init() {
        try {
            String workDir = System.getProperty("user.home") + "/.jpyrust";
            log.info("[JPyRustService] Initializing Native Bridge (v1.2.0)...");
            jPyRustBridge.initialize(workDir, null);
            log.info("[JPyRustService] Bridge initialized. Waiting for Python daemon...");
        } catch (Exception e) {
            log.error("[JPyRustService] Initialization failed", e);
        }
    }

    @PreDestroy
    public void cleanup() {
        log.info("[JPyRustService] Stopping service...");
        executor.shutdown();
    }

    public CompletableFuture<String> detectAsync(byte[] imageBytes) {
        if (!processing.compareAndSet(false, true)) {
            return CompletableFuture.completedFuture("{\"skipped\": true}");
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("[AI] Processing frame ({} bytes)...", imageBytes.length);
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
                    log.warn("[AI] Native bridge returned null");
                    return "{\"error\": \"Native bridge returned null\"}";
                }

                String result = new String(resultBytes, StandardCharsets.UTF_8);
                log.info("[AI] Result: {}", result.substring(0, Math.min(200, result.length())));
                return result;
            } catch (Exception e) {
                log.error("[AI] Inference Error", e);
                return "{\"error\": \"" + e.getMessage() + "\"}";
            } finally {
                processing.set(false);
            }
        }, executor);
    }
}
