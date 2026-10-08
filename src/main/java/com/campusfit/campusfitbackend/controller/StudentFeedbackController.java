package com.campusfit.campusfitbackend.controller;

import com.campusfit.campusfitbackend.entity.StudentFeedback;
import com.campusfit.campusfitbackend.service.StudentFeedbackService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/feedback")
@CrossOrigin(origins = "*")
public class StudentFeedbackController {

    @Autowired
    private StudentFeedbackService service;

    @PostMapping("/submit")
    public ResponseEntity<?> submitFeedback(@RequestBody StudentFeedback feedback) {
        try {
            if (feedback.getCreatedAt() == null) {
                feedback.setCreatedAt(LocalDateTime.now());
            }
            StudentFeedback saved = service.saveFeedback(feedback);
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to submit feedback: " + e.getMessage()));
        }
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<?> getUserFeedback(@PathVariable Long userId) {
        try {
            List<StudentFeedback> list = service.getFeedbackByUserId(userId);
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve feedback: " + e.getMessage()));
        }
    }
}
