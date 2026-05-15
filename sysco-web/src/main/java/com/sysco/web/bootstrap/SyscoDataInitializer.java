package com.sysco.web.bootstrap;

import com.sysco.web.domain.AutomatedJob;
import com.sysco.web.domain.CourierPacket;
import com.sysco.web.domain.Department;
import com.sysco.web.domain.Direction;
import com.sysco.web.domain.MonthlyReport;
import com.sysco.web.domain.SousDirection;
import com.sysco.web.domain.Ticket;
import com.sysco.web.domain.UserAccount;
import com.sysco.web.domain.UserPermission;
import com.sysco.web.repo.AutomatedJobRepository;
import com.sysco.web.repo.CourierPacketRepository;
import com.sysco.web.repo.DepartmentRepository;
import com.sysco.web.repo.DirectionRepository;
import com.sysco.web.repo.MonthlyReportRepository;
import com.sysco.web.repo.SousDirectionRepository;
import com.sysco.web.repo.TicketRepository;
import com.sysco.web.repo.UserAccountRepository;
import com.sysco.web.repo.UserPermissionRepository;
import com.sysco.web.service.UserNotificationEmailFallback;
import java.time.Instant;
import java.time.Year;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(10)
@RequiredArgsConstructor
public class SyscoDataInitializer implements CommandLineRunner {

    private static final String DEMO_DSTI_DIRECTION_NAME =
            "Direction des Systèmes et Technologies d'Information";

    private final PasswordEncoder passwordEncoder;
    private final UserAccountRepository users;
    private final UserPermissionRepository perms;
    private final SousDirectionRepository sousDirections;
    private final DepartmentRepository departments;
    private final DirectionRepository directions;
    private final TicketRepository tickets;
    private final CourierPacketRepository courierPackets;
    private final AutomatedJobRepository jobs;
    private final MonthlyReportRepository monthlyReports;
    private final UserNotificationEmailFallback notificationEmailFallback;

    private static final List<String> DEMO_PERMISSIONS = List.of(
            "DASHBOARD",
            "DATA_ENTRY_WRITE",
            "DATA_MANAGEMENT_WRITE",
            "DATASHARE_WRITE",
            "MY_ACTIVITY_WRITE",
            "MY_WORK_WRITE",
            "TICKET_MONITORING_WRITE",
            "TICKET_MANAGEMENT_WRITE",
            "FILE_SHARE_MANAGEMENT_WRITE",
            "USER_MANAGEMENT_WRITE",
            "LOGIN_AUDIT_WRITE",
            "FILE_SHARE_AUDIT_WRITE",
            "CREATE_TICKET_WRITE",
            "JOB_SCHEDULER_WRITE",
            "MISSIONS_WRITE",
            "PHYSICAL_COURIER_WRITE",
            "LEAVE_MANAGEMENT_WRITE",
            "MY_SHIFT_WRITE");

