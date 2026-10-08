package com.campusfit.campusfitbackend.service;

import com.campusfit.campusfitbackend.entity.AcademicTask;
import com.campusfit.campusfitbackend.repository.AcademicTaskRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Service
public class AcademicTaskService {

    private static final List<String> VALID_TASK_TYPES = Arrays.asList(
            "ASSIGNMENT", "EXAM", "PROJECT", "LAB"
    );

    @Autowired
    private AcademicTaskRepository repository;

    public AcademicTask addTask(AcademicTask task) {
        validateTask(task);
        if (task.getStatus() == null || task.getStatus().trim().isEmpty()) {
            task.setStatus("PENDING");
        } else {
            task.setStatus(task.getStatus().trim().toUpperCase());
        }
        if (task.getPriority() == null || task.getPriority().trim().isEmpty()) {
            task.setPriority("MEDIUM");
        } else {
            task.setPriority(task.getPriority().trim().toUpperCase());
        }
        task.setTaskType(task.getTaskType().trim().toUpperCase());
        return repository.save(task);
    }

    public List<AcademicTask> getTasksByUserId(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID is required.");
        }
        return repository.findByUserIdOrderByDeadlineAsc(userId);
    }

    public Optional<AcademicTask> markTaskCompleted(Long taskId) {
        if (taskId == null) {
            throw new IllegalArgumentException("Task ID is required.");
        }
        return repository.findById(taskId).map(task -> {
            task.setStatus("COMPLETED");
            return repository.save(task);
        });
    }

    public Optional<AcademicTask> updateTaskStatus(Long taskId, String status) {
        if (taskId == null) {
            throw new IllegalArgumentException("Task ID is required.");
        }
        if (status == null || status.trim().isEmpty()) {
            throw new IllegalArgumentException("Status cannot be empty.");
        }
        return repository.findById(taskId).map(task -> {
            task.setStatus(status.trim().toUpperCase());
            return repository.save(task);
        });
    }

    public boolean deleteTask(Long taskId) {
        if (taskId == null) {
            throw new IllegalArgumentException("Task ID is required.");
        }
        if (repository.existsById(taskId)) {
            repository.deleteById(taskId);
            return true;
        }
        return false;
    }

    private void validateTask(AcademicTask task) {
        if (task == null) {
            throw new IllegalArgumentException("Task data cannot be null.");
        }
        if (task.getUserId() == null) {
            throw new IllegalArgumentException("User ID is required.");
        }
        if (task.getTitle() == null || task.getTitle().trim().isEmpty()) {
            throw new IllegalArgumentException("Task title is required.");
        }
        if (task.getTaskType() == null || task.getTaskType().trim().isEmpty()) {
            throw new IllegalArgumentException("Task type is required.");
        }
        String normalizedType = task.getTaskType().trim().toUpperCase();
        if (!VALID_TASK_TYPES.contains(normalizedType)) {
            throw new IllegalArgumentException("Invalid task type: " + task.getTaskType() + ". Must be one of: ASSIGNMENT, EXAM, PROJECT, LAB");
        }
        if (task.getDeadline() == null) {
            throw new IllegalArgumentException("Deadline is required.");
        }
        if (task.getEstimatedHours() != null && task.getEstimatedHours() < 0) {
            throw new IllegalArgumentException("Estimated hours cannot be negative.");
        }
    }
}
