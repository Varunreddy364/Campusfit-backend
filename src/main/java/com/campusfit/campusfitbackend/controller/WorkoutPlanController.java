package com.campusfit.campusfitbackend.controller;

import com.campusfit.campusfitbackend.entity.WorkoutPlan;
import com.campusfit.campusfitbackend.service.WorkoutPlanGenerationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/workout-plan")
@CrossOrigin(origins = "*")
public class WorkoutPlanController {

    @Autowired
    private WorkoutPlanGenerationService service;

    @PostMapping({"/generate/{userId}", "/generate"})
    public ResponseEntity<?> generateWorkoutPlan(
            @PathVariable(required = false) Long userId,
            @RequestParam(required = false) Long userIdParam,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        Long targetUserId = userId != null ? userId : userIdParam;
        if (targetUserId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "User ID is required."));
        }

        try {
            LocalDate targetDate = date != null ? date : LocalDate.now();
            WorkoutPlan generated = service.generateWorkoutPlan(targetUserId, targetDate);
            return ResponseEntity.ok(generated);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to generate workout plan: " + e.getMessage()));
        }
    }

    @GetMapping("/today/{userId}")
    public ResponseEntity<?> getTodayWorkout(@PathVariable Long userId) {
        try {
            Optional<WorkoutPlan> workout = service.getTodayWorkout(userId);
            if (workout.isPresent()) {
                return ResponseEntity.ok(workout.get());
            } else {
                return ResponseEntity.ok(Map.of("message", "No workout planned for today yet."));
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve today's workout: " + e.getMessage()));
        }
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<?> getWorkoutPlansByUser(@PathVariable Long userId) {
        try {
            List<WorkoutPlan> plans = service.getWorkoutPlansByUser(userId);
            return ResponseEntity.ok(plans);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve workout plans: " + e.getMessage()));
        }
    }

    @PutMapping({"/{workoutId}/complete", "/complete/{workoutId}"})
    public ResponseEntity<?> markWorkoutCompleted(@PathVariable Long workoutId) {
        try {
            Optional<WorkoutPlan> updated = service.markWorkoutCompleted(workoutId);
            if (updated.isPresent()) {
                return ResponseEntity.ok(updated.get());
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Workout plan not found with ID: " + workoutId));
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update workout status: " + e.getMessage()));
        }
    }

    @GetMapping("/history/{userId}")
    public ResponseEntity<?> getWorkoutHistory(@PathVariable Long userId) {
        try {
            Map<String, Object> history = service.getWorkoutHistory(userId);
            return ResponseEntity.ok(history);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve workout history: " + e.getMessage()));
        }
    }

    @DeleteMapping("/{workoutId}")
    public ResponseEntity<?> deleteWorkoutPlan(@PathVariable Long workoutId) {
        try {
            boolean deleted = service.deleteWorkoutPlan(workoutId);
            if (deleted) {
                return ResponseEntity.ok(Map.of("message", "Workout plan deleted successfully"));
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Workout plan not found with ID: " + workoutId));
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to delete workout plan: " + e.getMessage()));
        }
    }
}
