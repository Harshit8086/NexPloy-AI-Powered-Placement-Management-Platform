package com.nexploy.campuslink.campus.repository;

import com.nexploy.campuslink.campus.entity.Campus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CampusRepository extends JpaRepository<Campus, String> {
}
