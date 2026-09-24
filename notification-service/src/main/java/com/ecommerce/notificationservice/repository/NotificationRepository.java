package com.ecommerce.notificationservice.repository;

import com.ecommerce.notificationservice.entity.Notification;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<Notification> findByIdAndUserId(Long id, Long userId);

    long countByUserIdAndReadFalse(Long userId);

    @Modifying(clearAutomatically = true)
    @Query("update Notification n set n.read = true where n.userId = :userId and n.read = false")
    int markAllReadForUser(@Param("userId") Long userId);
}
