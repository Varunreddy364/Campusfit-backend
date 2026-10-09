package com.campusfit.campusfitbackend.service;

import com.campusfit.campusfitbackend.entity.StudentFeedback;
import com.campusfit.campusfitbackend.repository.StudentFeedbackRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class StudentFeedbackService {

    @Autowired
    private StudentFeedbackRepository repository;

    public StudentFeedback saveFeedback(StudentFeedback feedback) {
        if (feedback == null) {
            throw new IllegalArgumentException("Feedback data cannot be null.");
        }
        if (feedback.getUserId() == null || feedback.getUserId() <= 0) {
            throw new IllegalArgumentException("User ID is required.");
        }
        return repository.save(feedback);
    }

    public List<StudentFeedback> getFeedbackByUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("Valid User ID is required.");
        }
        return repository.findByUserIdOrderByCreatedAtDesc(userId);
    }
}
