package com.campusfit.campusfitbackend.controller;

import com.campusfit.campusfitbackend.entity.User;
import com.campusfit.campusfitbackend.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user")
@CrossOrigin("*")
public class UserController {

    @Autowired
    private UserService service;

    @PostMapping("/register")
    public User registerUser(@RequestBody User user) {
        return service.register(user);
    }

    @PostMapping("/login")
    public User loginUser(@RequestBody User user) {
        return service.login(
                user.getEmail(),
                user.getPassword()
        );
    }
    @GetMapping("/{userId}")
    public User getUser(@PathVariable Integer userId) {
        return service.getUserById(userId);
    }
}