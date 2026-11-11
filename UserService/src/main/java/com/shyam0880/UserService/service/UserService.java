package com.shyam0880.UserService.service;

import com.shyam0880.UserService.dto.UserResponse;
import com.shyam0880.UserService.entity.User;

import java.util.List;

public interface UserService {

    List<User> getAllUser();

    User getUserById(Long id);

    boolean existById(Long id);

    User createUser(User user);

    User updateUser(Long id, User updatedUser);

    void deleteUser(Long id);

    public UserResponse mapToResponse(User user);
}
