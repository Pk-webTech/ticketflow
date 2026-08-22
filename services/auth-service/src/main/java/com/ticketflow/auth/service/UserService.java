package com.ticketflow.auth.service;

import com.ticketflow.auth.dto.UserProfileResponse;
import com.ticketflow.auth.entity.User;
import com.ticketflow.auth.exception.UserNotFoundException;
import com.ticketflow.auth.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(UUID userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
        return toProfile(user);
    }

    @Transactional(readOnly = true)
    public Page<UserProfileResponse> listUsers(Pageable pageable) {
        return userRepository.findAll(pageable).map(this::toProfile);
    }

    @Transactional
    public UserProfileResponse setEnabled(UUID userId, boolean enabled) {
        User user = userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
        user.setEnabled(enabled);
        return toProfile(userRepository.save(user));
    }

    private UserProfileResponse toProfile(User user) {
        return new UserProfileResponse(
                user.getId(), user.getEmail(), user.getFullName(), user.getPhone(),
                user.getRole(), user.isEnabled(), user.getCreatedAt()
        );
    }
}
