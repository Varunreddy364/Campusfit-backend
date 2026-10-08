package com.campusfit.campusfitbackend.controller;

import com.campusfit.campusfitbackend.entity.StudyPlan;
import com.campusfit.campusfitbackend.service.PlanGenerationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/study-plan")
@CrossOrigin(origins = "*")
public class StudyPlanController {

    @Autowired
    private PlanGenerationService service;

    @PostMapping({"/generate/{userId}", "/generate"})
    public ResponseEntity<?> generateStudyPlan(@PathVariable(required = false) Long userId,
                                               @RequestParam(required = false) Long userIdParam) {
        Long targetUserId = userId != null ? userId : userIdParam;
        if (targetUserId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "User ID is required."));
        }
        try {
            List<StudyPlan> generated = service.generateStudyPlan(targetUserId);
            return ResponseEntity.ok(generated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to generate study plan: " + e.getMessage()));
        }
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<?> getStudyPlansByUser(@PathVariable Long userId) {
        try {
            List<StudyPlan> plans = service.getStudyPlansByUserId(userId);
            return ResponseEntity.ok(plans);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve study plans: " + e.getMessage()));
        }
    }

    @PutMapping({"/{planId}/complete", "/complete/{planId}"})
    public ResponseEntity<?> markPlanCompleted(@PathVariable Long planId) {
        try {
            Optional<StudyPlan> updated = service.markPlanCompleted(planId);
            if (updated.isPresent()) {
                return ResponseEntity.ok(updated.get());
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Study plan not found with ID: " + planId));
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update plan: " + e.getMessage()));
        }
    }

    @PutMapping("/{planId}/status")
    public ResponseEntity<?> updatePlanStatus(@PathVariable Long planId, @RequestBody Map<String, String> statusBody) {
        try {
            String status = statusBody.get("status");
            Optional<StudyPlan> updated = service.updatePlanStatus(planId, status);
            if (updated.isPresent()) {
                return ResponseEntity.ok(updated.get());
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Study plan not found with ID: " + planId));
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update plan status: " + e.getMessage()));
        }
    }

    @DeleteMapping("/{planId}")
    public ResponseEntity<?> deletePlan(@PathVariable Long planId) {
        try {
            boolean deleted = service.deletePlan(planId);
            if (deleted) {
                return ResponseEntity.ok(Map.of("message", "Study plan session deleted successfully"));
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Study plan not found with ID: " + planId));
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to delete plan: " + e.getMessage()));
        }
    }
}
