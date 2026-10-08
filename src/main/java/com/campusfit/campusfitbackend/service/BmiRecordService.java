package com.campusfit.campusfitbackend.service;

import com.campusfit.campusfitbackend.entity.BmiRecord;
import com.campusfit.campusfitbackend.entity.User;
import com.campusfit.campusfitbackend.repository.BmiRecordRepository;
import com.campusfit.campusfitbackend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class BmiRecordService {

    @Autowired
    private BmiRecordRepository bmiRecordRepository;

    @Autowired
    private UserRepository userRepository;

    @Transactional
    public BmiRecord logBmi(Long userId, Double height, Double weight) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID is required.");
        }
        if (height == null || height <= 0) {
            throw new IllegalArgumentException("Valid height (cm) is required.");
        }
        if (weight == null || weight <= 0) {
            throw new IllegalArgumentException("Valid weight (kg) is required.");
        }

        double hMeters = height / 100.0;
        double bmiVal = Math.round((weight / (hMeters * hMeters)) * 10.0) / 10.0;
        String category = classifyBmi(bmiVal);
        int healthScore = calculateHealthScore(bmiVal);

        BmiRecord record = new BmiRecord();
        record.setUserId(userId);
        record.setHeight(height);
        record.setWeight(weight);
        record.setBmi(bmiVal);
        record.setCategory(category);
        record.setHealthScore(healthScore);
        record.setRecordedAt(LocalDateTime.now());

        // Update user entity height & weight if user exists
        try {
            Optional<User> userOpt = userRepository.findById(userId.intValue());
            if (userOpt.isPresent()) {
                User u = userOpt.get();
                u.setHeight((int) Math.round(height));
                u.setWeight((int) Math.round(weight));
                userRepository.save(u);
            }
        } catch (Exception ignored) {
        }

        return bmiRecordRepository.save(record);
    }

    public List<BmiRecord> getBmiHistory(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID is required.");
        }
        return bmiRecordRepository.findByUserIdOrderByRecordedAtAsc(userId);
    }

    public Optional<BmiRecord> getLatestBmi(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID is required.");
        }
        return bmiRecordRepository.findFirstByUserIdOrderByRecordedAtDesc(userId);
    }

    public boolean deleteBmiRecord(Long recordId) {
        if (recordId == null) {
            throw new IllegalArgumentException("Record ID is required.");
        }
        if (bmiRecordRepository.existsById(recordId)) {
            bmiRecordRepository.deleteById(recordId);
            return true;
        }
        return false;
    }

    public static String classifyBmi(double bmi) {
        if (bmi < 16.0) return "Severely Underweight";
        if (bmi < 17.0) return "Moderately Underweight";
        if (bmi < 18.5) return "Mildly Underweight";
        if (bmi < 20.0) return "Healthy Lean";
        if (bmi < 22.0) return "Ideal Fitness Zone";
        if (bmi < 25.0) return "Optimal Health Zone";
        if (bmi < 27.0) return "Slightly Above Optimal";
        if (bmi < 30.0) return "Early Weight Management Zone";
        if (bmi < 35.0) return "High Risk Weight Zone";
        return "Critical Health Improvement Zone";
    }

    public static int calculateHealthScore(double bmi) {
        // Optimal BMI (20.0 - 24.0) gives base score around 95-100
        if (bmi >= 21.0 && bmi <= 23.5) {
            return 98;
        } else if (bmi >= 20.0 && bmi < 21.0 || bmi > 23.5 && bmi <= 24.9) {
            return 93;
        } else if (bmi >= 18.5 && bmi < 20.0) {
            return 88;
        } else if (bmi >= 25.0 && bmi <= 26.9) {
            return 84;
        } else if (bmi >= 17.0 && bmi < 18.5) {
            return 76;
        } else if (bmi >= 27.0 && bmi <= 29.9) {
            return 72;
        } else if (bmi >= 16.0 && bmi < 17.0) {
            return 60;
        } else if (bmi >= 30.0 && bmi <= 34.9) {
            return 58;
        } else {
            return 45;
        }
    }
}
