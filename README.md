# 🏭 SmartFactory-Vision

> **"Real-Time AI-Powered Factory Inspection: Multi-Stream Architecture with JPyRust."**

![Build Status](https://img.shields.io/github/actions/workflow/status/farmer0010/SmartFactory-Vision/build.yml?style=flat-square&logo=github&label=Build)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.1-brightgreen?style=flat-square&logo=spring-boot)
[![Java](https://img.shields.io/badge/Java-17+-orange?style=flat-square&logo=openjdk)](https://openjdk.org/)
[![JPyRust](https://img.shields.io/badge/JPyRust-v1.2.0-blue?style=flat-square)](https://github.com/farmer0010/JPyRust)
[![Python](https://img.shields.io/badge/Python-3.11-blue?style=flat-square&logo=python)](https://www.python.org/)

[🇰🇷 한국어 버전 (README_KR.md)](README_KR.md)

---

## 💡 Introduction

**SmartFactory-Vision** is a high-performance AI vision inspection system. It utilizes **Spring Boot** and **JPyRust** to achieve native-speed object detection across multiple camera streams.

This project demonstrates a robust pipeline: capturing frames from multiple sources, processing them through an AI worker pool, persisting detection history in an H2 database, and pushing real-time overlays to a 2x2 grid dashboard via WebSocket.

---

## ✨ Key Features

| Feature | Description |
|---------|-------------|
| 📹 **Multi-Stream Support** | Simultaneous monitoring of up to 4 camera feeds in a 2x2 grid |
| 🧠 **AI Worker Pool** | Managed pool of JPyRust workers for parallel image processing |
| 🗄️ **Detection History** | Automatic persistence of all detection logs to H2/JPA database |
| ⚡ **Performance** | ~40ms GPU inference with zero Python VM startup overhead |
| 📡 **Independent Topics** | Camera-specific WebSocket topics for low-latency updates |
| 🖥️ **Sci-Fi UI** | Advanced neon-themed dashboard with real-time analytics & charts |

---

## 🏗️ Architecture

```mermaid
graph TD
    subgraph "Browser (Client)"
        C1["📷 CAM-1"]
        C2["📷 CAM-2"]
        C3["📷 CAM-3"]
        C4["📷 CAM-4"]
        UI["🖥️ 2x2 Dashboard UI"]
    end

    subgraph "Spring Boot Backend"
        WC["WebcamController"]
        Pool["🧠 AI Service Pool"]
        subgraph "Persistence"
            DHS["DetectionHistoryService"]
            DB[("H2 Database")]
        end
        WS["WebSocket Broker"]
    end

    subgraph "JPyRust Native Layer"
        Bridge["JPyRustBridge (Native)"]
        SHM["Shared Memory"]
        Daemon["Python Daemon"]
        YOLO["YOLOv8 Model"]
    end

    C1 & C2 & C3 & C4 -- "POST /api/stream/frame?camId=X" --> WC
    WC -- "Queue Tasks" --> Pool
    Pool -- "Direct Map" --> Bridge
    Bridge -- "IPC" --> SHM
    SHM -- "Inference" --> Daemon
    Daemon --> YOLO
    
    YOLO -- "Result JSON" --> Pool
    Pool -- "Async Save" --> DHS
    DHS --> DB
    
    Pool -- "Push Topic /detections/camX" --> WS
    WS -- "STOMP" --> UI
```

---

## 📊 Performance Benchmark

| Metric | Target | Result |
|--------|:-----:|:------:|
| **Max Streams** | 4 | Verified (2x2 Grid) |
| **Inference Latency** | < 50ms | ~42ms (NVIDIA GPU) |
| **Persistence Delay** | < 10ms | Async Non-blocking |
| **UI Stability** | 60 FPS | Stable (Chart.js Opt.) |

---

## 🚀 Getting Started

### Prerequisites
- **Java 17+**
- **Webcam/Vision Input**
- **Windows 10/11** (Optimized for Win-SHMEM)

### Running the System
```bash
# 1. Clone
git clone https://github.com/farmer0010/SmartFactory-Vision.git

# 2. Build & Run
./gradlew bootRun
```

Visit `http://localhost:8080` to access the multi-stream dashboard.

---

## 📁 System Configuration

The system is configured to use a standardized work directory and database path to ensure environment stability on Windows.

- **Work Directory**: `~/.jpyrust` (User Home)
- **Database**: H2 File-based (`~/.jpyrust/historydb`)
- **Model**: YOLOv8n (Auto-downloaded)

---

## 📜 Roadmap

- [x] Multi-camera support (Phase 2)
- [x] Detection history persistence
- [x] 2x2 High-density grid UI
- [ ] Custom Model Training Integration
- [ ] Distributed Worker Support (Multiple Machines)
- [ ] Dockerized Deployment

---

## 📄 License

MIT License. See [LICENSE](LICENSE) for details.

---

<p align="center">
  <b>🏭 SmartFactory-Vision</b><br>
  <i>Empowering Manufacturing with Next-Gen AI Vision.</i>
</p>
