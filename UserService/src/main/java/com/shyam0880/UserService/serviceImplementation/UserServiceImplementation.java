package com.shyam0880.UserService.serviceImplementation;

import com.shyam0880.UserService.dto.UserResponse;
import com.shyam0880.UserService.entity.User;
import com.shyam0880.UserService.exception.ResourceNotFoundException;
import com.shyam0880.UserService.repository.UserRepository;
import com.shyam0880.UserService.service.UserService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserServiceImplementation implements UserService {

    private final UserRepository userRepository;

    public UserServiceImplementation(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User createUser(User user){
        return userRepository.save(user);
    }

    public List<User> getAllUser(){
        return userRepository.findAll();
    }

    public User getUserById(Long userId){
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User with id " + userId + " not found"));
    }

    public boolean existById(Long id) {
        return userRepository.existsById(id);
    }

    public User updateUser(Long id, User updatedUser) {
        return userRepository.findById(id)
                .map(existingUser -> {
                    existingUser.setName(updatedUser.getName());
                    existingUser.setPhoneNo(updatedUser.getPhoneNo());
                    existingUser.setAddress(updatedUser.getAddress());
                    return userRepository.save(existingUser);
                })
                .orElseThrow(() -> new ResourceNotFoundException("User with id " + id + " not found"));
    }

    public void deleteUser(Long id) {
        if (!userRepository.existsById(id)) {
            throw new ResourceNotFoundException("User with id " + id + " not found");
        }
        userRepository.deleteById(id);
    }

    public UserResponse mapToResponse(User user) {
        if (user == null) {
            return null;
        }
        return new UserResponse(
                user.getUserId(),
                user.getName(),
                user.getPhoneNo(),
                user.getAddress()
        );
    }
}
