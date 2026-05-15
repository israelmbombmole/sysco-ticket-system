package com.sysco.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sysco.web.domain.Direction;
import com.sysco.web.domain.Ticket;
import com.sysco.web.domain.UserAccount;
import com.sysco.web.repo.DirectionRepository;
import com.sysco.web.repo.NotificationRepository;
import com.sysco.web.repo.TicketRepository;
import com.sysco.web.repo.UserAccountRepository;
import com.sysco.web.security.DirectionScopeService;
import com.sysco.web.service.TicketTimelineService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class SecurityScopeIntegrationTest {

    @Autowired
    private UserAccountRepository users;

    @Autowired
    private DirectionRepository directions;

    @Autowired
    private TicketRepository tickets;

    @Autowired
    private DirectionScopeService scope;

    @Autowired
    private TicketTimelineService timeline;

    @Autowired
    private NotificationRepository notifications;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void superAdminIsSeededWithExpectedUsername() {
        UserAccount superAdmin = users.findByUsernameIgnoreCase("superadmin").orElse(null);
        assertNotNull(superAdmin);
        assertTrue(superAdmin.isActiveBool());
        assertTrue(passwordEncoder.matches("123456", superAdmin.getPasswordHash()));
    }

    @Test
    void directionScopeBlocksCrossDirectionTicketAccess() {
        Direction d1 = new Direction();
        d1.setName("Direction Test 1");
        d1 = directions.save(d1);

        Direction d2 = new Direction();
        d2.setName("Direction Test 2");
        d2 = directions.save(d2);

        UserAccount u1 = new UserAccount();
        u1.setUsername("user-d1");
        u1.setRole("ADMIN");
        u1.setDirectionId(d1.getId());
        u1.setPasswordHash("x");
        u1.setActive(1);
        u1 = users.save(u1);

        UserAccount u2 = new UserAccount();
        u2.setUsername("user-d2");
        u2.setRole("DIRECTEUR");
        u2.setDirectionId(d2.getId());
        u2.setPasswordHash("x");
        u2.setActive(1);
        u2 = users.save(u2);

        Ticket t = new Ticket();
        t.setTitle("Scoped ticket");
        t.setStatus("OPEN");
        t.setTicketType("INTERNAL");
        t.setCreatedBy(u2.getId());
        t.setUpdatedBy(u2.getId());
        t.setCreatedAt(Instant.now());
        t.setUpdatedAt(Instant.now());
        t = tickets.save(t);

        assertFalse(scope.canAccessTicket(u1, t));
    }

    @Test
    void ticketMovementCreatesOwnerNotification() {
        UserAccount owner = users.findByUsernameIgnoreCase("admin").orElseThrow();
        Ticket t = new Ticket();
        t.setTitle("Timeline notify ticket");
        t.setStatus("OPEN");
        t.setTicketType("INTERNAL");
        t.setCreatedBy(owner.getId());
        t.setUpdatedBy(owner.getId());
        t.setCreatedAt(Instant.now());
        t.setUpdatedAt(Instant.now());
        t = tickets.save(t);

        long before = notifications.count();
        timeline.log("UPDATED", t.getId(), owner.getUsername(), null, "status moved");
        long after = notifications.count();
        assertTrue(after > before);
    }
}
