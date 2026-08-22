package com.ticketflow.auth.service;

import com.ticketflow.auth.dto.AuthResponse;
import com.ticketflow.auth.dto.LoginRequest;
import com.ticketflow.auth.dto.RegisterRequest;
import com.ticketflow.auth.entity.Role;
import com.ticketflow.auth.entity.User;
import com.ticketflow.auth.exception.EmailAlreadyRegisteredException;
import com.ticketflow.auth.exception.InvalidCredentialsException;
import com.ticketflow.auth.exception.SelfRegistrationNotAllowedException;
import com.ticketflow.auth.repository.RefreshTokenRepository;
import com.ticketflow.auth.repository.UserRepository;
import com.ticketflow.auth.security.JwtProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4); // low cost for fast tests
    private final JwtProvider jwtProvider = new JwtProvider(
            "test-secret-key-at-least-32-bytes-long-for-hs256", 3600000L, 604800000L);

    private AuthService authService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        authService = new AuthService(userRepository, refreshTokenRepository, passwordEncoder, jwtProvider);
    }

    @Test
    void registerRejectsAdminSelfRegistration() {
        RegisterRequest request = new RegisterRequest("admin@x.com", "Password1", "Admin Guy", null, Role.ADMIN);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(SelfRegistrationNotAllowedException.class);

        verifyNoInteractions(userRepository);
    }

    @Test
    void registerRejectsDuplicateEmail() {
        RegisterRequest request = new RegisterRequest("dup@x.com", "Password1", "Dup User", null, Role.CUSTOMER);
        when(userRepository.existsByEmailIgnoreCase("dup@x.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(EmailAlreadyRegisteredException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void registerSucceedsAndIssuesTokens() {
        RegisterRequest request = new RegisterRequest("new@x.com", "Password1", "New User", "9999999999", Role.CUSTOMER);
        when(userRepository.existsByEmailIgnoreCase("new@x.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });

        AuthResponse response = authService.register(request);

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(response.email()).isEqualTo("new@x.com");
        assertThat(response.role()).isEqualTo(Role.CUSTOMER);
        verify(refreshTokenRepository).save(any());
    }

    @Test
    void loginFailsWithWrongPassword() {
        User existing = User.builder()
                .id(UUID.randomUUID())
                .email("user@x.com")
                .passwordHash(passwordEncoder.encode("CorrectPassword1"))
                .fullName("User")
                .role(Role.CUSTOMER)
                .enabled(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(userRepository.findByEmailIgnoreCase("user@x.com")).thenReturn(Optional.of(existing));

        LoginRequest request = new LoginRequest("user@x.com", "WrongPassword");

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void loginFailsWhenAccountDisabled() {
        User disabled = User.builder()
                .id(UUID.randomUUID())
                .email("disabled@x.com")
                .passwordHash(passwordEncoder.encode("Password1"))
                .fullName("Disabled User")
                .role(Role.CUSTOMER)
                .enabled(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(userRepository.findByEmailIgnoreCase("disabled@x.com")).thenReturn(Optional.of(disabled));

        assertThatThrownBy(() -> authService.login(new LoginRequest("disabled@x.com", "Password1")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void loginSucceedsWithCorrectCredentials() {
        User existing = User.builder()
                .id(UUID.randomUUID())
                .email("good@x.com")
                .passwordHash(passwordEncoder.encode("Password1"))
                .fullName("Good User")
                .role(Role.ORGANISER)
                .enabled(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(userRepository.findByEmailIgnoreCase("good@x.com")).thenReturn(Optional.of(existing));

        AuthResponse response = authService.login(new LoginRequest("good@x.com", "Password1"));

        assertThat(response.userId()).isEqualTo(existing.getId());
        assertThat(response.role()).isEqualTo(Role.ORGANISER);
    }
}
