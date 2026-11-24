package com.auradev.erp.user.repository;

import com.auradev.erp.user.entity.User;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data-access layer for {@link User} entities.
 *
 * <p>{@link #findByEmail(String)} is used exclusively by the authentication
 * filter / login flow and intentionally bypasses the tenant filter so that
 * SUPER_ADMIN accounts (which have a {@code null} tenant_id) can also be
 * resolved.</p>
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Locate a user by e-mail within a specific tenant.
     * Used when creating / looking up users in a tenant context.
     */
    Optional<User> findByEmailAndTenantId(String email, UUID tenantId);

    /**
     * Locate a user by e-mail regardless of tenant.
     * Used during authentication so SUPER_ADMIN (tenantId = null) is resolved.
     */
    Optional<User> findByEmail(String email);

    /**
     * Locate a user by their invitation token.
     * Used to validate invite links during user on-boarding.
     */
    Optional<User> findByInviteToken(String token);

    /**
     * Return all users belonging to the given tenant.
     */
    List<User> findAllByTenantId(UUID tenantId);

    /**
     * Records a successful sign-in without loading and saving the whole user
     * record. Login must remain reliable even when an admin updates the same
     * user at the same time.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update User u set u.lastLoginAt = :lastLoginAt where u.id = :userId")
    int updateLastLoginAt(@Param("userId") UUID userId, @Param("lastLoginAt") java.time.Instant lastLoginAt);
}
