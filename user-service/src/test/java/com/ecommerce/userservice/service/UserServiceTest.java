package com.ecommerce.userservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecommerce.userservice.dto.LoginRequest;
import com.ecommerce.userservice.dto.RegisterRequest;
import com.ecommerce.userservice.dto.UpdateUserRequest;
import com.ecommerce.userservice.entity.User;
import com.ecommerce.userservice.entity.UserRole;
import com.ecommerce.userservice.exception.DuplicateEmailException;
import com.ecommerce.userservice.exception.ForbiddenAccessException;
import com.ecommerce.userservice.exception.InvalidCredentialsException;
import com.ecommerce.userservice.exception.UserNotFoundException;
import com.ecommerce.userservice.repository.UserRepository;
import com.ecommerce.userservice.security.JwtService;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private UserService userService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setFirstName("Jane");
        user.setLastName("Doe");
        user.setEmail("jane@example.com");
        user.setPassword("encoded-password");
        user.setRole(UserRole.USER);
        user.setEnabled(true);
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void register_shouldCreateUserWithHashedPassword() {
        RegisterRequest request = new RegisterRequest("Jane", "Doe", "jane@example.com", "password123");

        when(userRepository.existsByEmail("jane@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(1L);
            saved.setCreatedAt(Instant.now());
            saved.setUpdatedAt(Instant.now());
            return saved;
        });

        var response = userService.register(request);

        assertThat(response.email()).isEqualTo("jane@example.com");
        assertThat(response.role()).isEqualTo(UserRole.USER);
        verify(passwordEncoder).encode("password123");
    }

    @Test
    void register_shouldRejectDuplicateEmail() {
        RegisterRequest request = new RegisterRequest("Jane", "Doe", "jane@example.com", "password123");
        when(userRepository.existsByEmail("jane@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.register(request))
                .isInstanceOf(DuplicateEmailException.class);
    }

    @Test
    void login_shouldReturnJwtWhenCredentialsValid() {
        LoginRequest request = new LoginRequest("jane@example.com", "password123");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(new UsernamePasswordAuthenticationToken("jane@example.com", "password123"));
        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(user));
        when(jwtService.generateToken(user)).thenReturn("jwt-token");
        when(jwtService.getExpirationMs()).thenReturn(3600000L);

        var response = userService.login(request);

        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.user().email()).isEqualTo("jane@example.com");
    }

    @Test
    void login_shouldRejectInvalidCredentials() {
        LoginRequest request = new LoginRequest("jane@example.com", "wrong-password");
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new InvalidCredentialsException());

        assertThatThrownBy(() -> userService.login(request))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void getUserById_shouldAllowOwnerAccess() {
        authenticateAs("jane@example.com");
        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(user));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        var response = userService.getUserById(1L);

        assertThat(response.id()).isEqualTo(1L);
    }

    @Test
    void getUserById_shouldRejectAccessToOtherUsersForNonAdmin() {
        User otherUser = new User();
        otherUser.setId(2L);
        otherUser.setEmail("other@example.com");
        otherUser.setRole(UserRole.USER);

        authenticateAs("jane@example.com");
        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(user));
        when(userRepository.findById(2L)).thenReturn(Optional.of(otherUser));

        assertThatThrownBy(() -> userService.getUserById(2L))
                .isInstanceOf(ForbiddenAccessException.class);
    }

    @Test
    void getUserById_shouldAllowAdminAccess() {
        user.setRole(UserRole.ADMIN);
        authenticateAs("jane@example.com", UserRole.ADMIN);
        User otherUser = new User();
        otherUser.setId(2L);
        otherUser.setEmail("other@example.com");
        otherUser.setRole(UserRole.USER);

        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(user));
        when(userRepository.findById(2L)).thenReturn(Optional.of(otherUser));

        var response = userService.getUserById(2L);

        assertThat(response.id()).isEqualTo(2L);
    }

    @Test
    void updateUser_shouldUpdateOwnProfile() {
        authenticateAs("jane@example.com");
        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(user));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        when(passwordEncoder.encode(anyString())).thenReturn("new-encoded-password");

        var response = userService.updateUser(1L, new UpdateUserRequest("Janet", null, "newpassword1", null));

        assertThat(response.firstName()).isEqualTo("Janet");
        verify(passwordEncoder).encode("newpassword1");
    }

    @Test
    void deleteUser_shouldDeleteOwnAccount() {
        authenticateAs("jane@example.com");
        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(user));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        userService.deleteUser(1L);

        verify(userRepository).delete(user);
    }

    @Test
    void deleteUser_shouldRejectDeletingOtherUsersForNonAdmin() {
        User otherUser = new User();
        otherUser.setId(2L);
        otherUser.setEmail("other@example.com");

        authenticateAs("jane@example.com");
        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(user));
        when(userRepository.findById(2L)).thenReturn(Optional.of(otherUser));

        assertThatThrownBy(() -> userService.deleteUser(2L))
                .isInstanceOf(ForbiddenAccessException.class);

        verify(userRepository, never()).delete(otherUser);
    }

    @Test
    void getUserById_shouldThrowWhenUserNotFound() {
        authenticateAs("jane@example.com");
        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(user));
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserById(99L))
                .isInstanceOf(UserNotFoundException.class);
    }

    private void authenticateAs(String email) {
        authenticateAs(email, UserRole.USER);
    }

    private void authenticateAs(String email, UserRole role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        email,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
    }
}
