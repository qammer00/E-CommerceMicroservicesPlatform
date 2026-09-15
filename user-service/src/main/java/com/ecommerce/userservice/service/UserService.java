package com.ecommerce.userservice.service;

import com.ecommerce.userservice.dto.LoginRequest;
import com.ecommerce.userservice.dto.LoginResponse;
import com.ecommerce.userservice.dto.RegisterRequest;
import com.ecommerce.userservice.dto.UpdateUserRequest;
import com.ecommerce.userservice.dto.UserResponse;
import com.ecommerce.userservice.entity.User;
import com.ecommerce.userservice.entity.UserRole;
import com.ecommerce.userservice.exception.DuplicateEmailException;
import com.ecommerce.userservice.exception.ForbiddenAccessException;
import com.ecommerce.userservice.exception.InvalidCredentialsException;
import com.ecommerce.userservice.exception.UserNotFoundException;
import com.ecommerce.userservice.mapper.UserMapper;
import com.ecommerce.userservice.repository.UserRepository;
import com.ecommerce.userservice.security.JwtService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public UserService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateEmailException(request.email());
        }

        User user = new User();
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setEmail(request.email().toLowerCase());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole(UserRole.USER);
        user.setEnabled(true);

        return UserMapper.toResponse(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(
                    request.email().toLowerCase(),
                    request.password()));
        } catch (Exception ex) {
            throw new InvalidCredentialsException();
        }

        User user = userRepository.findByEmail(request.email().toLowerCase())
                .orElseThrow(InvalidCredentialsException::new);

        String token = jwtService.generateToken(user);
        return new LoginResponse(
                token,
                "Bearer",
                jwtService.getExpirationMs(),
                UserMapper.toResponse(user));
    }

    @Transactional(readOnly = true)
    public UserResponse getCurrentUserProfile() {
        return UserMapper.toResponse(getCurrentUserEntity());
    }

    @Transactional(readOnly = true)
    public UserResponse getUserById(Long id) {
        User currentUser = getCurrentUserEntity();
        User targetUser = getUserOrThrow(id);
        assertCanAccessUser(currentUser, targetUser);
        return UserMapper.toResponse(targetUser);
    }

    @Transactional
    public UserResponse updateUser(Long id, UpdateUserRequest request) {
        User currentUser = getCurrentUserEntity();
        User targetUser = getUserOrThrow(id);
        assertCanModifyUser(currentUser, targetUser);

        if (StringUtils.hasText(request.firstName())) {
            targetUser.setFirstName(request.firstName());
        }
        if (StringUtils.hasText(request.lastName())) {
            targetUser.setLastName(request.lastName());
        }
        if (StringUtils.hasText(request.password())) {
            targetUser.setPassword(passwordEncoder.encode(request.password()));
        }
        if (request.enabled() != null) {
            if (currentUser.getRole() != UserRole.ADMIN) {
                throw new ForbiddenAccessException("Only administrators can change account status");
            }
            targetUser.setEnabled(request.enabled());
        }

        return UserMapper.toResponse(userRepository.save(targetUser));
    }

    @Transactional
    public void deleteUser(Long id) {
        User currentUser = getCurrentUserEntity();
        User targetUser = getUserOrThrow(id);
        assertCanModifyUser(currentUser, targetUser);
        userRepository.delete(targetUser);
    }

    private User getCurrentUserEntity() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new InvalidCredentialsException();
        }
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new UserNotFoundException(authentication.getName()));
    }

    private User getUserOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
    }

    private void assertCanAccessUser(User currentUser, User targetUser) {
        if (!canManageUser(currentUser, targetUser)) {
            throw new ForbiddenAccessException("You are not allowed to access this user");
        }
    }

    private void assertCanModifyUser(User currentUser, User targetUser) {
        if (!canManageUser(currentUser, targetUser)) {
            throw new ForbiddenAccessException("You are not allowed to modify this user");
        }
    }

    boolean canManageUser(User currentUser, User targetUser) {
        return currentUser.getRole() == UserRole.ADMIN
                || currentUser.getId().equals(targetUser.getId());
    }
}
