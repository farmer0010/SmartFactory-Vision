package com.smartfactory.vision.stream.controller;

import com.smartfactory.vision.detection.service.JPyRustService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/stream")
@RequiredArgsConstructor
public class WebcamController {

    private final JPyRustService jPyRustService;
    private final SimpMessagingTemplate messagingTemplate;

    @PostMapping("/frame")
    public void receiveFrame(@RequestParam("image") MultipartFile file) throws IOException {
        byte[] bytes = file.getBytes();

        jPyRustService.detectAsync(bytes).thenAccept(result -> {
            messagingTemplate.convertAndSend("/topic/detections", result);
        });
    }
}
