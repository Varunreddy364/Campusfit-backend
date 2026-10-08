package com.campusfit.campusfitbackend;

import com.campusfit.campusfitbackend.controller.WorkoutPlanController;
import com.campusfit.campusfitbackend.entity.WorkoutPlan;
import com.campusfit.campusfitbackend.service.WorkoutPlanGenerationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkoutPlanControllerTest {

    @Mock
    private WorkoutPlanGenerationService service;

    @InjectMocks
    private WorkoutPlanController controller;

    private Long userId = 101L;

    @BeforeEach
    void setUp() {}

    @Test
    void testGenerateWorkoutPlanSuccess() {
        WorkoutPlan plan = new WorkoutPlan();
        plan.setWorkoutId(1L);
        plan.setUserId(userId);
        plan.setWorkoutTitle("Upper Body Routine");

        when(service.generateWorkoutPlan(eq(userId), any(LocalDate.class))).thenReturn(plan);

        ResponseEntity<?> response = controller.generateWorkoutPlan(userId, null, null);
        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody() instanceof WorkoutPlan);
    }

    @Test
    void testGenerateWorkoutPlanValidationFailure() {
        when(service.generateWorkoutPlan(eq(userId), any(LocalDate.class)))
                .thenThrow(new IllegalStateException("Please add your academic schedule first."));

        ResponseEntity<?> response = controller.generateWorkoutPlan(userId, null, null);
        assertEquals(400, response.getStatusCode().value());
        assertTrue(response.getBody().toString().contains("Please add your academic schedule first."));
    }

    @Test
    void testGetTodayWorkout() {
        WorkoutPlan plan = new WorkoutPlan();
        plan.setWorkoutId(1L);
        when(service.getTodayWorkout(userId)).thenReturn(Optional.of(plan));

        ResponseEntity<?> response = controller.getTodayWorkout(userId);
        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody() instanceof WorkoutPlan);
    }

    @Test
    void testMarkWorkoutCompleted() {
        WorkoutPlan plan = new WorkoutPlan();
        plan.setWorkoutId(1L);
        plan.setStatus("COMPLETED");

        when(service.markWorkoutCompleted(1L)).thenReturn(Optional.of(plan));

        ResponseEntity<?> response = controller.markWorkoutCompleted(1L);
        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void testGetWorkoutHistory() {
        Map<String, Object> history = Map.of("streak", 3, "totalCompleted", 10);
        when(service.getWorkoutHistory(userId)).thenReturn(history);

        ResponseEntity<?> response = controller.getWorkoutHistory(userId);
        assertEquals(200, response.getStatusCode().value());
    }
}
