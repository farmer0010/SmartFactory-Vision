package com.smartfactory.vision.detection.service;

import com.jpyrust.JPyRustBridge;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
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
        log.info("[Multi-Stream] Initializing Process Pool in {}", workDir);
        try {
            JPyRustBridge.initialize(workDir, workDir, "yolov8n.pt", 0.5f);

            for (String cam : List.of("cam1", "cam2")) {
                log.info("[Multi-Stream] Spawning worker for {}", cam);
                bridges.put(cam, new JPyRustBridge());
                Thread.sleep(2000); 
            }
            log.info("[Multi-Stream] Pool ready. Active bridges: {}", bridges.size());
        } catch (Exception e) {
            log.error("[Multi-Stream] Failed to initialize bridges", e);
        }
    }

    @PreDestroy
    public void cleanup() {
        log.info("[Multi-Stream] Shutting down bridge pool...");
        executor.shutdown();
        bridges.clear();
    }

    public CompletableFuture<String> detectAsync(String cameraId, byte[] imageBytes) {
        JPyRustBridge bridge = bridges.get(cameraId);
        if (bridge == null) {
            log.warn("Unknown camera ID: {}", cameraId);
            return CompletableFuture.completedFuture("{\"error\": \"Unknown Camera ID\"}");
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("[AI] Processing frame for {} ({} bytes)...", cameraId, imageBytes.length);

                ByteBuffer buffer = ByteBuffer.allocateDirect(imageBytes.length);
                buffer.put(imageBytes);
                buffer.flip();

                byte[] resultBytes = bridge.processImage(
                        workDir,
                        buffer,
                        imageBytes.length,
                        640, 480, 3);

                if (resultBytes == null) {
                    return "{\"error\": \"Native bridge returned null\"}";
                }

                String result = new String(resultBytes, StandardCharsets.UTF_8);

                detectionHistoryService.saveLogAsync(cameraId, result);

                return result;
            } catch (Exception e) {
                log.error("Detection failed for {}", cameraId, e);
                return "{\"error\": \"" + e.getMessage() + "\"}";
            }
        }, executor);
    }
}
