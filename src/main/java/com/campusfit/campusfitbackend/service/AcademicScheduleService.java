package com.campusfit.campusfitbackend.service;

import com.campusfit.campusfitbackend.entity.AcademicSchedule;
import com.campusfit.campusfitbackend.repository.AcademicScheduleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class AcademicScheduleService {

    @Autowired
    private AcademicScheduleRepository repository;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    public AcademicSchedule addSchedule(AcademicSchedule schedule) {
        validateSchedule(schedule);
        checkForClashes(schedule);
        return repository.save(schedule);
    }

    public List<AcademicSchedule> getSchedulesByUserId(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID is required.");
        }
        return repository.findByUserIdOrderByStartTimeAsc(userId);
    }

    public boolean deleteSchedule(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("Schedule ID is required.");
        }
        if (repository.existsById(id)) {
            repository.deleteById(id);
            return true;
        }
        return false;
    }

    private void validateSchedule(AcademicSchedule schedule) {
        if (schedule == null) {
            throw new IllegalArgumentException("Schedule data cannot be null.");
        }
        if (schedule.getUserId() == null) {
            throw new IllegalArgumentException("User ID is required.");
        }
        if (schedule.getSubjectName() == null || schedule.getSubjectName().trim().isEmpty()) {
            throw new IllegalArgumentException("Subject name is required.");
        }
        if (schedule.getDayOfWeek() == null || schedule.getDayOfWeek().trim().isEmpty()) {
            throw new IllegalArgumentException("Day of week is required.");
        }
        if (schedule.getStartTime() == null) {
            throw new IllegalArgumentException("Start time is required.");
        }
        if (schedule.getEndTime() == null) {
            throw new IllegalArgumentException("End time is required.");
        }
        if (!schedule.getEndTime().isAfter(schedule.getStartTime())) {
            throw new IllegalArgumentException("End time must be after start time.");
        }
    }

    private void checkForClashes(AcademicSchedule schedule) {
        List<AcademicSchedule> dayClasses = repository.findByUserIdAndDayOfWeekIgnoreCase(
                schedule.getUserId(), schedule.getDayOfWeek().trim()
        );

        for (AcademicSchedule existing : dayClasses) {
            // Ignore self if updating an existing record
            if (schedule.getScheduleId() != null && schedule.getScheduleId().equals(existing.getScheduleId())) {
                continue;
            }

            // Conflict condition: newStart < exEnd && newEnd > exStart
            // Adjacent classes (e.g. 09:00 - 10:00 and 10:00 - 11:00) are explicitly allowed
            if (schedule.getStartTime().isBefore(existing.getEndTime()) &&
                schedule.getEndTime().isAfter(existing.getStartTime())) {

                String errorMsg = String.format(
                        "The class overlaps with %s (%s - %s). Please choose another time.",
                        existing.getSubjectName(),
                        existing.getStartTime().format(TIME_FORMATTER),
                        existing.getEndTime().format(TIME_FORMATTER)
                );
                throw new IllegalStateException(errorMsg);
            }
        }
    }
}
