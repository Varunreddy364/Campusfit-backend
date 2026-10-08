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
        return repository.save(feedback);
    }

    public List<StudentFeedback> getFeedbackByUserId(Long userId) {
        return repository.findByUserId(userId);
    }
}
