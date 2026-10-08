package com.campusfit.campusfitbackend;

import com.campusfit.campusfitbackend.entity.AcademicTask;
import com.campusfit.campusfitbackend.repository.AcademicTaskRepository;
import com.campusfit.campusfitbackend.service.AcademicTaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AcademicTaskServiceTest {

    @Mock
    private AcademicTaskRepository repository;

    @InjectMocks
    private AcademicTaskService service;

    private AcademicTask sampleTask;

    @BeforeEach
    void setUp() {
        sampleTask = new AcademicTask();
        sampleTask.setTaskId(1L);
        sampleTask.setUserId(101L);
        sampleTask.setTitle("DBMS Assignment 1");
        sampleTask.setTaskType("ASSIGNMENT");
        sampleTask.setDeadline(LocalDateTime.of(2026, 10, 15, 23, 59));
        sampleTask.setEstimatedHours(3.0);
        sampleTask.setPriority("HIGH");
        sampleTask.setStatus("PENDING");
    }

    @Test
    void testAddTaskSuccess() {
        when(repository.save(any(AcademicTask.class))).thenReturn(sampleTask);

        AcademicTask saved = service.addTask(sampleTask);

        assertNotNull(saved);
        assertEquals("DBMS Assignment 1", saved.getTitle());
        assertEquals("ASSIGNMENT", saved.getTaskType());
        assertEquals("PENDING", saved.getStatus());
        verify(repository, times(1)).save(sampleTask);
    }

    @Test
    void testAddTaskValidationErrors() {
        // Missing title
        sampleTask.setTitle("");
        assertThrows(IllegalArgumentException.class, () -> service.addTask(sampleTask));

        // Missing task type
        sampleTask.setTitle("Valid Title");
        sampleTask.setTaskType(null);
        assertThrows(IllegalArgumentException.class, () -> service.addTask(sampleTask));

        // Invalid task type
        sampleTask.setTaskType("WORKOUT");
        assertThrows(IllegalArgumentException.class, () -> service.addTask(sampleTask));

        // Missing deadline
        sampleTask.setTaskType("EXAM");
        sampleTask.setDeadline(null);
        assertThrows(IllegalArgumentException.class, () -> service.addTask(sampleTask));

        // Negative estimated hours
        sampleTask.setDeadline(LocalDateTime.now().plusDays(2));
        sampleTask.setEstimatedHours(-1.0);
        assertThrows(IllegalArgumentException.class, () -> service.addTask(sampleTask));
    }

    @Test
    void testGetTasksByUserId() {
        when(repository.findByUserIdOrderByDeadlineAsc(101L)).thenReturn(Arrays.asList(sampleTask));

        List<AcademicTask> list = service.getTasksByUserId(101L);

        assertEquals(1, list.size());
        assertEquals("DBMS Assignment 1", list.get(0).getTitle());
    }

    @Test
    void testMarkTaskCompleted() {
        when(repository.findById(1L)).thenReturn(Optional.of(sampleTask));
        when(repository.save(any(AcademicTask.class))).thenAnswer(i -> i.getArgument(0));

        Optional<AcademicTask> updated = service.markTaskCompleted(1L);

        assertTrue(updated.isPresent());
        assertEquals("COMPLETED", updated.get().getStatus());
        verify(repository, times(1)).save(sampleTask);
    }

    @Test
    void testDeleteTask() {
        when(repository.existsById(1L)).thenReturn(true);
        doNothing().when(repository).deleteById(1L);

        boolean result = service.deleteTask(1L);

        assertTrue(result);
        verify(repository, times(1)).deleteById(1L);
    }
}
