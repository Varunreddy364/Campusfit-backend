package com.campusfit.campusfitbackend;

import com.campusfit.campusfitbackend.controller.StudentFeedbackController;
import com.campusfit.campusfitbackend.entity.StudentFeedback;
import com.campusfit.campusfitbackend.service.StudentFeedbackService;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StudentFeedbackControllerTest {

    @Mock
    private StudentFeedbackService service;

    @InjectMocks
    private StudentFeedbackController controller;

    private StudentFeedback sampleFeedback;

    @BeforeEach
    void setUp() {
        sampleFeedback = new StudentFeedback();
        sampleFeedback.setId(1L);
        sampleFeedback.setUserId(101L);
        sampleFeedback.setMood("Great");
        sampleFeedback.setAcademicLoadScore(5);
        sampleFeedback.setPlannerSatisfactionScore(5);
        sampleFeedback.setWorkoutDifficultyScore(4);
        sampleFeedback.setMissedActivityReason("Nutrition: 5/5 stars");
        sampleFeedback.setImprovementSuggestions("Everything works smoothly!");
        sampleFeedback.setCreatedAt(LocalDateTime.now());
    }

    @Test
    void testSubmitFeedback_Success() {
        when(service.saveFeedback(any(StudentFeedback.class))).thenReturn(sampleFeedback);

        ResponseEntity<?> response = controller.submitFeedback(sampleFeedback);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(sampleFeedback, response.getBody());
    }

    @Test
    void testSubmitFeedback_WithTransientModuleScores_MapsCorrectly() {
        StudentFeedback input = new StudentFeedback();
        input.setUserId(101L);
        input.setMood("Good");
        input.setStudyPlanScore(5);
        input.setReplanScore(4);
        input.setWorkoutScore(5);
        input.setNutritionScore(5);
        input.setImprovementSuggestions("Great planner!");

        when(service.saveFeedback(any(StudentFeedback.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ResponseEntity<?> response = controller.submitFeedback(input);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        StudentFeedback saved = (StudentFeedback) response.getBody();
        assertNotNull(saved);
        assertEquals(5, saved.getAcademicLoadScore());
        assertEquals(4, saved.getPlannerSatisfactionScore());
        assertEquals(5, saved.getWorkoutDifficultyScore());
        assertTrue(saved.getMissedActivityReason().contains("Nutrition: 5/5 stars"));
    }

    @Test
    void testSubmitFeedback_MissingUserId_ReturnsBadRequest() {
        sampleFeedback.setUserId(null);

        ResponseEntity<?> response = controller.submitFeedback(sampleFeedback);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody() instanceof Map);
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertEquals("User ID is required.", body.get("error"));
    }

    @Test
    void testSubmitFeedback_ServiceThrowsException_ReturnsServerError() {
        when(service.saveFeedback(any(StudentFeedback.class)))
                .thenThrow(new RuntimeException("Database connectivity issue"));

        ResponseEntity<?> response = controller.submitFeedback(sampleFeedback);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertTrue(response.getBody() instanceof Map);
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertTrue(body.get("error").toString().contains("Database connectivity issue"));
    }

    @Test
    void testGetUserFeedback_Success() {
        when(service.getFeedbackByUserId(101L)).thenReturn(Collections.singletonList(sampleFeedback));

        ResponseEntity<?> response = controller.getUserFeedback(101L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody() instanceof List);
        List<?> list = (List<?>) response.getBody();
        assertEquals(1, list.size());
    }

    @Test
    void testGetUserFeedback_InvalidUserId_ReturnsBadRequest() {
        ResponseEntity<?> response = controller.getUserFeedback(0L);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody() instanceof Map);
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertEquals("Valid User ID is required.", body.get("error"));
    }
}
