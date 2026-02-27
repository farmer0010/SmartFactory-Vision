package com.smartfactory.vision.detection.service;

import com.jpyrust.JPyRustBridge;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import java.util.Map;
import java.util.concurrent.*;

@Slf4j
@Service
@lombok.RequiredArgsConstructor
public class JPyRustService {

    @Value("${app.ai.work-dir}")
    private String workDir;

    private final Map<String, JPyRustBridge> bridges = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    private final DetectionHistoryService detectionHistoryService;

    @PostConstruct
    public void init() {
        log.info("[Multi-Stream] Initializing Process Pool Manager in {}", workDir);

    }

    @PreDestroy
    public void cleanup() {
        log.info("[Multi-Stream] Shutting down bridge pool...");
        executor.shutdown();
        bridges.values().forEach(bridge -> {
            try {
                bridge.close();
            } catch (Exception e) {
                log.error("Error closing bridge", e);
            }
        });
        bridges.clear();
    }

    private JPyRustBridge getBridge(String cameraId) {
        return bridges.computeIfAbsent(cameraId, id -> {
            log.info("[Multi-Stream] Spawning new worker for {}", id);
            try {
                JPyRustBridge bridge = new JPyRustBridge(id);
                bridge.initialize(workDir);
                log.info("[Multi-Stream] Worker {} ready", id);
                return bridge;
            } catch (Exception e) {
                log.error("Failed to initialize worker for {}", id, e);
                throw new RuntimeException("Bridge init failed", e);
            }
        });
    }

    public CompletableFuture<String> detectAsync(String cameraId, byte[] imageBytes) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                JPyRustBridge bridge = getBridge(cameraId);
                log.info("[AI] Processing frame for {} ({} bytes)...", cameraId, imageBytes.length);

                ByteBuffer buffer = ByteBuffer.allocateDirect(imageBytes.length);
                buffer.put(imageBytes);
                buffer.flip();

                byte[] resultBytes = bridge.processImage(
                        buffer,
                        imageBytes.length,
                        640, 480, 3);

                if (resultBytes == null) {
                    return "{\"error\": \"Native bridge returned null\"}";
                }

                String result = new String(resultBytes, StandardCharsets.UTF_8);

                if (result.trim().startsWith("[")) {
                     result = "{\"detections\":" + result + "}";
                }

                detectionHistoryService.saveLogAsync(cameraId, result);

                return result;
            } catch (Exception e) {
                log.error("Detection failed for {}", cameraId, e);
                return "{\"error\": \"" + e.getMessage() + "\"}";
            }
        }, executor);
    }
}
