package com.campusfit.campusfitbackend;

import com.campusfit.campusfitbackend.controller.AcademicTaskController;
import com.campusfit.campusfitbackend.entity.AcademicTask;
import com.campusfit.campusfitbackend.service.AcademicTaskService;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AcademicTaskControllerTest {

    @Mock
    private AcademicTaskService service;

    @InjectMocks
    private AcademicTaskController controller;

    private AcademicTask sampleTask;

    @BeforeEach
    void setUp() {
        sampleTask = new AcademicTask();
        sampleTask.setTaskId(1L);
        sampleTask.setUserId(101L);
        sampleTask.setTitle("OS Final Exam Prep");
        sampleTask.setTaskType("EXAM");
        sampleTask.setDeadline(LocalDateTime.of(2026, 11, 20, 10, 0));
        sampleTask.setEstimatedHours(5.0);
        sampleTask.setPriority("HIGH");
        sampleTask.setStatus("PENDING");
    }

    @Test
    void testAddTaskSuccess() {
        when(service.addTask(any(AcademicTask.class))).thenReturn(sampleTask);

        ResponseEntity<?> response = controller.addTask(sampleTask);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(sampleTask, response.getBody());
    }

    @Test
    void testAddTaskValidationError() {
        when(service.addTask(any(AcademicTask.class)))
                .thenThrow(new IllegalArgumentException("Task title is required."));

        ResponseEntity<?> response = controller.addTask(sampleTask);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody() instanceof Map);
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertEquals("Task title is required.", body.get("error"));
    }

    @Test
    void testGetTasksByUser() {
        when(service.getTasksByUserId(101L)).thenReturn(Collections.singletonList(sampleTask));

        ResponseEntity<?> response = controller.getTasksByUser(101L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody() instanceof List);
        List<?> list = (List<?>) response.getBody();
        assertEquals(1, list.size());
    }

    @Test
    void testMarkTaskCompletedSuccess() {
        sampleTask.setStatus("COMPLETED");
        when(service.markTaskCompleted(1L)).thenReturn(Optional.of(sampleTask));

        ResponseEntity<?> response = controller.markTaskCompleted(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(sampleTask, response.getBody());
    }

    @Test
    void testMarkTaskCompletedNotFound() {
        when(service.markTaskCompleted(99L)).thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.markTaskCompleted(99L);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void testDeleteTaskSuccess() {
        when(service.deleteTask(1L)).thenReturn(true);

        ResponseEntity<?> response = controller.deleteTask(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertEquals("Task deleted successfully", body.get("message"));
    }
}
