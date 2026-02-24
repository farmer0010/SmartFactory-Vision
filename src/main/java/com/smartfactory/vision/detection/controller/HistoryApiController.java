package com.smartfactory.vision.detection.controller;

import org.springframework.http.ResponseEntity;

import com.opencsv.CSVWriter;
import com.smartfactory.vision.detection.entity.DetectionLog;
import com.smartfactory.vision.detection.repository.DetectionLogRepository;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/history")
@RequiredArgsConstructor
public class HistoryApiController {

    private final DetectionLogRepository detectionLogRepository;

    @GetMapping("/recent")
    public List<DetectionLog> getRecentLogs() {
        return detectionLogRepository.findAllByOrderByTimestampDesc(PageRequest.of(0, 50)).getContent();
    }

    @GetMapping("/stats/yield")
    public List<Map<String, Object>> getYieldStats() {
        // Last 24 hours
        return detectionLogRepository.getYieldStatsPerHour(LocalDateTime.now().minusDays(1));
    }

    @GetMapping("/stats/heatmap")
    public List<Map<String, Object>> getHeatmapStats() {
        // Last 24 hours
        return detectionLogRepository.getDefectHeatmapStats(LocalDateTime.now().minusDays(1));
    }

    @GetMapping("/report/csv")
    public void downloadCsvReport(HttpServletResponse response) throws IOException {
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"defect_report.csv\"");
        // Output BOM for Excel UTF-8
        response.getOutputStream().write(0xEF);
        response.getOutputStream().write(0xBB);
        response.getOutputStream().write(0xBF);

        try (CSVWriter writer = new CSVWriter(new OutputStreamWriter(response.getOutputStream()))) {
            String[] header = {"ID", "Camera ID", "Timestamp", "Label", "Confidence", "Is Defect"};
            writer.writeNext(header);

            List<DetectionLog> defects = detectionLogRepository.findByIsDefectTrueOrderByTimestampDesc();
            for (DetectionLog log : defects) {
                String[] row = {
                        String.valueOf(log.getId()),
                        log.getCameraId(),
                        log.getTimestamp().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                        log.getLabel(),
                        String.valueOf(log.getConfidence()),
                        String.valueOf(log.isDefect())
                };
                writer.writeNext(row);
            }
        }
    }

    @GetMapping("/report/pdf")
    public void downloadPdfReport(HttpServletResponse response) {
        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "attachment; filename=\"defect_report.pdf\"");

        try {
            com.itextpdf.text.Document document = new com.itextpdf.text.Document();
            com.itextpdf.text.pdf.PdfWriter.getInstance(document, response.getOutputStream());
            document.open();

            com.itextpdf.text.Font titleFont = com.itextpdf.text.FontFactory.getFont(com.itextpdf.text.FontFactory.HELVETICA_BOLD, 18);
            document.add(new com.itextpdf.text.Paragraph("SmartFactory Defect Report", titleFont));
            document.add(new com.itextpdf.text.Paragraph("Generated at: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))));
            document.add(com.itextpdf.text.Chunk.NEWLINE);

            com.itextpdf.text.pdf.PdfPTable table = new com.itextpdf.text.pdf.PdfPTable(5);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{1f, 2f, 3f, 2f, 2f});

            // Table Header
            String[] headers = {"ID", "Camera", "Timestamp", "Label", "Confidence"};
            for (String h : headers) {
                com.itextpdf.text.pdf.PdfPCell cell = new com.itextpdf.text.pdf.PdfPCell(new com.itextpdf.text.Phrase(h));
                cell.setBackgroundColor(com.itextpdf.text.BaseColor.LIGHT_GRAY);
                table.addCell(cell);
            }

            // Table Body
            List<DetectionLog> defects = detectionLogRepository.findByIsDefectTrueOrderByTimestampDesc();
            for (DetectionLog log : defects) {
                table.addCell(String.valueOf(log.getId()));
                table.addCell(log.getCameraId());
                table.addCell(log.getTimestamp().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                table.addCell(log.getLabel());
                table.addCell(String.format("%.2f%%", log.getConfidence() * 100));
            }

            document.add(table);
            document.close();
        } catch (Exception e) {
            throw new RuntimeException("Error generating PDF", e);
        }
    }

    @GetMapping(value = "/report/defect/{id}/frame", produces = "image/jpeg")
    public ResponseEntity<byte[]> getDefectFrame(@org.springframework.web.bind.annotation.PathVariable Long id) {
        DetectionLog log = detectionLogRepository.findById(id).orElse(null);
        if (log == null || log.getWorkDir() == null) {
            return ResponseEntity.notFound().build();
        }

        try {
            java.nio.file.Path imagePath = java.nio.file.Paths.get(log.getWorkDir(), "frame.jpg");
            if (!java.nio.file.Files.exists(imagePath)) {
                return ResponseEntity.notFound().build();
            }
            byte[] imageBytes = java.nio.file.Files.readAllBytes(imagePath);
            return ResponseEntity.ok().body(imageBytes);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
