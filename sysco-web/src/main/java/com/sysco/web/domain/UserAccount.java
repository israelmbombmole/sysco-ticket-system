package com.sysco.web.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "users")
public class UserAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    private String matricule;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(nullable = false)
    private String role;

    private Integer active = 1;

    @Column(name = "sous_direction_id")
    private Long sousDirectionId;

    @Column(name = "direction_id")
    private Long directionId;

    private Integer hidden = 0;

    @Column(name = "must_change_password")
    private Integer mustChangePassword = 1;

    @Column(name = "failed_attempts")
    private Integer failedAttempts = 0;

    @Column(name = "lock_until")
    private Instant lockUntil;

    /**
     * After each lock triggered by {@code failed_attempts}, incremented so the next lock lasts longer (capped in code).
     * Reset on successful login or admin unlock.
     */
    @Column(name = "login_lockout_tier", nullable = false)
    private Integer loginLockoutTier = 0;

    private String email;

    @Column(name = "attendance_signature")
    private String attendanceSignature;

    /** Last time the user opened the chat UI; used for unread message badge. */
    @Column(name = "chat_last_seen_at")
    private Instant chatLastSeenAt;

    /**
     * 0 = offer automatic guided tour on next full login to /app; 1 = skip auto-start (user finished or skipped once).
     * Help button always replays the tour.
     */
    @Column(name = "onboarding_tutorial_completed", nullable = false)
    private Integer onboardingTutorialCompleted = 0;

    public boolean isActiveBool() {
        return active != null && active == 1;
    }
}
