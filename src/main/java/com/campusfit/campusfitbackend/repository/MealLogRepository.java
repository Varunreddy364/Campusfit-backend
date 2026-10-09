package com.campusfit.campusfitbackend.repository;

import com.campusfit.campusfitbackend.entity.MealLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface MealLogRepository extends JpaRepository<MealLog, Integer> {

    List<MealLog> findByUserid(Integer userid);

    List<MealLog> findByUseridAndMealDate(Integer userid, LocalDate mealDate);

    List<MealLog> findByUseridAndMealDateBetweenOrderByMealDateAsc(Integer userid, LocalDate start, LocalDate end);
}