package com.campusfit.campusfitbackend.controller;

import com.campusfit.campusfitbackend.entity.MealLog;
import com.campusfit.campusfitbackend.service.MealLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/meal")
@CrossOrigin(origins = "*")
public class MealLogController {

    @Autowired
    private MealLogService mealLogService;

    @PostMapping("/add")
    public MealLog addMeal(@RequestBody MealLog meal) {
        return mealLogService.addMeal(meal);
    }

    @GetMapping("/user/{userid}")
    public List<MealLog> getMealsByUser(@PathVariable Integer userid) {
        return mealLogService.getMealsByUser(userid);
    }

    @GetMapping("/today/{userid}")
    public List<MealLog> getTodaysMeals(@PathVariable Integer userid) {
        return mealLogService.getTodaysMeals(userid);
    }

    @PutMapping("/update/{mealid}")
    public MealLog updateMeal(@PathVariable Integer mealid, @RequestBody MealLog updatedMeal) {
        return mealLogService.updateMeal(mealid, updatedMeal);
    }

    @GetMapping("/weekly/{userid}")
    public List<MealLog> getWeeklyMeals(@PathVariable Integer userid) {
        return mealLogService.getWeeklyMeals(userid);
    }

    @DeleteMapping("/{mealid}")
    public String deleteMeal(@PathVariable Integer mealid) {
        mealLogService.deleteMeal(mealid);
        return "Meal deleted successfully";
    }
}