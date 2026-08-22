package com.ticketflow.auth.controller;

import com.ticketflow.auth.dto.UserProfileResponse;
import com.ticketflow.auth.security.AuthenticatedUser;
import com.ticketflow.auth.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /** Any authenticated user can fetch their own profile. Also used internally
     *  by other services (booking, seat-hold) to resolve a userId → profile. */
    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> me(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(userService.getProfile(principal.userId()));
    }

    @GetMapping("/users/{userId}")
    public ResponseEntity<UserProfileResponse> getUser(@PathVariable UUID userId) {
        return ResponseEntity.ok(userService.getProfile(userId));
    }

    @GetMapping("/users")
    public ResponseEntity<Page<UserProfileResponse>> listUsers(Pageable pageable) {
        return ResponseEntity.ok(userService.listUsers(pageable));
    }

    @PatchMapping("/users/{userId}/enabled")
    public ResponseEntity<UserProfileResponse> setEnabled(
            @PathVariable UUID userId,
            @RequestParam boolean enabled
    ) {
        return ResponseEntity.ok(userService.setEnabled(userId, enabled));
    }
}
