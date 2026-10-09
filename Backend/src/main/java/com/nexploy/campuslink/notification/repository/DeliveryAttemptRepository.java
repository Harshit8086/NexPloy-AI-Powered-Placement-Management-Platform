package com.nexploy.campuslink.notification.repository;

import com.nexploy.campuslink.notification.entity.DeliveryAttempt;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryAttemptRepository extends JpaRepository<DeliveryAttempt, String> {

    List<DeliveryAttempt> findByNotificationIdOrderByAttemptNumberAsc(String notificationId);
}
