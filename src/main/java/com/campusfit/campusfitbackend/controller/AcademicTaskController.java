package com.campusfit.campusfitbackend.controller;

import com.campusfit.campusfitbackend.entity.AcademicTask;
import com.campusfit.campusfitbackend.service.AcademicTaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/academic-task")
@CrossOrigin(origins = "*")
public class AcademicTaskController {

    @Autowired
    private AcademicTaskService service;

    @PostMapping({"/add", ""})
    public ResponseEntity<?> addTask(@RequestBody AcademicTask task) {
        try {
            AcademicTask saved = service.addTask(task);
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to add academic task: " + e.getMessage()));
        }
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<?> getTasksByUser(@PathVariable Long userId) {
        try {
            List<AcademicTask> tasks = service.getTasksByUserId(userId);
            return ResponseEntity.ok(tasks);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve tasks: " + e.getMessage()));
        }
    }

    @PutMapping({"/{taskId}/complete", "/complete/{taskId}"})
    public ResponseEntity<?> markTaskCompleted(@PathVariable Long taskId) {
        try {
            Optional<AcademicTask> updated = service.markTaskCompleted(taskId);
            if (updated.isPresent()) {
                return ResponseEntity.ok(updated.get());
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Task not found with ID: " + taskId));
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update task: " + e.getMessage()));
        }
    }

    @PutMapping("/{taskId}/status")
    public ResponseEntity<?> updateTaskStatus(@PathVariable Long taskId, @RequestBody Map<String, String> statusBody) {
        try {
            String status = statusBody.get("status");
            Optional<AcademicTask> updated = service.updateTaskStatus(taskId, status);
            if (updated.isPresent()) {
                return ResponseEntity.ok(updated.get());
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Task not found with ID: " + taskId));
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update task status: " + e.getMessage()));
        }
    }

    @DeleteMapping("/{taskId}")
    public ResponseEntity<?> deleteTask(@PathVariable Long taskId) {
        try {
            boolean deleted = service.deleteTask(taskId);
            if (deleted) {
                return ResponseEntity.ok(Map.of("message", "Task deleted successfully"));
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Task not found with ID: " + taskId));
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to delete task: " + e.getMessage()));
        }
    }
}
