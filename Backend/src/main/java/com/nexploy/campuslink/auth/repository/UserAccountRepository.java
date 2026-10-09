package com.nexploy.campuslink.auth.repository;

import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.entity.UserRole;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, String> {

    /**
     * Case-insensitive existence check on the username (which is the account's email). Registration
     * uses this instead of {@code existsById} so that {@code Officer123@gmail.com} and
     * {@code officer123@gmail.com} are treated as the same identity — the account PK is exact-match,
     * so without this two case-variant accounts for one real person could both be created.
     */
    boolean existsByUsernameIgnoreCase(String username);

    Optional<UserAccount> findByUsernameIgnoreCase(String username);

    /**
     * Enabled accounts of a role within a campus — used to fan a campus-level reminder out to all its
     * officers. The username is the account's email (and the notification recipient).
     */
    List<UserAccount> findByRoleAndCampusIdAndEnabledTrue(UserRole role, String campusId);
}
