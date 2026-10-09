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
            if (feedback == null || feedback.getUserId() == null || feedback.getUserId() <= 0) {
                return ResponseEntity.badRequest().body(Map.of("error", "User ID is required."));
            }
            if (feedback.getCreatedAt() == null) {
                feedback.setCreatedAt(LocalDateTime.now());
            }

            // Map transient category star ratings if direct column scores were not populated
            if (feedback.getAcademicLoadScore() == null && feedback.getStudyPlanScore() != null) {
                feedback.setAcademicLoadScore(feedback.getStudyPlanScore());
            }
            if (feedback.getPlannerSatisfactionScore() == null && feedback.getReplanScore() != null) {
                feedback.setPlannerSatisfactionScore(feedback.getReplanScore());
            }
            if (feedback.getWorkoutDifficultyScore() == null && feedback.getWorkoutScore() != null) {
                feedback.setWorkoutDifficultyScore(feedback.getWorkoutScore());
            }

            // Consolidate nutrition score into missedActivityReason column without requiring database schema changes
            if (feedback.getNutritionScore() != null) {
                String nutritionTag = "Nutrition: " + feedback.getNutritionScore() + "/5 stars";
                if (feedback.getMissedActivityReason() == null || feedback.getMissedActivityReason().trim().isEmpty()) {
                    feedback.setMissedActivityReason(nutritionTag);
                } else if (!feedback.getMissedActivityReason().contains("Nutrition:")) {
                    feedback.setMissedActivityReason(nutritionTag + " • " + feedback.getMissedActivityReason().trim());
                }
            }

            StudentFeedback saved = service.saveFeedback(feedback);
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to submit feedback: " + e.getMessage()));
        }
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<?> getUserFeedback(@PathVariable Long userId) {
        try {
            if (userId == null || userId <= 0) {
                return ResponseEntity.badRequest().body(Map.of("error", "Valid User ID is required."));
            }
            List<StudentFeedback> list = service.getFeedbackByUserId(userId);
            return ResponseEntity.ok(list);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve feedback: " + e.getMessage()));
        }
    }
}
