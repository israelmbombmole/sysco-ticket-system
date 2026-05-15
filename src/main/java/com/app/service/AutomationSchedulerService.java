package com.app.service;

import com.app.dao.AutomationDAO;
import com.app.dao.NotificationDAO;
import com.app.dao.UserDAO;
import com.app.model.ScheduledJob;
import com.app.util.TimeUtil;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class AutomationSchedulerService {
    private static final DateTimeFormatter DB_DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ScheduledExecutorService EXEC = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "automation-scheduler");
        t.setDaemon(true);
        return t;
    });
    private static volatile boolean started = false;

    public static synchronized void start() {
        if (started) return;
        started = true;
        EXEC.scheduleWithFixedDelay(AutomationSchedulerService::tick, 3, 15, TimeUnit.SECONDS);
    }

    private static void tick() {
        try {
            processScheduledJobs();
            generateMonthlyReportIfNeeded();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void processScheduledJobs() {
        List<ScheduledJob> jobs = AutomationDAO.getActiveJobs();
        LocalDateTime now = LocalDateTime.now();
        for (ScheduledJob j : jobs) {
            if (j.getDueAt() == null || j.getDueAt().isBlank()) continue;
            LocalDateTime due = TimeUtil.parseDbLocalDateTime(j.getDueAt());
            if (due == null) {
                System.err.println("[AutomationScheduler] Job id=" + j.getId() + " has unparsable due_at: " + j.getDueAt());
                continue;
            }

            LocalDateTime remindAt = due.minusMinutes(Math.max(0, j.getReminderMinutes()));
            String lastReminder = AutomationDAO.getJobLastReminderAt(j.getId());
            if (!now.isBefore(remindAt) && (lastReminder == null || lastReminder.isBlank())) {
                List<Integer> remindUsers = j.getAssigneeUserIds();
                if (remindUsers.isEmpty() && j.getAssigneeUserId() != null) {
                    remindUsers = List.of(j.getAssigneeUserId());
                }
                if (!remindUsers.isEmpty()) {
                    for (int uid : remindUsers) {
                        NotificationDAO.create(
                                uid,
                                "Job Reminder",
                                "Scheduled job \"" + j.getTitle() + "\" is due at " + due.format(DB_DT),
                                "JOB_REMINDER"
                        );
                        String toEmail = UserDAO.getEmailByUserId(uid);
                        EmailService.sendSimpleNotificationEmail(
                                toEmail,
                                "SYSCO Job Reminder",
                                "Reminder: scheduled job \"" + j.getTitle() + "\" is due at " + due.format(DB_DT) + "."
                        );
                    }
                    AutomationDAO.markReminderSent(j.getId());
                }
            }

            String lastCreated = AutomationDAO.getJobLastTicketCreatedAt(j.getId());
            if (!now.isBefore(due) && (lastCreated == null || lastCreated.isBlank())) {
                try {
                    AutomationDAO.createTicketForScheduledJob(j.getId());
                    AutomationDAO.markTicketCreatedForJob(j.getId());
                    if ("MONTHLY".equalsIgnoreCase(j.getRecurrence())) {
                        AutomationDAO.advanceJobToNextMonthlyCycle(j.getId());
                    } else {
                        AutomationDAO.deactivateJob(j.getId());
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private static void generateMonthlyReportIfNeeded() {
        YearMonth previousMonth = YearMonth.now().minusMonths(1);
        AutomationDAO.generateMonthlyReport(previousMonth);
    }
}