    @Override
    @Transactional
    public void run(String... args) {
        if (users.count() > 0) {
            return;
        }

        Department dept = new Department();
        dept.setName("Principal");
        dept = departments.save(dept);

        SousDirection sdDsti =
                sousDirections
                        .findByNameIgnoreCase(DEMO_DSTI_DIRECTION_NAME)
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "DGDA seed missing DSTI parent direction — ensure DgdaOrganizationSeedRunner ran."));
        List<Direction> dstiChildren =
                directions.findAllBySousDirectionIdOrderByNameAsc(sdDsti.getId());
        if (dstiChildren.isEmpty()) {
            throw new IllegalStateException("DGDA seed missing DSTI sous-direction rows.");
        }
        Direction dir =
                dstiChildren.stream()
                        .filter(d -> d.getName() != null && d.getName().contains("Développement"))
                        .findFirst()
                        .orElse(dstiChildren.get(0));

        UserAccount admin = new UserAccount();
        admin.setUsername("admin");
        admin.setPasswordHash(passwordEncoder.encode("sysco"));
        admin.setRole("SUPER_ADMIN");
        admin.setActive(1);
        admin.setMustChangePassword(0);
        admin.setEmail(notificationEmailFallback.resolveStoredEmail(null));
        admin.setOnboardingTutorialCompleted(1);
        admin.setSousDirectionId(sdDsti.getId());
        admin.setDirectionId(dir.getId());
        admin = users.save(admin);

        UserAccount superAdmin = new UserAccount();
        superAdmin.setUsername("superadmin");
        superAdmin.setPasswordHash(passwordEncoder.encode("123456"));
        superAdmin.setRole("SUPER_ADMIN");
        superAdmin.setActive(1);
        superAdmin.setMustChangePassword(1);
        superAdmin.setEmail(notificationEmailFallback.resolveStoredEmail(null));
        superAdmin.setDirectionId(dir.getId());
        superAdmin.setOnboardingTutorialCompleted(1);
        superAdmin = users.save(superAdmin);

        UserAccount director = new UserAccount();
        director.setUsername("directeur-demo");
        director.setPasswordHash(passwordEncoder.encode("sysco"));
        director.setRole("DIRECTEUR");
        director.setActive(1);
        director.setMustChangePassword(0);
        director.setSousDirectionId(sdDsti.getId());
        director.setDirectionId(dir.getId());
        director.setOnboardingTutorialCompleted(1);
        director = users.save(director);

        grantAll(perms, admin.getId());
        grantAll(perms, superAdmin.getId());
        grantAll(perms, director.getId());

        Instant now = Instant.now();

        Ticket t = new Ticket();
        t.setTitle("SYSCO Web parity demo ticket");
        t.setDescription("Generated by Spring Boot seed — point JDBC at Oracle for production parity.");
        t.setPriority("MEDIUM");
        t.setStatus("OPEN");
        t.setTicketType("INTERNAL");
        t.setDepartmentId(dept.getId());
        t.setCreatedBy(admin.getId());
        t.setUpdatedBy(admin.getId());
        t.setTicketNumber("TCK-" + Year.now().getValue() + "-WEB-00001");
        t.setCreatedAt(now);
        t.setUpdatedAt(now);
        tickets.save(t);

        CourierPacket cp = new CourierPacket();
        cp.setRefCode("CP-" + Year.now().getValue() + "-WEB-00001");
        cp.setTitle("Courrier démo");
        cp.setDescription("Colis démo — même tables que le client JavaFX.");
        cp.setStatus("REGISTERED");
        cp.setTargetDirectionId(dir.getId());
        cp.setTargetSousDirectionId(sdDsti.getId());
        cp.setCreatedBy(admin.getId());
        cp.setCreatedAt(now);
        cp.setSender("SYSCO SEED");
        cp.setPriority("HIGH");
        cp.setRegistrationDate(now.toString().substring(0, 10));
        courierPackets.save(cp);

        AutomatedJob job = new AutomatedJob();
        job.setJobTitle("Tâche planifiée démo");
        job.setJobDescription("Planificateur — aligné sur automated_jobs");
        job.setDueAt(now.toString().substring(0, 10) + " 09:00:00");
        job.setReminderMinutes(60);
        job.setAssigneeUserId(director.getId());
        job.setCreatedBy(admin.getId());
        job.setRecurrence("ONCE");
        job.setActive(1);
        job.setCreatedAt(now);
        jobs.save(job);

        MonthlyReport mr = new MonthlyReport();
        mr.setMonthKey(Year.now().getValue() + "-03");
        mr.setGeneratedAt(now);
        mr.setFilePath("reports/demo-" + mr.getMonthKey() + ".docx");
        mr.setTotalTickets(1);
        mr.setOpenTickets(1);
        mr.setInProgressTickets(0);
        mr.setClosedTickets(0);
        monthlyReports.save(mr);
    }

    private static void grantAll(UserPermissionRepository perms, Long userId) {
        for (String p : DEMO_PERMISSIONS) {
            UserPermission row = new UserPermission();
            row.setUserId(userId);
            row.setPermission(p);
            perms.save(row);
        }
    }
}
