package com.campusfit.campusfitbackend;

import com.campusfit.campusfitbackend.controller.StudyPlanController;
import com.campusfit.campusfitbackend.entity.StudyPlan;
import com.campusfit.campusfitbackend.service.PlanGenerationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudyPlanControllerTest {

    @Mock
    private PlanGenerationService service;

    @InjectMocks
    private StudyPlanController controller;

    private StudyPlan samplePlan;

    @BeforeEach
    void setUp() {
        samplePlan = new StudyPlan(
                1L, 101L, "DBMS Assignment",
                LocalDateTime.of(2026, 10, 10, 10, 0),
                LocalDateTime.of(2026, 10, 10, 11, 0),
                "PENDING"
        );
    }

    @Test
    void testGenerateStudyPlanSuccess() {
        when(service.generateStudyPlan(101L)).thenReturn(Collections.singletonList(samplePlan));

        ResponseEntity<?> response = controller.generateStudyPlan(101L, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody() instanceof List);
        List<?> list = (List<?>) response.getBody();
        assertEquals(1, list.size());
    }

    @Test
    void testGetStudyPlansByUser() {
        when(service.getStudyPlansByUserId(101L)).thenReturn(Collections.singletonList(samplePlan));

        ResponseEntity<?> response = controller.getStudyPlansByUser(101L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody() instanceof List);
    }

    @Test
    void testMarkPlanCompleted() {
        samplePlan.setStatus("COMPLETED");
        when(service.markPlanCompleted(1L)).thenReturn(Optional.of(samplePlan));

        ResponseEntity<?> response = controller.markPlanCompleted(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(samplePlan, response.getBody());
    }

    @Test
    void testDeletePlanSuccess() {
        when(service.deletePlan(1L)).thenReturn(true);

        ResponseEntity<?> response = controller.deletePlan(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertEquals("Study plan session deleted successfully", body.get("message"));
    }
}
