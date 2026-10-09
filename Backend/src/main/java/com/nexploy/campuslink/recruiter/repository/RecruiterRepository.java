package com.nexploy.campuslink.recruiter.repository;

import com.nexploy.campuslink.recruiter.entity.Recruiter;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecruiterRepository extends JpaRepository<Recruiter, String> {

    List<Recruiter> findByCampusId(String campusId);

    /** Resolves the recruiter row for the signed-in recruiter account (username == email). */
    Optional<Recruiter> findByEmail(String email);
}
