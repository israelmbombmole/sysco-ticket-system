package com.app.model;

public class AgentStats {

    private String agentName;
    private int totalTickets;
    private int totalMinutes;

    public AgentStats(String agentName, int totalTickets, int totalMinutes) {
        this.agentName = agentName;
        this.totalTickets = totalTickets;
        this.totalMinutes = totalMinutes;
    }

    // ✅ REQUIRED GETTERS

    public String getAgentName() {
        return agentName;
    }

    public int getTotalTickets() {
        return totalTickets;
    }

    public int getTotalMinutes() {
        return totalMinutes;
    }
}
