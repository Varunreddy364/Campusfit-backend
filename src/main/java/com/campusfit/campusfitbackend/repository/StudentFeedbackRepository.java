package com.campusfit.campusfitbackend.repository;

import com.campusfit.campusfitbackend.entity.StudentFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StudentFeedbackRepository extends JpaRepository<StudentFeedback, Long> {
    List<StudentFeedback> findByUserId(Long userId);
}
