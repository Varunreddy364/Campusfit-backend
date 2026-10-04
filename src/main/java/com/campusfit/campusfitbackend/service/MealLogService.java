package com.campusfit.campusfitbackend.service;

import com.campusfit.campusfitbackend.entity.MealLog;
import com.campusfit.campusfitbackend.repository.MealLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class MealLogService {

    @Autowired
    private MealLogRepository repository;

    public MealLog addMeal(MealLog meal) {

        if (meal.getMealDate() == null) {
            meal.setMealDate(LocalDate.now());
        }

        return repository.save(meal);
    }

    public List<MealLog> getMealsByUser(Integer userid) {
        return repository.findByUserid(userid);
    }

    public List<MealLog> getTodaysMeals(Integer userid) {
        return repository.findByUseridAndMealDate(
                userid,
                LocalDate.now()
        );
    }

    public void deleteMeal(Integer mealid) {
        repository.deleteById(mealid);
    }
}