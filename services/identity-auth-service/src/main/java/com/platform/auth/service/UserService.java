package com.platform.auth.service;

import com.platform.auth.domain.Role;
import com.platform.auth.domain.User;
import com.platform.auth.exception.UserNotFoundException;
import com.platform.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public User getById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + id));
    }

    @Transactional(readOnly = true)
    public User getByEmail(String email) {
        return userRepository.findByEmail(email.toLowerCase().strip())
                .orElseThrow(() -> new UserNotFoundException("User not found: " + email));
    }

    @Transactional
    public User grantRole(UUID userId, Role role) {
        User user = getById(userId);
        user.getRoles().add(role);
        return userRepository.save(user);
    }

    @Transactional
    public User revokeRole(UUID userId, Role role) {
        User user = getById(userId);
        user.getRoles().remove(role);
        return userRepository.save(user);
    }
}
