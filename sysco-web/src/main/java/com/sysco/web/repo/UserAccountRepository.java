package com.sysco.web.repo;

import com.sysco.web.domain.UserAccount;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    /**
     * Sous-directeurs to notify for new tickets: optional filter by handling direction.
     *
     * @param directionId when non-null, only users with this {@link com.sysco.web.domain.UserAccount#getDirectionId()}
     */
    @Query(
            """
            SELECT u FROM UserAccount u WHERE u.active = 1
            AND UPPER(TRIM(u.role)) = 'SOUS-DIRECTEUR'
            AND u.email IS NOT NULL AND TRIM(u.email) <> ''
            AND (:directionId IS NULL OR u.directionId = :directionId)
            """)
    List<UserAccount> findActiveSousDirecteursWithEmail(@Param("directionId") Long directionId);

    /**
     * Sous-directeurs for new-ticket e-mails: same handling direction row, or same parent sous-direction (DSTI-style org).
     */
    @Query(
            """
            SELECT DISTINCT u FROM UserAccount u WHERE u.active = 1
            AND UPPER(TRIM(u.role)) = 'SOUS-DIRECTEUR'
            AND u.email IS NOT NULL AND TRIM(u.email) <> ''
            AND (
                (:directionId IS NOT NULL AND u.directionId = :directionId)
                OR (:handlingSousDirectionId IS NOT NULL AND u.sousDirectionId = :handlingSousDirectionId)
            )
            """)
    List<UserAccount> findActiveSousDirecteursWithEmailForHandlingScope(
            @Param("directionId") Long directionId, @Param("handlingSousDirectionId") Long handlingSousDirectionId);

    /**
     * Active sous-directeurs attached to this sous-direction (direct {@code sous_direction_id} or via their direction row).
     */
    @Query(
            """
            SELECT DISTINCT u FROM UserAccount u
            WHERE u.active = 1
            AND UPPER(TRIM(u.role)) IN ('SOUS-DIRECTEUR', 'SOUS_DIRECTEUR')
            AND (
              u.sousDirectionId = :sousDirectionId
              OR u.directionId IN (SELECT d.id FROM Direction d WHERE d.sousDirectionId = :sousDirectionId)
            )
            """)
    List<UserAccount> findActiveSousDirecteursForSousDirection(@Param("sousDirectionId") Long sousDirectionId);

    List<UserAccount> findByActiveOrderByUsernameAsc(Integer active);

    Optional<UserAccount> findByUsernameIgnoreCase(String username);

    Optional<UserAccount> findByMatriculeIgnoreCase(String matricule);

    @Query(
            """
            SELECT u FROM UserAccount u
            WHERE u.active = 1
              AND (
                (:q <> '' AND LOWER(COALESCE(u.username, '')) LIKE LOWER(CONCAT('%', :q, '%')))
                OR (:q <> '' AND LOWER(COALESCE(u.matricule, '')) LIKE LOWER(CONCAT('%', :q, '%')))
              )
            ORDER BY u.username ASC
            """)
    List<UserAccount> findActiveByUsernameOrMatriculeContains(@Param("q") String query);

    Optional<UserAccount> findByEmailIgnoreCase(String email);

    @Query("SELECT COUNT(u) FROM UserAccount u WHERE u.active = 1 AND upper(trim(u.role)) = upper(trim(:role))")
    long countActiveByRole(@Param("role") String role);
}
