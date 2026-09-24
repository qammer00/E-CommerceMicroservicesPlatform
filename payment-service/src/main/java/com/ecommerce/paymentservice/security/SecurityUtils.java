package com.ecommerce.paymentservice.security;

import com.ecommerce.paymentservice.exception.UnauthorizedAccessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static AuthenticatedUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new UnauthorizedAccessException("Authentication required");
        }
        return user;
    }

    public static boolean isAdmin(AuthenticatedUser user) {
        return "ADMIN".equalsIgnoreCase(user.getRole());
    }
}
