package com.app.model;

import java.util.ArrayList;
import java.util.List;

public class ScheduledJob {
    private int id;
    private String title;
    private String description;
    private String dueAt;
    private int reminderMinutes;
    private Integer assigneeUserId;
    private String assigneeUsername;
    private final List<Integer> assigneeUserIds = new ArrayList<>();
    private String recurrence;
    private boolean active;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getDueAt() { return dueAt; }
    public void setDueAt(String dueAt) { this.dueAt = dueAt; }
    public int getReminderMinutes() { return reminderMinutes; }
    public void setReminderMinutes(int reminderMinutes) { this.reminderMinutes = reminderMinutes; }
    public Integer getAssigneeUserId() { return assigneeUserId; }
    public void setAssigneeUserId(Integer assigneeUserId) { this.assigneeUserId = assigneeUserId; }
    public String getAssigneeUsername() { return assigneeUsername; }
    public void setAssigneeUsername(String assigneeUsername) { this.assigneeUsername = assigneeUsername; }

    /** All users who receive the scheduled job (tickets / reminders). */
    public List<Integer> getAssigneeUserIds() { return assigneeUserIds; }

    public void setAssigneeUserIds(List<Integer> ids) {
        assigneeUserIds.clear();
        if (ids != null) {
            for (Integer id : ids) {
                if (id != null && id > 0 && !assigneeUserIds.contains(id)) {
                    assigneeUserIds.add(id);
                }
            }
        }
    }

    public String getRecurrence() { return recurrence; }
    public void setRecurrence(String recurrence) { this.recurrence = recurrence; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
