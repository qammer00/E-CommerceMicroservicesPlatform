package com.ecommerce.notificationservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecommerce.notificationservice.dto.CreateNotificationRequest;
import com.ecommerce.notificationservice.dto.NotificationResponse;
import com.ecommerce.notificationservice.entity.Notification;
import com.ecommerce.notificationservice.entity.NotificationType;
import com.ecommerce.notificationservice.exception.ForbiddenAccessException;
import com.ecommerce.notificationservice.exception.NotificationNotFoundException;
import com.ecommerce.notificationservice.repository.NotificationRepository;
import com.ecommerce.notificationservice.security.AuthenticatedUser;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationCreator notificationCreator;

    @InjectMocks
    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        authenticate(1L, "user@example.com", "USER");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createForAdmin_requiresAdmin() {
        assertThatThrownBy(() -> notificationService.createForAdmin(
                new CreateNotificationRequest(1L, NotificationType.ORDER_CREATED, "t", "m", "1", "ORDER")))
                .isInstanceOf(ForbiddenAccessException.class);
    }

    @Test
    void createForAdmin_succeedsForAdmin() {
        authenticate(9L, "admin@example.com", "ADMIN");
        Notification saved = sample(10L, 1L, false);
        saved.setType(NotificationType.PAYMENT_PAID);
        when(notificationCreator.create(any(CreateNotificationRequest.class))).thenReturn(saved);

        NotificationResponse response = notificationService.createForAdmin(
                new CreateNotificationRequest(1L, NotificationType.PAYMENT_PAID, "Paid", "ok", "5", "PAYMENT"));

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.type()).isEqualTo(NotificationType.PAYMENT_PAID);
    }

    @Test
    void listMyNotifications_returnsOnlyCurrentUser() {
        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(1L))
                .thenReturn(List.of(sample(1L, 1L, false)));

        assertThat(notificationService.listMyNotifications()).hasSize(1);
    }

    @Test
    void getById_forbidsOtherUser() {
        when(notificationRepository.findById(3L)).thenReturn(Optional.of(sample(3L, 99L, false)));

        assertThatThrownBy(() -> notificationService.getById(3L))
                .isInstanceOf(ForbiddenAccessException.class);
    }

    @Test
    void markRead_updatesOwnedNotification() {
        Notification notification = sample(4L, 1L, false);
        when(notificationRepository.findByIdAndUserId(4L, 1L)).thenReturn(Optional.of(notification));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(i -> i.getArgument(0));

        assertThat(notificationService.markRead(4L).read()).isTrue();
    }

    @Test
    void markRead_throwsWhenMissing() {
        when(notificationRepository.findByIdAndUserId(404L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markRead(404L))
                .isInstanceOf(NotificationNotFoundException.class);
    }

    @Test
    void markAllRead_clearsUnread() {
        when(notificationRepository.markAllReadForUser(1L)).thenReturn(2);

        assertThat(notificationService.markAllRead().unreadCount()).isZero();
        verify(notificationRepository).markAllReadForUser(1L);
    }

    @Test
    void unreadCount_returnsRepositoryValue() {
        when(notificationRepository.countByUserIdAndReadFalse(1L)).thenReturn(3L);

        assertThat(notificationService.unreadCount().unreadCount()).isEqualTo(3L);
    }

    private void authenticate(Long userId, String email, String role) {
        AuthenticatedUser principal = new AuthenticatedUser(
                userId, email, role, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private Notification sample(Long id, Long userId, boolean read) {
        Notification n = new Notification();
        n.setId(id);
        n.setUserId(userId);
        n.setType(NotificationType.ORDER_CREATED);
        n.setTitle("title");
        n.setMessage("message");
        n.setReferenceId("1");
        n.setReferenceType("ORDER");
        n.setRead(read);
        n.setCreatedAt(Instant.now());
        return n;
    }
}
