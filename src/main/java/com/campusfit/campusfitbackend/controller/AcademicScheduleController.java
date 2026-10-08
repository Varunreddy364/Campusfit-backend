package com.campusfit.campusfitbackend.controller;

import com.campusfit.campusfitbackend.entity.AcademicSchedule;
import com.campusfit.campusfitbackend.service.AcademicScheduleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/academic-schedule")
@CrossOrigin(origins = "*")
public class AcademicScheduleController {

    @Autowired
    private AcademicScheduleService service;

    @PostMapping("/add")
    public ResponseEntity<?> addSchedule(@RequestBody AcademicSchedule schedule) {
        try {
            AcademicSchedule saved = service.addSchedule(schedule);
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to save academic schedule: " + e.getMessage()));
        }
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<?> getSchedulesByUser(@PathVariable Long userId) {
        try {
            List<AcademicSchedule> schedules = service.getSchedulesByUserId(userId);
            return ResponseEntity.ok(schedules);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve schedules: " + e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteSchedule(@PathVariable Long id) {
        try {
            boolean deleted = service.deleteSchedule(id);
            if (deleted) {
                return ResponseEntity.ok(Map.of("message", "Academic schedule deleted successfully"));
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Academic schedule not found with ID: " + id));
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to delete schedule: " + e.getMessage()));
        }
    }
}
