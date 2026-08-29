package com.campusfit.campusfitbackend.service;

import com.campusfit.campusfitbackend.entity.User;
import com.campusfit.campusfitbackend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    @Autowired
    private UserRepository repository;

    public User register(User user) {
        return repository.save(user);
    }

    public User login(String email, String password) {

        User user = repository.findByEmail(email);

        if (user != null && user.getPassword().equals(password)) {
            return user;
        }

        return null;
    }
}