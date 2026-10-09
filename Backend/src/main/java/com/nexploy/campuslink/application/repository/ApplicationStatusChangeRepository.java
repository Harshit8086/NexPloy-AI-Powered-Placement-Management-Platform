package com.nexploy.campuslink.application.repository;

import com.nexploy.campuslink.application.entity.ApplicationStatusChange;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationStatusChangeRepository
    extends JpaRepository<ApplicationStatusChange, String> {

    List<ApplicationStatusChange> findByApplicationIdOrderByChangedAtAsc(String applicationId);
}
