package com.campusfit.campusfitbackend.controller;

import com.campusfit.campusfitbackend.entity.BmiRecord;
import com.campusfit.campusfitbackend.service.BmiRecordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/bmi")
@CrossOrigin(origins = "*")
public class BmiRecordController {

    @Autowired
    private BmiRecordService service;

    @PostMapping("/log")
    public ResponseEntity<?> logBmi(@RequestBody Map<String, Object> body) {
        try {
            if (!body.containsKey("userId") || !body.containsKey("height") || !body.containsKey("weight")) {
                return ResponseEntity.badRequest().body(Map.of("error", "userId, height, and weight are required."));
            }

            Long userId = Long.valueOf(body.get("userId").toString());
            Double height = Double.valueOf(body.get("height").toString());
            Double weight = Double.valueOf(body.get("weight").toString());

            BmiRecord record = service.logBmi(userId, height, weight);
            return ResponseEntity.ok(record);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to log BMI record: " + e.getMessage()));
        }
    }

    @GetMapping("/history/{userId}")
    public ResponseEntity<?> getHistory(@PathVariable Long userId) {
        try {
            List<BmiRecord> history = service.getBmiHistory(userId);
            return ResponseEntity.ok(history);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fetch BMI history: " + e.getMessage()));
        }
    }

    @GetMapping("/latest/{userId}")
    public ResponseEntity<?> getLatest(@PathVariable Long userId) {
        try {
            Optional<BmiRecord> latest = service.getLatestBmi(userId);
            if (latest.isPresent()) {
                return ResponseEntity.ok(latest.get());
            } else {
                return ResponseEntity.ok(Map.of("message", "No BMI record found."));
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fetch latest BMI: " + e.getMessage()));
        }
    }

    @DeleteMapping("/{recordId}")
    public ResponseEntity<?> deleteRecord(@PathVariable Long recordId) {
        try {
            boolean deleted = service.deleteBmiRecord(recordId);
            if (deleted) {
                return ResponseEntity.ok(Map.of("message", "BMI record deleted successfully."));
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Record not found with ID: " + recordId));
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to delete record: " + e.getMessage()));
        }
    }
}
