package com.app.model;

public class MonthlyReportSummary {
    private int id;
    private String monthKey;
    private String generatedAt;
    private String filePath;
    private int totalTickets;
    private int openTickets;
    private int inProgressTickets;
    private int closedTickets;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getMonthKey() { return monthKey; }
    public void setMonthKey(String monthKey) { this.monthKey = monthKey; }
    public String getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(String generatedAt) { this.generatedAt = generatedAt; }
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    public int getTotalTickets() { return totalTickets; }
    public void setTotalTickets(int totalTickets) { this.totalTickets = totalTickets; }
    public int getOpenTickets() { return openTickets; }
    public void setOpenTickets(int openTickets) { this.openTickets = openTickets; }
    public int getInProgressTickets() { return inProgressTickets; }
    public void setInProgressTickets(int inProgressTickets) { this.inProgressTickets = inProgressTickets; }
    public int getClosedTickets() { return closedTickets; }
    public void setClosedTickets(int closedTickets) { this.closedTickets = closedTickets; }
}
