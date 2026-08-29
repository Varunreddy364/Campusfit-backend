package com.campusfit.campusfitbackend.controller;

import com.campusfit.campusfitbackend.entity.FitnessProfile;
import com.campusfit.campusfitbackend.service.FitnessProfileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/fitness")
@CrossOrigin("*")
public class FitnessProfileController {

    @Autowired
    private FitnessProfileService service;

    @PostMapping("/save")
    public FitnessProfile saveProfile(@RequestBody FitnessProfile profile) {
        return service.saveProfile(profile);
    }
    @GetMapping("/{userId}")
    public FitnessProfile getProfile(@PathVariable Integer userId) {
        return service.getProfile(userId);
    }
}